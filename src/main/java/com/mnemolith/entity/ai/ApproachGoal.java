package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.MomentReplicant;
import java.util.EnumSet;
import com.mnemolith.entity.MobTuning;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

public final class ApproachGoal extends Goal {
    private final MomentReplicant replicant;

    public ApproachGoal(MomentReplicant replicant) {
        this.replicant = replicant;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return MobTuning.replicantEnabled() && !this.replicant.blinded() && !this.replicant.telegraphing();
    }

    @Override
    public void tick() {
        Player focus = this.replicant.focus();
        Player player = focus != null ? focus : this.replicant.level().getNearestPlayer(this.replicant, 16.0D);
        if (player == null) {
            return;
        }
        if (this.replicant.distanceToSqr(player) > 9.0D && MobTuning.sensorDue(this.replicant.tickCount, this.replicant.getNavigation().isDone())) {
            this.replicant.getNavigation().moveTo(player, 0.9D);
        }
    }
}

