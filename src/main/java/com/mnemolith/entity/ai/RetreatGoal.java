package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.EchoStrider;
import java.util.EnumSet;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobTuning;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.particle.ModParticles;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class RetreatGoal extends Goal {
    private final EchoStrider strider;

    public RetreatGoal(EchoStrider strider) {
        this.strider = strider;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return MobTuning.striderEnabled() && this.strider.shouldRetreat();
    }

    @Override
    public void start() {
        this.strider.cancelCharge();
        this.strider.noPhysics = false;
        this.strider.setAction(MobActions.FLEE);
        this.strider.setTarget(null);
    }

    @Override
    public void tick() {
        this.strider.setAction(MobActions.FLEE);
        Player focus = this.strider.lensFocus();
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
            MemoryFx.mob(getServerLevel(this.strider), ModParticles.STRIDER_TRAIL.get(), this.strider.getX(), this.strider.getY() + 0.6D, this.strider.getZ(), 2);
        }
    }
}

