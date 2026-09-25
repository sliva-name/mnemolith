package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.Archivist;
import java.util.EnumSet;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.MobTuning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class StalkGoal extends Goal {
    private final Archivist archivist;

    public StalkGoal(Archivist archivist) {
        this.archivist = archivist;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return MobTuning.archivistEnabled()
                && this.archivist.stunTicks <= 0
                && this.archivist.fleeTicks <= 0
                && (this.archivist.interest != null || this.archivist.dropped != null);
    }

    @Override
    public void tick() {
        if (this.archivist.dropped != null && this.archivist.dropped.isAlive() && ImprintSlips.isSlip(this.archivist.dropped.getItem())) {
            ItemEntity drop = this.archivist.dropped;
            if (MobTuning.sensorDue(this.archivist.tickCount, this.archivist.getNavigation().isDone())) {
                this.archivist.getNavigation().moveTo(drop, 1.05D);
            }
            if (this.archivist.distanceToSqr(drop) < 2.0D && this.archivist.stealCooldown <= 0) {
                ItemStack stolen = drop.getItem().split(1);
                if (drop.getItem().isEmpty()) {
                    drop.discard();
                }
                this.archivist.finishSteal(this.archivist.serverLevel(), null, stolen);
            }
            return;
        }
        ServerPlayer player = this.archivist.interest;
        if (player == null || !player.isAlive()) {
            this.archivist.interest = null;
            return;
        }
        this.archivist.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (MobTuning.sensorDue(this.archivist.tickCount, this.archivist.getNavigation().isDone())) {
            this.archivist.getNavigation().moveTo(player, 0.95D);
        }
    }
}

