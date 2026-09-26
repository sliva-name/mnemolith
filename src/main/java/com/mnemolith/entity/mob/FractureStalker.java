package com.mnemolith.entity.mob;

import com.mnemolith.armory.ArmoryItems;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/** A fast hunter of loud chunks. It copies a neighbour's target and, rarely, grows into a heavier scarred stalker. */
public class FractureStalker extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_ELITE = SynchedEntityData.defineId(FractureStalker.class, EntityDataSerializers.BOOLEAN);

    public FractureStalker(EntityType<? extends FractureStalker> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    public boolean elite() {
        return this.entityData.get(DATA_ELITE);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.mnemolith.audio.ModSounds.STALKER_AMBIENT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return com.mnemolith.audio.ModSounds.STALKER_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return com.mnemolith.audio.ModSounds.STALKER_DEATH.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ELITE, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LedgerMite.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (this.random.nextFloat() < 0.08F) {
            this.entityData.set(DATA_ELITE, true);
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(28.0D);
            this.setHealth(28.0F);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0D);
            this.setCustomName(net.minecraft.network.chat.Component.translatable("entity.mnemolith.fracture_stalker.elite"));
        }
        return result;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level) || this.getTarget() != null || this.tickCount % 20 != 0) {
            return;
        }
        for (FractureStalker other : level.getEntitiesOfClass(FractureStalker.class, this.getBoundingBox().inflate(12.0D), stalker -> stalker != this)) {
            if (other.getTarget() != null) {
                this.setTarget(other.getTarget());
                return;
            }
        }
    }

    private boolean gaveScale;

    public boolean gaveScale() {
        return this.gaveScale;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (this.elite()) {
            this.spawnAtLocation(level, new ItemStack(ArmoryItems.SCAR_SINEW.get()));
            this.spawnAtLocation(level, new ItemStack(ArmoryItems.GRAVE_SCALE.get(), 2));
            this.gaveScale = true;
        } else if (this.random.nextFloat() < 0.7F) {
            this.spawnAtLocation(level, new ItemStack(ArmoryItems.GRAVE_SCALE.get(), 1 + this.random.nextInt(2)));
            this.gaveScale = true;
        }
    }
}
