package com.mnemolith.entity.mob;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.MemoryMob;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobTuning;
import com.mnemolith.entity.ai.PathLedger;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Walks recorded paths, then charges when hurt or the chunk is overloaded. */
public class EchoStrider extends MemoryMob {
    private int chargeTicks;
    private int chargeCooldown;
    private boolean forceCharge;
    private int phaseTicks;
    private int ownerCursor;
    private @Nullable UUID pathOwner;
    private @Nullable BlockPos waypoint;
    private @Nullable BlockPos lastImprint;

    public EchoStrider(EntityType<? extends EchoStrider> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RetreatGoal(this));
        this.goalSelector.addGoal(2, new ChargeGoal(this));
        this.goalSelector.addGoal(3, new FollowPathGoal(this));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        this.applyAttackDamage(MobTuning.striderDamage());
        return result;
    }

    public void beginCharge() {
        this.forceCharge = true;
        this.chargeTicks = MobTuning.CHARGE_TELEGRAPH_TICKS;
        this.chargeCooldown = 0;
        this.setAction(MobActions.TELEGRAPH);
        BlockPos pos = this.blockPosition();
        Mnemolith.LOGGER.info("Mnemolith strider charge at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
        this.playSound(ModSounds.STRIDER_CHARGE.get(), 1.0F, 0.7F);
        if (this.level() instanceof ServerLevel server) {
            MemoryFx.mob(server, ModParticles.STRIDER_TRAIL.get(), this.getX(), this.getY() + 1.0D, this.getZ(), 8);
        }
    }

    public boolean shouldRetreat() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        if (LoadedChunkMemory.isMuted(level, this.blockPosition())) {
            return true;
        }
        return this.lensFocus(level) != null;
    }

    public boolean wantsCharge() {
        if (this.chargeCooldown > 0) {
            return false;
        }
        if (this.forceCharge || this.chargeTicks > 0) {
            return true;
        }
        if (!MobTuning.striderEnabled() || this.shouldRetreat()) {
            return false;
        }
        if (this.getLastHurtByMob() instanceof Player) {
            return true;
        }
        ChunkMemory memory = LoadedChunkMemory.existing(this.serverLevel().getChunkAt(this.blockPosition()));
        int pressure = memory == null ? 0 : memory.cachedPressure();
        return MemoryPressure.band(pressure).ordinal() >= PressureBand.OVERLOADED.ordinal();
    }

    private @Nullable Player lensFocus(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(MobTuning.LENS_FLEE_RANGE);
        for (Player player : level.getEntitiesOfClass(Player.class, box, this::holdingLens)) {
            if (player.isShiftKeyDown()) {
                return player;
            }
        }
        return null;
    }

    private boolean holdingLens(Player player) {
        return player.getMainHandItem().getItem() == ModItems.CHRONICLE_LENS.get()
                || player.getOffhandItem().getItem() == ModItems.CHRONICLE_LENS.get();
    }

    private @Nullable LivingEntity chargeTarget() {
        if (this.getLastHurtByMob() instanceof Player player && player.isAlive()) {
            return player;
        }
        return this.level().getNearestPlayer(this, 16.0D);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(level, ImprintSlips.of(ImprintTag.PATH, this.blockPosition()));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.STRIDER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.STRIDER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.STRIDER_DEATH.get();
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.chargeCooldown > 0) {
            this.chargeCooldown--;
        }
        if (this.phaseTicks > MobTuning.PHASE_LIMIT) {
            this.noPhysics = false;
            this.phaseTicks = 0;
            if (this.action() == MobActions.PHASE) {
                this.setAction(MobActions.IDLE);
            }
        }
    }

    private void stepPhase(BlockPos destination) {
        Vec3 look = Vec3.atLowerCornerOf(destination).add(0.5D, 0.0D, 0.5D).subtract(this.position());
        if (look.lengthSqr() < 0.01D) {
            this.noPhysics = false;
            return;
        }
        Vec3 forward = look.normalize();
        BlockPos ahead = BlockPos.containing(this.getX() + forward.x, this.getY() + 0.4D, this.getZ() + forward.z);
        BlockState state = this.level().getBlockState(ahead);
        boolean soft = !state.isAir() && !state.isSolid();
        if (soft && this.phaseTicks < MobTuning.PHASE_LIMIT) {
            this.noPhysics = true;
            this.phaseTicks++;
            this.setAction(MobActions.PHASE);
            this.setDeltaMovement(forward.scale(0.18D));
            return;
        }
        this.noPhysics = false;
        if (this.phaseTicks > 0) {
            this.phaseTicks = 0;
            if (this.action() == MobActions.PHASE) {
                this.setAction(MobActions.IDLE);
            }
        }
    }

    private static final class RetreatGoal extends Goal {
        private final EchoStrider strider;

        private RetreatGoal(EchoStrider strider) {
            this.strider = strider;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return MobTuning.striderEnabled() && this.strider.shouldRetreat();
        }

        @Override
        public void start() {
            this.strider.forceCharge = false;
            this.strider.chargeTicks = 0;
            this.strider.noPhysics = false;
            this.strider.setAction(MobActions.FLEE);
            this.strider.setTarget(null);
        }

        @Override
        public void tick() {
            this.strider.setAction(MobActions.FLEE);
            Player focus = this.strider.lensFocus(this.strider.serverLevel());
            Vec3 away;
            if (focus != null) {
                away = this.strider.position().subtract(focus.position());
            } else {
                away = this.strider.getLookAngle().scale(-1.0D);
            }
            if (away.lengthSqr() < 0.01D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            away = away.normalize();
            if (MobTuning.sensorDue(this.strider.tickCount, this.strider.getNavigation().isDone())) {
                double x = this.strider.getX() + away.x * 8.0D;
                double z = this.strider.getZ() + away.z * 8.0D;
                this.strider.getNavigation().moveTo(x, this.strider.getY(), z, 1.25D);
            }
            if (this.strider.tickCount % 10 == 0) {
                MemoryFx.mob(this.strider.serverLevel(), ModParticles.STRIDER_TRAIL.get(), this.strider.getX(), this.strider.getY() + 0.6D, this.strider.getZ(), 2);
            }
        }
    }

    private static final class ChargeGoal extends Goal {
        private final EchoStrider strider;

        private ChargeGoal(EchoStrider strider) {
            this.strider = strider;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.strider.wantsCharge();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            if (this.strider.chargeTicks <= 0) {
                this.strider.beginCharge();
            }
            this.strider.noPhysics = false;
        }

        @Override
        public void stop() {
            this.strider.forceCharge = false;
            if (this.strider.action() == MobActions.TELEGRAPH) {
                this.strider.setAction(MobActions.IDLE);
            }
        }

        @Override
        public void tick() {
            LivingEntity target = this.strider.chargeTarget();
            if (target != null) {
                this.strider.getLookControl().setLookAt(target, 30.0F, 30.0F);
                this.strider.setTarget(target);
            }
            if (this.strider.chargeTicks > 0) {
                this.strider.chargeTicks--;
                this.strider.setAction(MobActions.TELEGRAPH);
                return;
            }
            this.strider.setAction(MobActions.ATTACK);
            if (target == null) {
                this.strider.chargeCooldown = 40;
                this.strider.forceCharge = false;
                return;
            }
            this.strider.getNavigation().moveTo(target, 1.35D);
            if (this.strider.distanceToSqr(target) < 4.0D) {
                this.strider.doHurtTarget(this.strider.serverLevel(), target);
                this.strider.playSound(ModSounds.STRIDER_CHARGE.get(), 1.0F, 1.2F);
                this.strider.chargeCooldown = 40;
                this.strider.forceCharge = false;
                this.strider.chargeTicks = 0;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    private static final class FollowPathGoal extends Goal {
        private final EchoStrider strider;

        private FollowPathGoal(EchoStrider strider) {
            this.strider = strider;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return MobTuning.striderEnabled() && !this.strider.wantsCharge() && !this.strider.shouldRetreat();
        }

        @Override
        public void stop() {
            this.strider.noPhysics = false;
            this.strider.phaseTicks = 0;
        }

        @Override
        public void tick() {
            if (this.strider.waypoint == null || this.strider.distanceToSqr(Vec3.atLowerCornerOf(this.strider.waypoint).add(0.5D, 0.0D, 0.5D)) < MobTuning.PATH_ARRIVE_SQR) {
                if (this.strider.pathOwner != null && this.strider.waypoint != null) {
                    PathLedger.consume(this.strider.pathOwner, this.strider.waypoint);
                }
                this.pick();
            }
            if (this.strider.waypoint == null) {
                return;
            }
            this.strider.stepPhase(this.strider.waypoint);
            if (!this.strider.noPhysics && MobTuning.sensorDue(this.strider.tickCount, this.strider.getNavigation().isDone())) {
                BlockPos waypoint = this.strider.waypoint;
                this.strider.getNavigation().moveTo(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D, 0.9D);
            }
        }

        private void pick() {
            ServerLevel level = this.strider.serverLevel();
            List<UUID> owners = PathLedger.owners();
            this.strider.waypoint = null;
            if (!owners.isEmpty()) {
                int count = this.strider.twin() ? Math.min(2, owners.size()) : 1;
                for (int attempt = 0; attempt < count; attempt++) {
                    UUID owner = owners.get(Math.floorMod(this.strider.ownerCursor++, owners.size()));
                    BlockPos next = PathLedger.peek(owner);
                    if (next != null) {
                        this.strider.pathOwner = owner;
                        this.strider.waypoint = next;
                        return;
                    }
                }
            }
            BlockPos imprint = PathLedger.nearestImprint(level, this.strider.blockPosition(), this.strider.lastImprint);
            if (imprint != null) {
                this.strider.lastImprint = imprint;
                this.strider.pathOwner = null;
                this.strider.waypoint = imprint;
                return;
            }
            BlockPos gradient = PathLedger.higherPressure(level, this.strider.blockPosition());
            if (gradient != null) {
                this.strider.pathOwner = null;
                this.strider.waypoint = gradient;
                return;
            }
            int dx = this.strider.random.nextInt(9) - 4;
            int dz = this.strider.random.nextInt(9) - 4;
            this.strider.waypoint = this.strider.blockPosition().offset(dx, 0, dz);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }
}
