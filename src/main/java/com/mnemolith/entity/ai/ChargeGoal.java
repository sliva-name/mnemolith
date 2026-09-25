package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.EchoStrider;
import java.util.EnumSet;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.entity.MobActions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class ChargeGoal extends Goal {
    private final EchoStrider strider;

    public ChargeGoal(EchoStrider strider) {
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

