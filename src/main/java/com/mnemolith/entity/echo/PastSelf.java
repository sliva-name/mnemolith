package com.mnemolith.entity.echo;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.recall.LifeMoment;
import com.mnemolith.recall.LifeMomentKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "The past you": a translucent figure in its owner's skin that plays one {@link LifeMoment} at its place, then is
 * gone. It cannot be hit, pushed or targeted, changes nothing in the world, holds a copy of a remembered item that
 * never drops, and is never saved with the chunk. By default only its owner is sent the entity at all.
 */
public class PastSelf extends MemoryAvatar {
    public static EntityType.@Nullable EntityFactory<PastSelf> clientFactory;

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(PastSelf.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LENGTH = SynchedEntityData.defineId(PastSelf.class, EntityDataSerializers.INT);

    /** Ticks each scene lasts. A death ends earlier: the vanilla fall-over removes the body 20 ticks after it dies. */
    public static int length(LifeMomentKind kind) {
        return switch (kind) {
            case DEATH -> 80;
            case HOME -> 110;
            case BUILD -> 90;
            case BATTLE -> 75;
        };
    }

    private static final int DEATH_HIT_1 = 30;
    private static final int DEATH_HIT_2 = 40;
    private static final int DEATH_FALL = 46;
    private static final int HOME_LIE = 28;

    private LifeMomentKind kind = LifeMomentKind.DEATH;
    private BlockPos site = BlockPos.ZERO;
    private float baseYaw;
    private boolean othersSee;
    private @Nullable BlockPos bed;
    private final List<BlockPos> looks = new ArrayList<>();
    private int age;
    private boolean staged;
    private boolean lying;
    private int swings;
    private long startedAt = Long.MIN_VALUE;

    protected PastSelf(EntityType<? extends PastSelf> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public static PastSelf create(EntityType<PastSelf> type, Level level) {
        if (level.isClientSide() && clientFactory != null) {
            return clientFactory.create(type, level);
        }
        return new PastSelf(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_KIND, 0);
        entityData.define(DATA_LENGTH, 80);
    }

    /**
     * Places this body for {@code moment} and starts the scene. {@code standAt} is where it stands, {@code looks} are
     * blocks a build scene glances at. The caller adds it to the level.
     */
    public void stage(ServerPlayer owner, LifeMoment moment, Vec3 standAt, @Nullable BlockPos bed, List<BlockPos> looks, boolean othersSee) {
        this.setOwner(owner);
        this.kind = moment.kind();
        this.site = moment.pos();
        this.baseYaw = moment.yaw();
        this.bed = bed;
        this.othersSee = othersSee;
        this.looks.clear();
        this.looks.addAll(looks);
        this.entityData.set(DATA_KIND, this.kind.ordinal());
        this.entityData.set(DATA_LENGTH, length(this.kind));
        this.snapTo(standAt.x, standAt.y, standAt.z, this.baseYaw, 0.0F);
        this.face(this.baseYaw, 0.0F);
        ItemStack hand = hand(moment);
        this.setItemSlot(EquipmentSlot.MAINHAND, hand);
        this.startedAt = this.level().getGameTime();
        this.staged = true;
    }

    /** What the figure holds: the remembered item, or nothing. A build holds its most used block. */
    public static ItemStack hand(LifeMoment moment) {
        Identifier id = Identifier.tryParse(moment.itemId());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (item == Items.AIR) {
            item = BuiltInRegistries.BLOCK.getOptional(id).map(block -> block.asItem()).orElse(Items.AIR);
        }
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public LifeMomentKind kind() {
        return LifeMomentKind.byOrdinal(this.entityData.get(DATA_KIND));
    }

    public int sceneLength() {
        return this.entityData.get(DATA_LENGTH);
    }

    public int age() {
        return this.age;
    }

    /**
     * True once the scene should be over by the clock, whether or not the body ticked. A body left in a loaded chunk
     * that no longer ticks entities (the owner teleported, died, or left) would otherwise hold its owner's scene slot
     * forever.
     */
    public boolean overdue(long now) {
        return this.startedAt != Long.MIN_VALUE && now - this.startedAt > this.sceneLength() + 40L;
    }

    public boolean lying() {
        return this.lying;
    }

    public int swings() {
        return this.swings;
    }

    public boolean othersSee() {
        return this.othersSee;
    }

    /**
     * Client: how visible the body is at {@code ageInTicks}, from 0 to 1. Fades in over a second, out over the last
     * second, and out while it falls dead.
     */
    public float visibility(float ageInTicks) {
        float in = Mth.clamp(ageInTicks / 20.0F, 0.0F, 1.0F);
        float out = Mth.clamp((this.sceneLength() - ageInTicks) / 20.0F, 0.0F, 1.0F);
        float dying = this.deathTime > 0 ? Mth.clamp(1.0F - this.deathTime / 22.0F, 0.0F, 1.0F) : 1.0F;
        return Math.min(in, Math.min(out, dying));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (!this.staged) {
            this.discard();
            return;
        }
        this.age++;
        this.setDeltaMovement(Vec3.ZERO);
        if (this.isDeadOrDying()) {
            return;
        }
        switch (this.kind) {
            case DEATH -> this.tickDeathScene(level);
            case HOME -> this.tickHomeScene(level);
            case BUILD -> this.tickBuildScene(level);
            case BATTLE -> this.tickBattleScene(level);
        }
        if (this.age >= length(this.kind) && !this.isRemoved()) {
            this.vanish(level);
        }
    }

    private void tickDeathScene(ServerLevel level) {
        if (this.age < DEATH_HIT_1) {
            float sway = 40.0F * Mth.sin(this.age * 0.13F);
            this.face(this.baseYaw + sway, 8.0F * Mth.sin(this.age * 0.21F));
            return;
        }
        if (this.age == DEATH_HIT_1 || this.age == DEATH_HIT_2) {
            level.broadcastDamageEvent(this, this.damageSources().generic());
            this.fx(level, ModParticles.REPLICANT_TELEGRAPH.get(), this.getX(), this.getY() + 1.2D, this.getZ(), 6, 0.3D);
            this.face(this.getYRot() + (this.age == DEATH_HIT_1 ? -25.0F : 30.0F), -10.0F);
        }
        if (this.age == DEATH_FALL) {
            this.sound(level, ModSounds.ECHO_DEATH, 0.6F, 0.8F);
            this.setHealth(0.0F);
        }
    }

    private void tickHomeScene(ServerLevel level) {
        BlockPos head = this.bed;
        if (head == null || !(level.getBlockState(head).getBlock() instanceof BedBlock)) {
            // No bed left: stand on the empty place, look down at it, fade.
            this.face(this.baseYaw, 35.0F);
            if (this.lying) {
                this.stand();
            }
            return;
        }
        if (this.age < HOME_LIE) {
            this.lookAt(Vec3.atCenterOf(head));
            return;
        }
        if (!this.lying) {
            this.lying = true;
            BlockState state = level.getBlockState(head);
            Direction facing = state.hasProperty(BedBlock.FACING) ? state.getValue(BedBlock.FACING) : Direction.NORTH;
            // The vanilla sleeping pose without occupying the bed: a real player can still use it.
            this.setSleepingPos(head);
            this.setPose(Pose.SLEEPING);
            this.setPos(head.getX() + 0.5D, head.getY() + 0.6875D, head.getZ() + 0.5D);
            this.face(facing.getOpposite().toYRot(), 0.0F);
            this.fx(level, ModParticles.STRIDER_TRAIL.get(), head.getX() + 0.5D, head.getY() + 0.8D, head.getZ() + 0.5D, 6, 0.4D);
        }
        if (this.age % 30 == 0) {
            this.fx(level, ModParticles.REPLICANT_TELEGRAPH.get(), this.getX(), this.getY() + 0.4D, this.getZ(), 2, 0.3D);
        }
    }

    private void tickBuildScene(ServerLevel level) {
        if (this.looks.isEmpty()) {
            this.face(this.baseYaw, 20.0F);
        } else {
            BlockPos target = this.looks.get((this.age / 12) % this.looks.size());
            this.lookAt(Vec3.atCenterOf(target));
            if (this.age > 14 && this.age % 12 == 6 && this.age < length(this.kind) - 15) {
                this.swing(InteractionHand.MAIN_HAND, true);
                this.swings++;
                this.fx(level, ModParticles.REPLICANT_TELEGRAPH.get(), target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 4, 0.3D);
            }
        }
    }

    private void tickBattleScene(ServerLevel level) {
        float sway = 20.0F * Mth.sin(this.age * 0.25F);
        this.face(this.baseYaw + sway, 5.0F);
        if (this.age > 18 && this.age % 11 == 0 && this.age < length(this.kind) - 15) {
            this.swing(InteractionHand.MAIN_HAND, true);
            this.swings++;
            Vec3 front = this.position().add(this.getLookAngle().multiply(1.4D, 0.0D, 1.4D)).add(0.0D, 1.1D, 0.0D);
            this.fx(level, ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0.0D);
            this.fx(level, ModParticles.REPLICANT_TELEGRAPH.get(), front.x, front.y, front.z, 3, 0.3D);
        }
    }

    /** Ends the scene: a puff of memory and the body is gone. */
    public void vanish(ServerLevel level) {
        if (this.lying) {
            this.stand();
        }
        this.fx(level, ModParticles.STRIDER_TRAIL.get(), this.getX(), this.getY() + 0.2D, this.getZ(), 8, 0.4D);
        this.fx(level, ModParticles.REPLICANT_TELEGRAPH.get(), this.getX(), this.getY() + 1.0D, this.getZ(), 10, 0.4D);
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.discard();
    }

    private void stand() {
        this.lying = false;
        this.clearSleepingPos();
        this.setPose(Pose.STANDING);
    }

    private void face(float yaw, float pitch) {
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.setXRot(pitch);
    }

    private void lookAt(Vec3 target) {
        Vec3 eye = this.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, flat) * (180.0D / Math.PI)));
        this.face(yaw, pitch);
    }

    /** Particles for the owner only, or for everyone nearby when {@code othersSee}. */
    private void fx(ServerLevel level, ParticleOptions type, double x, double y, double z, int count, double spread) {
        if (this.othersSee) {
            level.sendParticles(type, x, y, z, count, spread, spread * 0.6D, spread, 0.02D);
            return;
        }
        ServerPlayer owner = this.onlineOwner();
        if (owner != null && owner.level() == level) {
            level.sendParticles(owner, type, false, false, x, y, z, count, spread, spread * 0.6D, spread, 0.02D);
        }
    }

    private void sound(ServerLevel level, Holder<SoundEvent> sound, float volume, float pitch) {
        if (this.othersSee) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
            return;
        }
        ServerPlayer owner = this.onlineOwner();
        if (owner != null && owner.level() == level) {
            owner.connection.send(new ClientboundSoundPacket(sound, SoundSource.NEUTRAL, this.getX(), this.getY(), this.getZ(), volume, pitch, level.getRandom().nextLong()));
        }
    }

    /** Only the owner is sent this body, unless {@code othersSee} was on when it was staged. */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return this.othersSee || this.isOwnedBy(player);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return false;
    }
}
