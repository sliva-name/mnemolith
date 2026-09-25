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
                && !this.archivist.isStunned()
                && !this.archivist.isFleeing()
                && (this.archivist.interest() != null || this.archivist.dropped() != null || this.archivist.echoTarget() != null);
    }

    @Override
    public void tick() {
        ItemEntity drop = this.archivist.dropped();
        if (drop != null && drop.isAlive() && ImprintSlips.isSlip(drop.getItem())) {
            if (MobTuning.sensorDue(this.archivist.tickCount, this.archivist.getNavigation().isDone())) {
                this.archivist.getNavigation().moveTo(drop, 1.05D);
            }
            if (this.archivist.distanceToSqr(drop) < 2.0D && this.archivist.stealReady()) {
                ItemStack stolen = drop.getItem().split(1);
                if (drop.getItem().isEmpty()) {
                    drop.discard();
                }
                this.archivist.finishSteal(getServerLevel(this.archivist), null, stolen);
            }
            return;
        }
        ServerPlayer player = this.archivist.interest();
        com.mnemolith.entity.echo.EchoEntity echo = this.archivist.echoTarget();
        if (player == null && echo != null) {
            // Stage 3: sneaks up on a working echo and takes one stack.
            this.archivist.getLookControl().setLookAt(echo, 30.0F, 30.0F);
            if (MobTuning.sensorDue(this.archivist.tickCount, this.archivist.getNavigation().isDone())) {
                this.archivist.getNavigation().moveTo(echo, 1.05D);
            }
            if (this.archivist.distanceToSqr(echo) < 6.25D && this.archivist.stealReady()) {
                this.archivist.stealFromEcho(getServerLevel(this.archivist), echo);
            }
            return;
        }
        if (player == null || !player.isAlive()) {
            this.archivist.loseInterest();
            return;
        }
        this.archivist.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (MobTuning.sensorDue(this.archivist.tickCount, this.archivist.getNavigation().isDone())) {
            this.archivist.getNavigation().moveTo(player, 0.95D);
        }
    }
}

