package com.mnemolith.gametest;

import com.mnemolith.command.qa.DebugCommands;
import com.mnemolith.echo.storm.RecollectionStorm;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** The operator debug commands set a pressure band and can start and stop a storm without leaving it running. */
final class DebugCommandTests {
    private DebugCommandTests() {}

    static void pressureAndStorm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        int fractured = DebugCommands.applyPressure(level, pos, 80);
        helper.assertTrue(MemoryPressure.band(fractured) == PressureBand.FRACTURE, "fracture pressure was " + fractured);
        int saturated = DebugCommands.applyPressure(level, pos, 20);
        helper.assertTrue(MemoryPressure.band(saturated) == PressureBand.SATURATED, "saturated pressure was " + saturated);
        int calm = DebugCommands.applyPressure(level, pos, 0);
        helper.assertTrue(MemoryPressure.band(calm) == PressureBand.CALM, "calm pressure was " + calm);
        RecollectionStorm storm = Storms.start(level, level.getChunkAt(pos).getPos(), "debug-test");
        helper.assertTrue(Storms.at(level, storm.center()) == storm, "storm did not start");
        Storms.finish(level, storm, Storms.End.PASSED);
        helper.assertTrue(Storms.at(level, storm.center()) == null, "storm did not stop");
        helper.succeed();
    }
}
