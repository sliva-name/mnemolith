package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.EchoStrider;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.mnemolith.entity.MobTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class FollowPathGoal extends Goal {
    private final EchoStrider strider;

    public FollowPathGoal(EchoStrider strider) {
        this.strider = strider;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return MobTuning.striderEnabled() && !this.strider.wantsCharge() && !this.strider.shouldRetreat();
    }

    @Override
    public void stop() {
        this.strider.noPhysics = false;
        this.strider.phaseTicks = 0;
    }

    @Override
    public void tick() {
        if (this.strider.waypoint == null || this.strider.distanceToSqr(Vec3.atLowerCornerOf(this.strider.waypoint).add(0.5D, 0.0D, 0.5D)) < MobTuning.PATH_ARRIVE_SQR) {
            if (this.strider.pathOwner != null && this.strider.waypoint != null) {
                PathLedger.consume(this.strider.pathOwner, this.strider.waypoint);
            }
            this.pick();
        }
        if (this.strider.waypoint == null) {
            return;
        }
        this.strider.stepPhase(this.strider.waypoint);
        if (!this.strider.noPhysics && MobTuning.sensorDue(this.strider.tickCount, this.strider.getNavigation().isDone())) {
            BlockPos waypoint = this.strider.waypoint;
            this.strider.getNavigation().moveTo(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D, 0.9D);
        }
    }

    private void pick() {
        ServerLevel level = this.strider.serverLevel();
        List<UUID> owners = PathLedger.owners();
        this.strider.waypoint = null;
        if (!owners.isEmpty()) {
            int count = this.strider.twin() ? Math.min(2, owners.size()) : 1;
            for (int attempt = 0; attempt < count; attempt++) {
                UUID owner = owners.get(Math.floorMod(this.strider.ownerCursor++, owners.size()));
                BlockPos next = PathLedger.peek(owner);
                if (next != null) {
                    this.strider.pathOwner = owner;
                    this.strider.waypoint = next;
                    return;
                }
            }
        }
        BlockPos imprint = PathLedger.nearestImprint(level, this.strider.blockPosition(), this.strider.lastImprint);
        if (imprint != null) {
            this.strider.lastImprint = imprint;
            this.strider.pathOwner = null;
            this.strider.waypoint = imprint;
            return;
        }
        BlockPos gradient = PathLedger.higherPressure(level, this.strider.blockPosition());
        if (gradient != null) {
            this.strider.pathOwner = null;
            this.strider.waypoint = gradient;
            return;
        }
        int dx = this.strider.random().nextInt(9) - 4;
        int dz = this.strider.random().nextInt(9) - 4;
        this.strider.waypoint = this.strider.blockPosition().offset(dx, 0, dz);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

