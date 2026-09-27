package com.mnemolith.recall;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * How a {@code distorted} gesture is shown locally: yaw plus fifteen degrees, and the site
 * shifted one block to the side. A replicant does not use this; it plays the stored yaw.
 */
public final class RecallSpace {
    public static final float YAW_NOISE = 15.0F;

    private RecallSpace() {}

    public static float yaw(Gesture gesture) {
        return gesture.distorted() ? gesture.yaw() + YAW_NOISE : gesture.yaw();
    }

    public static BlockPos place(Gesture gesture) {
        if (!gesture.distorted()) {
            return gesture.pos();
        }
        return gesture.pos().relative(Direction.fromYRot(gesture.yaw()).getClockWise());
    }

    public static List<BlockPos> trail(Gesture gesture) {
        if (!gesture.distorted()) {
            return gesture.trail();
        }
        Direction side = Direction.fromYRot(gesture.yaw()).getClockWise();
        List<BlockPos> shifted = new ArrayList<>(gesture.trail().size());
        for (BlockPos step : gesture.trail()) {
            shifted.add(step.relative(side));
        }
        return List.copyOf(shifted);
    }
}
