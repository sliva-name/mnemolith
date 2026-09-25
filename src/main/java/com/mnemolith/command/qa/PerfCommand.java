package com.mnemolith.command.qa;

import com.mnemolith.Mnemolith;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.entity.ai.PathLedger;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.network.PressureSync;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import com.mojang.brigadier.context.CommandContext;

/** {@code /mnemolith perf}. Times writes, scoring, a lens walk, and sensor scans. */
public final class PerfCommand {
    private static final int PATH_WRITES = 32;
    private static final int BUILD_ATTEMPTS = 128;
    /** Tags written before scoring; {@code ImprintTag} has more than this, the cap only guards the array. */
    private static final int SCORED_TAGS = 8;
    private static final int SCORE_CALLS = 1000;
    private static final int SYNC_POLLS = 2;
    private static final int SENSOR_CALLS = 200;

    private PerfCommand() {}

    private record Burst(int accepted, long ns) {}

    private record Scoring(int score, long ns) {}

    private record Polls(int walks, int skipped, long ns) {}

    /**
     * Times the write path, a throttled burst, pressure scoring, a lens walk, and strider sensor scans.
     * The chunk is beside the command source so {@code smoke} still owns the source chunk.
     */
    public static int perf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition()).offset(48, 0, 0);
        warmUp(level, pos);
        Burst path = pathWrites(level, pos);
        Burst build = buildBurst(level, pos);
        Scoring scoring = scoring(level, pos);
        Polls sync = lensPolls(level, pos);
        long sensorNs = sensorScans(level, pos);

        Mnemolith.LOGGER.info(
                "Mnemolith perf pathWrites={} pathAccepted={} pathNs={} buildAttempts={} buildAccepted={} buildNs={} scoreCalls={} scoreNs={} score={} syncWalks={} syncSkipped={} syncNs={} sensorCalls={} sensorNs={}",
                PATH_WRITES,
                path.accepted(),
                path.ns(),
                BUILD_ATTEMPTS,
                build.accepted(),
                build.ns(),
                SCORE_CALLS,
                scoring.ns(),
                scoring.score(),
                sync.walks(),
                sync.skipped(),
                sync.ns(),
                SENSOR_CALLS,
                sensorNs);
        source.sendSuccess(() -> Component.translatable(
                "mnemolith.command.perf",
                path.ns(),
                build.ns(),
                scoring.ns(),
                sync.ns(),
                sync.skipped(),
                sensorNs), true);
        return scoring.score();
    }

    /** Touches every timed path once so class loading and first-use costs stay out of the numbers. */
    private static void warmUp(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        LoadedChunkMemory.clear(chunk);
        ImprintWriter.tryWrite(level, pos, ImprintTag.PATH, null, false);
        ChunkMemory warmed = LoadedChunkMemory.existing(chunk);
        if (warmed != null) {
            MemoryPressure.score(warmed);
        }
        PressureSync.timedPoll(level, pos);
        PathLedger.nearestImprint(level, pos, null);
        LoadedChunkMemory.clear(chunk);
    }

    private static Burst pathWrites(ServerLevel level, BlockPos pos) {
        long start = System.nanoTime();
        int accepted = 0;
        for (int i = 0; i < PATH_WRITES; i++) {
            if (ImprintWriter.tryWrite(level, pos, ImprintTag.PATH, null, false)) {
                accepted++;
            }
        }
        long ns = System.nanoTime() - start;
        return new Burst(accepted, ns);
    }

    private static Burst buildBurst(ServerLevel level, BlockPos pos) {
        LoadedChunkMemory.clear(level.getChunkAt(pos));
        long start = System.nanoTime();
        int accepted = 0;
        for (int i = 0; i < BUILD_ATTEMPTS; i++) {
            if (ImprintWriter.tryWrite(level, pos, ImprintTag.BUILD, null, true)) {
                accepted++;
            }
        }
        long ns = System.nanoTime() - start;
        return new Burst(accepted, ns);
    }

    private static Scoring scoring(ServerLevel level, BlockPos pos) {
        LoadedChunkMemory.clear(level.getChunkAt(pos));
        ImprintTag[] tags = ImprintTag.values();
        for (int i = 0; i < Math.min(SCORED_TAGS, tags.length); i++) {
            ImprintWriter.tryWrite(level, pos, tags[i], null, false);
        }
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        long start = System.nanoTime();
        int score = 0;
        if (memory != null) {
            for (int i = 0; i < SCORE_CALLS; i++) {
                score = MemoryPressure.score(memory);
            }
        }
        long ns = System.nanoTime() - start;
        return new Scoring(score, ns);
    }

    private static Polls lensPolls(ServerLevel level, BlockPos pos) {
        long start = System.nanoTime();
        int walks = 0;
        int skipped = 0;
        for (int i = 0; i < SYNC_POLLS; i++) {
            if (PressureSync.timedPoll(level, pos) == 0) {
                skipped++;
            } else {
                walks++;
            }
        }
        long ns = System.nanoTime() - start;
        return new Polls(walks, skipped, ns);
    }

    private static long sensorScans(ServerLevel level, BlockPos pos) {
        long start = System.nanoTime();
        for (int i = 0; i < SENSOR_CALLS; i++) {
            PathLedger.nearestImprint(level, pos, null);
            PathLedger.higherPressure(level, pos);
        }
        return System.nanoTime() - start;
    }
}
