package com.mnemolith.entity.echo;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Scar: what a recollection storm's residues become when three or more of them outlast its waves. It hovers over
 * its heart, leashed to it, and recalls the memories that merged into it, one after another. It cannot be hurt until it
 * is read: three seconds of a steady Chronicle Lens gaze within 20 blocks pin it for 8 seconds (slow, silent,
 * vulnerable), after which it cannot be read again for 5 seconds.
 * <p>
 * Recall (every 3 s, telegraphed for 1 s): each player within 10 blocks is struck by the current memory (the residue
 * lash), and the memory is acted out once (fire and blasts respect mobGriefing; a hushed echo nearby swallows the
 * act-out). A grave-set echo within 16 blocks draws the whole recall onto itself instead, paying one graft charge. A
 * mute stone in its chunk halves the recall rate and stops it writing its memories into the chunk.
 * <p>
 * Synced: temper mask, pinned, reading progress, casting. Saved: mask, merged count, home, recall index, pin timer.
 * Never despawns; drops (code, not a loot table) one or two scar fragments and a residual shard per merged temper.
 */
public final class ScarEntity extends Mob {
    public static final double READ_RANGE = 20.0D;
    public static final int READ_TICKS = 60;
    public static final int PIN_TICKS = 160;
    public static final int UNREADABLE_TICKS = 100;
    public static final int RECALL_TICKS = 60;
    public static final int TELEGRAPH_TICKS = 20;
    public static final double RECALL_RADIUS = 10.0D;
    public static final double DECOY_RANGE = 16.0D;
    public static final double LEASH = 20.0D;
    public static final double BAR_RANGE = 32.0D;
    public static final int WRITE_TICKS = 200;

    private static final EntityDataAccessor<Integer> DATA_MASK = SynchedEntityData.defineId(ScarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PINNED = SynchedEntityData.defineId(ScarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_READ = SynchedEntityData.defineId(ScarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CASTING = SynchedEntityData.defineId(ScarEntity.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random),
            Component.translatable("entity.mnemolith.scar"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int merged = 3;
    private @Nullable BlockPos home;
    private int recallIndex;
    private int recallTicks;
    private int pinTicks;
    private int unreadableTicks;
    private int writeTicks;
    private @Nullable Vec3 driftTarget;
    /** Last recall's memory and how it landed ("players", "decoy", "none"); the QA reads it. */
    private @Nullable ImprintTag lastRecall;
    private String lastRecallTarget = "none";

    public ScarEntity(EntityType<? extends ScarEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setPersistenceRequired();
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 120.0D).add(Attributes.MOVEMENT_SPEED, 0.2D).add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ARMOR, 4.0D).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_MASK, 1 << Temper.GRAVE.id());
        entityData.define(DATA_PINNED, false);
        entityData.define(DATA_READ, 0);
        entityData.define(DATA_CASTING, false);
    }

    /** Called once when the storm merges, before the entity is added. */
    public void setup(int mask, int merged, BlockPos home) {
        this.entityData.set(DATA_MASK, mask == 0 ? 1 << Temper.GRAVE.id() : mask);
        this.merged = Math.max(1, merged);
        this.home = home.immutable();
        this.applyHealth();
        this.setHealth(this.getMaxHealth());
    }

    /** 60 + 20 per merged residue, at most 180. */
    public static double maxHealthFor(int merged) {
        return Math.min(180.0D, 60.0D + 20.0D * merged);
    }

    private void applyHealth() {
        var attribute = this.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(maxHealthFor(this.merged));
        }
    }

    // ---- state ----

    public int temperMask() {
        return this.entityData.get(DATA_MASK);
    }

    public List<Temper> tempers() {
        List<Temper> list = new ArrayList<>();
        int mask = this.temperMask();
        for (Temper temper : Temper.values()) {
            if ((mask & (1 << temper.id())) != 0) {
                list.add(temper);
            }
        }
        if (list.isEmpty()) {
            list.add(Temper.GRAVE);
        }
        return list;
    }

    public int merged() {
        return this.merged;
    }

    public BlockPos home() {
        if (this.home == null) {
            this.home = this.blockPosition().immutable();
        }
        return this.home;
    }

    public boolean isPinned() {
        return this.entityData.get(DATA_PINNED);
    }

    public boolean isCasting() {
        return this.entityData.get(DATA_CASTING);
    }

    /** Reading progress in ticks, 0..{@link #READ_TICKS}. */
    public int readProgress() {
        return this.entityData.get(DATA_READ);
    }

    public int recallIndex() {
        return this.recallIndex;
    }

    public @Nullable ImprintTag lastRecall() {
        return this.lastRecall;
    }

    public String lastRecallTarget() {
        return this.lastRecallTarget;
    }

    /** The memory the next recall plays. */
    public Temper nextTemper() {
        List<Temper> tempers = this.tempers();
        return tempers.get(Math.floorMod(this.recallIndex, tempers.size()));
    }

    public void pin(int ticks) {
        this.pinTicks = ticks;
        this.entityData.set(DATA_PINNED, true);
        this.entityData.set(DATA_READ, 0);
        this.entityData.set(DATA_CASTING, false);
        this.recallTicks = 0;
    }

    private boolean muted(ServerLevel level) {
        return LoadedChunkMemory.isMuted(level, this.blockPosition());
    }

    // ---- ticking ----

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.setNoGravity(true);
        if (this.level().isClientSide()) {
            List<Temper> tempers = this.tempers();
            int count = this.isCasting() ? 3 : 1;
            for (int i = 0; i < count; i++) {
                Temper temper = tempers.get(this.random.nextInt(tempers.size()));
                this.level().addParticle(EchoGrafts.particle(temper), this.getRandomX(0.9D), this.getRandomY(), this.getRandomZ(0.9D), 0.0D, 0.03D, 0.0D);
            }
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.serverTick(level, level.players());
    }

    /** One server tick against {@code players} (the level's players; the QA passes its fake player). */
    public void serverTick(ServerLevel level, Iterable<? extends ServerPlayer> players) {
        if (this.pinTicks > 0 && --this.pinTicks == 0) {
            this.entityData.set(DATA_PINNED, false);
            this.unreadableTicks = UNREADABLE_TICKS;
        }
        if (this.unreadableTicks > 0) {
            this.unreadableTicks--;
        }
        this.sense(level, players);
        boolean muted = this.muted(level);
        if (!this.isPinned()) {
            this.recallTicks += muted && this.tickCount % 2 == 0 ? 0 : 1;
            if (this.recallTicks == RECALL_TICKS - TELEGRAPH_TICKS) {
                this.entityData.set(DATA_CASTING, true);
                level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5F, 0.7F);
            }
            if (this.recallTicks >= RECALL_TICKS) {
                this.recallTicks = 0;
                this.entityData.set(DATA_CASTING, false);
                this.recall(level, players);
            }
        }
        if (++this.writeTicks >= WRITE_TICKS) {
            this.writeTicks = 0;
            if (!muted) {
                ImprintWriter.write(level, this.blockPosition(), List.of(this.nextTemper().tag()), null, false);
            }
        }
        if (this.tickCount % 20 == 0) {
            this.updateBar(level, players);
        }
    }

    /** The lens reading, every 5 ticks (the QA calls this alone with its fake player). */
    public void sense(ServerLevel level, Iterable<? extends ServerPlayer> players) {
        if (this.tickCount % 5 == 0) {
            this.checkReading(level, players);
        }
    }

    private void checkReading(ServerLevel level, Iterable<? extends ServerPlayer> players) {
        if (this.isPinned() || this.unreadableTicks > 0) {
            if (this.readProgress() > 0) {
                this.entityData.set(DATA_READ, 0);
            }
            return;
        }
        ServerPlayer reader = null;
        for (ServerPlayer player : players) {
            if (!player.isSpectator() && Residues.reading(player, this, READ_RANGE)) {
                reader = player;
                break;
            }
        }
        int progress = this.readProgress();
        if (reader == null) {
            if (progress > 0) {
                this.entityData.set(DATA_READ, Math.max(0, progress - 5));
            }
            return;
        }
        progress += 5;
        if (progress >= READ_TICKS) {
            this.pin(PIN_TICKS);
            level.playSound(null, this.blockPosition(), com.mnemolith.audio.ModSounds.LENS_FOCUS.get(), SoundSource.PLAYERS, 1.2F, 0.5F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.5D, this.getZ(), 40, 0.6D, 1.0D, 0.6D, 0.05D);
            reader.sendOverlayMessage(Component.translatable("mnemolith.scar.read", PIN_TICKS / 20));
            Mnemolith.LOGGER.info("Mnemolith scar read by {}", reader.getGameProfile().name());
        } else {
            this.entityData.set(DATA_READ, progress);
        }
    }

    /** Plays the next memory: onto a grave-set decoy echo if one is near, otherwise onto the players around. */
    public void recall(ServerLevel level, Iterable<? extends ServerPlayer> players) {
        Temper temper = this.nextTemper();
        ImprintTag tag = temper.tag();
        this.recallIndex++;
        this.lastRecall = tag;
        EchoEntity decoy = this.decoy(level);
        if (decoy != null) {
            EchoGrafts.spend(decoy, 1);
            Residues.actOut(level, this, tag, decoy.blockPosition());
            level.sendParticles(EchoGrafts.particle(temper), decoy.getX(), decoy.getY() + 1.0D, decoy.getZ(), 16, 0.3D, 0.5D, 0.3D, 0.05D);
            this.lastRecallTarget = "decoy";
            return;
        }
        ServerPlayer first = null;
        for (ServerPlayer player : players) {
            if (player.level() == level && Residues.affects(player)
                    && player.distanceToSqr(this.getX(), this.getY() + 1.0D, this.getZ()) <= RECALL_RADIUS * RECALL_RADIUS) {
                Residues.lash(level, this, tag, temper, player);
                if (first == null) {
                    first = player;
                }
            }
        }
        if (first != null) {
            Residues.actOut(level, this, tag, first.blockPosition());
            this.lastRecallTarget = "players";
        } else {
            this.lastRecallTarget = "none";
        }
    }

    private @Nullable EchoEntity decoy(ServerLevel level) {
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, this.getBoundingBox().inflate(DECOY_RANGE), EchoEntity::isAlive)) {
            if (EchoGrafts.decoy(echo) && echo.distanceToSqr(this) <= DECOY_RANGE * DECOY_RANGE) {
                return echo;
            }
        }
        return null;
    }

    private void updateBar(ServerLevel level, Iterable<? extends ServerPlayer> players) {
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        for (ServerPlayer player : players) {
            boolean near = player.level() == level && player.distanceToSqr(this) <= BAR_RANGE * BAR_RANGE;
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
        if (level.getNearestPlayer(this, BAR_RANGE) == null && this.getHealth() < this.getMaxHealth() && this.tickCount % 40 == 0) {
            this.heal(2.0F);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        BlockPos home = this.home();
        Vec3 homeCenter = Vec3.atCenterOf(home).add(0.0D, 2.0D, 0.0D);
        ServerPlayer target = null;
        double best = LEASH * LEASH;
        for (ServerPlayer player : level.players()) {
            double d = player.distanceToSqr(homeCenter);
            if (Residues.affects(player) && d < best) {
                best = d;
                target = player;
            }
        }
        Vec3 goal;
        if (target != null) {
            Vec3 away = this.position().subtract(target.position());
            double flat = Math.sqrt(away.x * away.x + away.z * away.z);
            if (flat < 1.0E-3D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
                flat = 1.0D;
            }
            double keep = Mth.clamp(flat, 4.0D, 7.0D);
            goal = new Vec3(target.getX() + away.x / flat * keep, target.getY() + 1.5D, target.getZ() + away.z / flat * keep);
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        } else {
            if (this.driftTarget == null || this.position().distanceToSqr(this.driftTarget) < 0.5D || this.random.nextInt(100) == 0) {
                this.driftTarget = homeCenter.add(this.random.nextDouble() * 8.0D - 4.0D, this.random.nextDouble() * 2.0D, this.random.nextDouble() * 8.0D - 4.0D);
            }
            goal = this.driftTarget;
        }
        if (goal.distanceToSqr(homeCenter) > LEASH * LEASH) {
            goal = homeCenter.add(goal.subtract(homeCenter).normalize().scale(LEASH));
        }
        double speed = this.isPinned() ? 0.02D : 0.12D;
        Vec3 to = goal.subtract(this.position());
        double length = to.length();
        this.setDeltaMovement(length < 0.1D ? Vec3.ZERO : to.scale(Math.min(speed, length) / length));
        if (target == null && this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D) {
            Vec3 motion = this.getDeltaMovement();
            float yaw = (float) (Math.atan2(motion.z, motion.x) * (180.0D / Math.PI)) - 90.0F;
            this.setYRot(yaw);
            this.yBodyRot = yaw;
        }
    }

    @Override
    public void travel(Vec3 input) {
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
    }

    // ---- damage, drops ----

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return !this.isPinned() || super.isInvulnerableTo(level, source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        for (net.minecraft.world.item.ItemStack stack : drops(level, this.merged, this.tempers(), this.home())) {
            this.spawnAtLocation(level, stack);
        }
    }

    /** One scar fragment (two if five or more residues merged) and a strength-4 residual shard per merged temper. */
    public static List<net.minecraft.world.item.ItemStack> drops(ServerLevel level, int merged, List<Temper> tempers, BlockPos home) {
        List<net.minecraft.world.item.ItemStack> list = new ArrayList<>();
        list.add(new net.minecraft.world.item.ItemStack(ModItems.SCAR_FRAGMENT.get(), merged >= 5 ? 2 : 1));
        for (Temper temper : tempers) {
            list.add(Residues.shard(temper.tag(), 4, home, level.getGameTime()));
        }
        return list;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            Mnemolith.LOGGER.info("Mnemolith scar defeated merged={} at {} by {}", this.merged, this.blockPosition().toShortString(),
                    source.getEntity() == null ? "?" : source.getEntity().getName().getString());
            level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        this.bossEvent.removeAllPlayers();
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return this.isPinned() || super.isCurrentlyGlowing();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // ---- save ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("scar_mask", this.temperMask());
        output.putInt("scar_merged", this.merged);
        output.store("scar_home", BlockPos.CODEC, this.home());
        output.putInt("scar_recall", this.recallIndex);
        output.putInt("scar_pin", this.pinTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int mask = input.getIntOr("scar_mask", 0);
        this.entityData.set(DATA_MASK, mask == 0 ? 1 << Temper.GRAVE.id() : mask);
        this.merged = Math.max(1, input.getIntOr("scar_merged", 3));
        this.home = input.read("scar_home", BlockPos.CODEC).orElse(null);
        this.recallIndex = input.getIntOr("scar_recall", 0);
        this.pinTicks = Math.max(0, input.getIntOr("scar_pin", 0));
        this.entityData.set(DATA_PINNED, this.pinTicks > 0);
        this.applyHealth();
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }
}
