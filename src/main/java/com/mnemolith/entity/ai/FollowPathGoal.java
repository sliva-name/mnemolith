package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.EchoStrider;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import com.mnemolith.entity.MobTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class FollowPathGoal extends Goal {
    private final EchoStrider strider;
    // Path-following state lives with the goal: one goal instance per strider, only this goal reads it.
    private int ownerCursor;
    private @Nullable UUID pathOwner;
    private @Nullable BlockPos waypoint;
    private @Nullable BlockPos lastImprint;
    private boolean shadowing;

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
        this.strider.stopPhasing();
    }

    @Override
    public void tick() {
        if (this.shadowing && this.strider.tickCount % 40 == 0) {
            this.pick();
        }
        if (this.waypoint == null || this.strider.distanceToSqr(Vec3.atLowerCornerOf(this.waypoint).add(0.5D, 0.0D, 0.5D)) < MobTuning.PATH_ARRIVE_SQR) {
            if (this.pathOwner != null && this.waypoint != null) {
                PathLedger.consume(this.pathOwner, this.waypoint);
            }
            this.pick();
        }
        if (this.waypoint == null) {
            return;
        }
        this.strider.stepPhase(this.waypoint);
        if (!this.strider.noPhysics && MobTuning.sensorDue(this.strider.tickCount, this.strider.getNavigation().isDone())) {
            BlockPos waypoint = this.waypoint;
            this.strider.getNavigation().moveTo(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D, 0.9D);
        }
    }

    private void pick() {
        ServerLevel level = getServerLevel(this.strider);
        List<UUID> owners = PathLedger.owners();
        this.waypoint = null;
        this.shadowing = false;
        var echo = this.strider.shadowedEcho(level);
        if (echo != null) {
            this.pathOwner = null;
            this.waypoint = this.strider.shadowPoint(echo);
            this.shadowing = true;
            return;
        }
        if (!owners.isEmpty()) {
            int count = this.strider.twin() ? Math.min(2, owners.size()) : 1;
            for (int attempt = 0; attempt < count; attempt++) {
                UUID owner = owners.get(Math.floorMod(this.ownerCursor++, owners.size()));
                BlockPos next = PathLedger.peek(owner);
                if (next != null) {
                    this.pathOwner = owner;
                    this.waypoint = next;
                    return;
                }
            }
        }
        BlockPos imprint = PathLedger.nearestImprint(level, this.strider.blockPosition(), this.lastImprint);
        if (imprint != null) {
            this.lastImprint = imprint;
            this.pathOwner = null;
            this.waypoint = imprint;
            return;
        }
        BlockPos gradient = PathLedger.higherPressure(level, this.strider.blockPosition());
        if (gradient != null) {
            this.pathOwner = null;
            this.waypoint = gradient;
            return;
        }
        int dx = this.strider.getRandom().nextInt(9) - 4;
        int dz = this.strider.getRandom().nextInt(9) - 4;
        this.waypoint = this.strider.blockPosition().offset(dx, 0, dz);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

