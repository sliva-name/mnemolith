package com.mnemolith.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/** Shared pose and rare twin flag for the three memory mobs. */
public abstract class MemoryMob extends Monster {
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(MemoryMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_TWIN = SynchedEntityData.defineId(MemoryMob.class, EntityDataSerializers.BOOLEAN);

    protected MemoryMob(EntityType<? extends MemoryMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, MobActions.IDLE);
        builder.define(DATA_TWIN, false);
    }

    public int action() {
        return this.entityData.get(DATA_ACTION);
    }

    public void setAction(int action) {
        this.entityData.set(DATA_ACTION, action);
    }

    public boolean twin() {
        return this.entityData.get(DATA_TWIN);
    }

    public void setTwin(boolean twin) {
        this.entityData.set(DATA_TWIN, twin);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (this.random.nextDouble() < MobTuning.TWIN_CHANCE) {
            this.setTwin(true);
        }
        return result;
    }

    protected void applyAttackDamage(double damage) {
        var attribute = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (attribute != null) {
            attribute.setBaseValue(damage);
        }
    }

    @Override
    public void aiStep() {
        if (this.action() == MobActions.ATTACK && this.tickCount % 8 == 0 && !this.level().isClientSide()) {
            this.setAction(MobActions.IDLE);
        }
        super.aiStep();
    }

    public final ServerLevel serverLevel() {
        return (ServerLevel) this.level();
    }
}
