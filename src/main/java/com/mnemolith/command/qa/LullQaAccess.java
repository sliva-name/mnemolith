package com.mnemolith.command.qa;

import com.mnemolith.pressure.PressureBand;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Lets the storm-lull live test reuse the storm QA's fracture helpers. */
public final class LullQaAccess {
    private LullQaAccess() {}

    public static PressureBand loud(ServerLevel level, BlockPos pos, PressureBand target) {
        return StormQa.loud(level, pos, target);
    }

    public static void resetArea(ServerLevel level, BlockPos pos) {
        StormQa.resetArea(level, pos);
    }
}
