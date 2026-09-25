package com.mnemolith.echo.job;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Reach tests shared by mining, building, and farming. */
final class JobReach {
    private JobReach() {}

    /** Standing at {@code feet}, the echo can break or place at {@code pos}: in reach, not inside it, and next to it or in sight of it. */
    static boolean canWorkOn(ServerLevel level, BlockPos feet, BlockPos pos) {
        if (feet.equals(pos) || feet.above().equals(pos)) {
            return false;
        }
        Vec3 eye = new Vec3(feet.getX() + 0.5D, feet.getY() + 1.62D, feet.getZ() + 0.5D);
        Vec3 center = Vec3.atCenterOf(pos);
        if (eye.distanceToSqr(center) > EchoWork.REACH * EchoWork.REACH) {
            return false;
        }
        if (adjacent(feet, pos) || adjacent(feet.above(), pos)) {
            return true;
        }
        BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private static boolean adjacent(BlockPos a, BlockPos b) {
        return a.distManhattan(b) == 1;
    }
}
