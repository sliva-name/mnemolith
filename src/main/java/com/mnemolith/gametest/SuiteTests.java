package com.mnemolith.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import com.mnemolith.Mnemolith;
import com.mnemolith.command.qa.QaReport;
import com.mnemolith.command.qa.QaSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Each {@code /mnemolith ...qa} suite as one game test. The test calls the same {@code check(level, origin)} the
 * command calls and fails with the names of the checks that did not pass (the full check line and notes are logged
 * as {@code Mnemolith <suite> ...}, exactly as for the command).
 */
final class SuiteTests {
    private SuiteTests() {}

    /** Notes kept in the failure message; the rest are in the log. */
    private static final int MESSAGE_NOTES = 6;
    /**
     * Blocks between two suites' origins. The suites run in the same batch and each builds its sites up to ~50 chunks
     * from its origin; run from one spot (as the commands usually are) they would share chunks, and one suite's
     * leftovers (a mute pocket, a replicant) could stand in another's way.
     */
    static final int LANE_BLOCKS = 4096;

    /**
     * Runs {@code suite} from the test position shifted {@code lane} lanes south. A check named in {@code waived}
     * cannot work in the game test world; its result is logged but does not fail the test.
     */
    static void run(GameTestHelper helper, int lane, BiFunction<ServerLevel, BlockPos, QaReport> suite, Map<String, String> waived) {
        BlockPos test = helper.absolutePos(BlockPos.ZERO).offset(0, 0, lane * LANE_BLOCKS);
        // Stand on the surface there, as a player running the command would (the test block itself is underground).
        BlockPos origin = test.atY(QaSupport.column(helper.getLevel(), test.getX() >> 4, test.getZ() >> 4).getY());
        QaReport report = suite.apply(helper.getLevel(), origin);
        List<String> failed = new ArrayList<>(report.failed());
        waived.forEach((check, why) -> {
            Mnemolith.LOGGER.info("Mnemolith gametest {} waived {} (passed={}): {}", report.suite(), check, !failed.contains(check), why);
            failed.remove(check);
        });
        if (failed.isEmpty()) {
            helper.succeed();
            return;
        }
        StringBuilder message = new StringBuilder()
                .append(report.suite()).append(' ').append(report.passed()).append('/').append(report.total())
                .append(", failed: ").append(String.join(", ", failed));
        int shown = 0;
        for (String note : report.notes()) {
            if (shown++ == MESSAGE_NOTES) {
                message.append(" | ...");
                break;
            }
            message.append(" | ").append(note);
        }
        throw helper.assertionException(Component.literal(message.toString()));
    }
}
