package com.mnemolith.entity.mob;

import com.mnemolith.entity.ai.RetreatGoal;
import com.mnemolith.entity.ai.ChargeGoal;
import com.mnemolith.entity.ai.FollowPathGoal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.MemoryMob;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobTuning;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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
    public int chargeTicks;
    public int chargeCooldown;
    public boolean forceCharge;
    public int phaseTicks;
    public int ownerCursor;
    public @Nullable UUID pathOwner;
    public @Nullable BlockPos waypoint;
    public @Nullable BlockPos lastImprint;

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

    public @Nullable Player lensFocus(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(MobTuning.LENS_FLEE_RANGE);
        for (Player player : level.getEntitiesOfClass(Player.class, box, ChronicleLensItem::isHeld)) {
            if (player.isShiftKeyDown()) {
                return player;
            }
        }
        return null;
    }

    public net.minecraft.util.RandomSource random() {
        return this.random;
    }

    public @Nullable LivingEntity chargeTarget() {
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

    public void stepPhase(BlockPos destination) {
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

}
