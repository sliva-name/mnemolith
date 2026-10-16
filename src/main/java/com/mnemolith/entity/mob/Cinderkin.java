package com.mnemolith.entity.mob;

import java.util.List;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.Level;

/**
 * The cinderkin: a memory that fell into the Nether, burning down to cinders. Hostile, fire immune, glowing; its hit
 * sets you on fire. Cold puts it out: a snowball deals {@link #SNOWBALL_DAMAGE} and water hurts it. Dying in the Nether
 * it leaves a fire imprint; killed by a player it drops memory embers (loot table). See {@code docs/design/embers.md}.
 */
public class Cinderkin extends Monster {
    public static final float SNOWBALL_DAMAGE = 5.0F;
    public static final int IGNITE_SECONDS = 4;

    public Cinderkin(EntityType<? extends Cinderkin> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof Snowball) {
            // cold puts a burning memory out
            amount = Math.max(amount, SNOWBALL_DAMAGE);
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            living.igniteForSeconds(IGNITE_SECONDS);
        }
        return hit;
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = this.dead || this.isRemoved();
        super.die(source);
        if (!wasDead && this.level() instanceof ServerLevel level && level.dimension() == Level.NETHER) {
            ImprintWriter.write(level, this.blockPosition(), List.of(ImprintTag.FIRE), null, false);
        }
    }

    @Override
    public float getVoicePitch() {
        return 1.3F + (this.random.nextFloat() - 0.5F) * 0.15F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.FADED_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return net.minecraft.sounds.SoundEvents.BLAZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH;
    }
}
