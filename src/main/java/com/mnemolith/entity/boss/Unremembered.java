package com.mnemolith.entity.boss;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Faded;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowLanterns;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Unremembered: a figure made of everything a hollow forgot, called with a hollow effigy. Takes half of a player's
 * damage unless a raised lens reveals it; feeds on the loudest imprint of its chunk unless a recollite lantern guards it;
 * a forgetting pulse darkens players near it; in its second half it calls faded. A posy given while it is revealed lays
 * part of it to rest. When it dies, the forgotten imprints around it clear. See {@code docs/design/unremembered.md}.
 */
public class Unremembered extends Monster {
    public static final double MAX_HEALTH = 160.0D;
    public static final double REVEAL_RANGE = 16.0D;
    public static final int REVEAL_TICKS = 100;
    public static final float UNSEEN_DAMAGE = 0.5F;
    public static final float REVEALED_DAMAGE = 1.5F;
    public static final int FEED_PERIOD = 200;
    public static final float FEED_HEAL = 8.0F;
    public static final int PULSE_PERIOD = 160;
    public static final double PULSE_RANGE = 10.0D;
    public static final int CALL_PERIOD = 300;
    public static final int FADED_CAP = 3;
    public static final double FADED_RANGE = 16.0D;
    public static final float POSY_DAMAGE = 20.0F;
    public static final double BAR_RANGE = 32.0D;
    public static final double LEASH_RANGE = 48.0D;
    public static final int LEASH_TICKS = 1200;
    public static final double TELL_RANGE = 32.0D;

    private static final EntityDataAccessor<Boolean> DATA_REVEALED = SynchedEntityData.defineId(Unremembered.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random),
            Component.translatable("entity.mnemolith.unremembered"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private long revealedUntil;
    private int feedTicks = FEED_PERIOD;
    private int pulseTicks = PULSE_PERIOD;
    private int callTicks = CALL_PERIOD;
    private int aloneTicks;
    /** What the last feeding did ("fed", "kept", "empty"); the QA reads it. */
    private String lastFeed = "none";
    private int remembered = -1;

    public Unremembered(EntityType<? extends Unremembered> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        this.setPersistenceRequired();
        this.bossEvent.setVisible(true);
        this.bossEvent.setPlayBossMusic(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_REVEALED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean revealed() {
        return this.entityData.get(DATA_REVEALED);
    }

    public boolean secondHalf() {
        return this.getHealth() <= this.getMaxHealth() * 0.5F;
    }

    public String lastFeed() {
        return this.lastFeed;
    }

    /** Forgotten imprints cleared when it died (-1 before). QA. */
    public int remembered() {
        return this.remembered;
    }

    public ServerBossEvent bossEvent() {
        return this.bossEvent;
    }

    // ---------------------------------------------------------------- lens

    public void reveal() {
        this.revealedUntil = this.level().getGameTime() + REVEAL_TICKS;
        this.entityData.set(DATA_REVEALED, true);
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, REVEAL_TICKS, 0, false, false));
    }

    /** Ends a reveal at once (QA). */
    public void hideForQa() {
        this.revealedUntil = 0L;
        this.entityData.set(DATA_REVEALED, false);
        this.removeEffect(MobEffects.GLOWING);
    }

    public boolean seenThroughLens(Player player) {
        if (!ChronicleLensItem.isFocusing(player) || player.distanceToSqr(this) > REVEAL_RANGE * REVEAL_RANGE) {
            return false;
        }
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 to = this.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        return look.dot(to) > 0.93D && player.hasLineOfSight(this);
    }

    public float playerDamageScale() {
        return this.revealed() ? REVEALED_DAMAGE : UNSEEN_DAMAGE;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player) {
            amount *= this.playerDamageScale();
        }
        return super.hurtServer(level, source, amount);
    }

    /** A revealed Unremembered takes a posy as {@link #POSY_DAMAGE} from {@code player}. Returns whether it took it. */
    public boolean takePosy(ServerLevel level, ServerPlayer player) {
        if (!this.isAlive() || !this.revealed()) {
            return false;
        }
        // the posy is not a blow: no lens scaling, straight to the base hurt
        super.hurtServer(level, this.damageSources().playerAttack(player), POSY_DAMAGE);
        level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 2.0D, this.getZ(), 20, 0.5D, 0.9D, 0.5D, 0.02D);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2.4D, this.getZ(), 10, 0.4D, 0.6D, 0.4D, 0.01D);
        return true;
    }

    // ---------------------------------------------------------------- abilities (public for QA)

    /** Eats the loudest imprint of its chunk and heals; a guarding lantern stops it. */
    public String feed(ServerLevel level) {
        BlockPos pos = this.blockPosition();
        if (HollowLanterns.guarded(level, pos)) {
            level.sendParticles(ParticleTypes.WAX_OFF, this.getX(), this.getY() + 2.0D, this.getZ(), 8, 0.4D, 0.6D, 0.4D, 0.0D);
            return this.lastFeed = "kept";
        }
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || memory.removeHighest().isEmpty()) {
            return this.lastFeed = "empty";
        }
        MemoryPressure.recompute(chunk, memory);
        this.heal(FEED_HEAL);
        level.sendParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 1.0D, this.getZ(), 10, 0.6D, 0.4D, 0.6D, 0.02D);
        return this.lastFeed = "fed";
    }

    /** Darkness and slowness for every player within {@link #PULSE_RANGE}. Returns how many it touched. */
    public int pulse(ServerLevel level) {
        int touched = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(PULSE_RANGE))) {
            if (player.isCreative() || player.isSpectator() || player.distanceToSqr(this) > PULSE_RANGE * PULSE_RANGE) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), this);
            touched++;
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 1.5D, this.getZ(), 24, PULSE_RANGE * 0.3D, 0.5D, PULSE_RANGE * 0.3D, 0.01D);
        level.playSound(null, this.blockPosition(), ModSounds.FADED_AMBIENT.get(), this.getSoundSource(), 1.6F, 0.45F);
        return touched;
    }

    /** In the second half, calls a faded beside itself while fewer than {@link #FADED_CAP} stand near. */
    public @Nullable Faded callFaded(ServerLevel level) {
        if (!this.secondHalf() || level.getEntitiesOfClass(Faded.class, this.getBoundingBox().inflate(FADED_RANGE)).size() >= FADED_CAP) {
            return null;
        }
        Faded faded = ModEntities.FADED.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (faded == null) {
            return null;
        }
        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        faded.snapTo(this.getX() + Math.cos(angle) * 2.0D, this.getY(), this.getZ() + Math.sin(angle) * 2.0D, this.getYRot(), 0.0F);
        faded.copy(this.blockPosition(), ImprintTag.FORGOTTEN);
        level.addFreshEntity(faded);
        if (this.getTarget() != null) {
            faded.provoke(this.getTarget());
        }
        level.sendParticles(ParticleTypes.SOUL, faded.getX(), faded.getY() + 1.0D, faded.getZ(), 10, 0.3D, 0.6D, 0.3D, 0.01D);
        return faded;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.revealed() && level.getGameTime() >= this.revealedUntil) {
            this.entityData.set(DATA_REVEALED, false);
        }
        if (this.tickCount % 5 == 0) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && this.seenThroughLens(player)) {
                    this.reveal();
                    break;
                }
            }
        }
        this.bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        if (this.tickCount % 10 == 0) {
            this.updateBar(level);
        }
        if (this.isNoAi()) {
            return;
        }
        if (--this.feedTicks <= 0) {
            this.feedTicks = FEED_PERIOD;
            this.feed(level);
        }
        if (--this.pulseTicks <= 0) {
            this.pulseTicks = PULSE_PERIOD;
            this.pulse(level);
        }
        if (--this.callTicks <= 0) {
            this.callTicks = CALL_PERIOD;
            this.callFaded(level);
        }
        if (this.tickCount % 20 == 0) {
            if (level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), LEASH_RANGE, p -> !p.isSpectator() && !((Player) p).isCreative()) == null) {
                this.aloneTicks += 20;
                if (this.aloneTicks >= LEASH_TICKS) {
                    this.sinkAway(level);
                }
            } else {
                this.aloneTicks = 0;
            }
        }
    }

    /** Left alone too long: it sinks back into the turf. No loot, no remembering. */
    public void sinkAway(ServerLevel level) {
        level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0D, this.getZ(), 30, 0.6D, 1.2D, 0.6D, 0.01D);
        this.bossEvent.removeAllPlayers();
        this.discard();
    }

    /** Keeps the boss bar on exactly the players within {@link #BAR_RANGE}. Public for QA. */
    public void updateBar(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            boolean near = player.distanceToSqr(this) <= BAR_RANGE * BAR_RANGE;
            if (near && !this.bossEvent.getPlayers().contains(player)) {
                this.bossEvent.addPlayer(player);
            } else if (!near && this.bossEvent.getPlayers().contains(player)) {
                this.bossEvent.removePlayer(player);
            }
        }
        for (ServerPlayer player : List.copyOf(this.bossEvent.getPlayers())) {
            if (player.isRemoved() || player.level() != level) {
                this.bossEvent.removePlayer(player);
            }
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    // ---------------------------------------------------------------- death

    @Override
    public void die(DamageSource source) {
        boolean wasDead = this.dead || this.isRemoved();
        super.die(source);
        if (!wasDead && this.level() instanceof ServerLevel level) {
            this.remembered = remember(level, this.blockPosition());
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(TELL_RANGE))) {
                player.sendSystemMessage(Component.translatable("mnemolith.unremembered.remembers", this.remembered));
            }
            this.bossEvent.removeAllPlayers();
        }
    }

    /** Clears every forgotten imprint in the loaded 3×3 chunks around {@code pos}. Returns how many. */
    public static int remember(ServerLevel level, BlockPos pos) {
        ChunkPos center = ChunkPos.containing(pos);
        int cleared = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(center.x() + dx, center.z() + dz)) {
                    continue;
                }
                cleared += clearForgotten(level.getChunk(center.x() + dx, center.z() + dz));
            }
        }
        return cleared;
    }

    /** Removes the forgotten imprints of one chunk. Returns how many. */
    public static int clearForgotten(LevelChunk chunk) {
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null) {
            return 0;
        }
        int n = 0;
        while (memory.removeStrongest(ImprintTag.FORGOTTEN).isPresent()) {
            n++;
        }
        if (n > 0) {
            MemoryPressure.recompute(chunk, memory);
        }
        return n;
    }

    /** Forgotten imprints in one chunk. */
    public static int countForgotten(LevelChunk chunk) {
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        int n = 0;
        for (int i = 0; memory != null && i < memory.imprintCount(); i++) {
            n += memory.imprintAt(i).tag() == ImprintTag.FORGOTTEN ? 1 : 0;
        }
        return n;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        this.spawnAtLocation(level, ImprintSlips.of(ImprintTag.FORGOTTEN, this.blockPosition()));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + (this.random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.FADED_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.FADED_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FADED_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("unremembered_alone", this.aloneTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.aloneTicks = input.getIntOr("unremembered_alone", 0);
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }
}
