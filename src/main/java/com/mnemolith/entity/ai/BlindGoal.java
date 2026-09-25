package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.MomentReplicant;
import java.util.EnumSet;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobTuning;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class BlindGoal extends Goal {
    private final MomentReplicant replicant;

    public BlindGoal(MomentReplicant replicant) {
        this.replicant = replicant;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.replicant.blinded();
    }

    @Override
    public void tick() {
        Player player = this.replicant.level().getNearestPlayer(this.replicant, 12.0D);
        Vec3 away = player == null ? this.replicant.getLookAngle().scale(-1.0D) : this.replicant.position().subtract(player.position());
        if (away.lengthSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();
        if (MobTuning.sensorDue(this.replicant.tickCount, this.replicant.getNavigation().isDone())) {
            this.replicant.getNavigation().moveTo(this.replicant.getX() + away.x * 6.0D, this.replicant.getY(), this.replicant.getZ() + away.z * 6.0D, 1.2D);
        }
        this.replicant.setAction(MobActions.FLEE);
    }
}

