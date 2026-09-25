package com.mnemolith.command;

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
    private PerfCommand() {}

    /**
     * Times the write path, a throttled burst, pressure scoring, a lens walk, and strider sensor scans.
     * The chunk is beside the command source so {@code smoke} still owns the source chunk.
     */
    public static int perf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition()).offset(48, 0, 0);
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

        final int pathWrites = 32;
        long pathStart = System.nanoTime();
        int pathAccepted = 0;
        for (int i = 0; i < pathWrites; i++) {
            if (ImprintWriter.tryWrite(level, pos, ImprintTag.PATH, null, false)) {
                pathAccepted++;
            }
        }
        long pathNs = System.nanoTime() - pathStart;

        LoadedChunkMemory.clear(level.getChunkAt(pos));
        final int buildAttempts = 128;
        long buildStart = System.nanoTime();
        int buildAccepted = 0;
        for (int i = 0; i < buildAttempts; i++) {
            if (ImprintWriter.tryWrite(level, pos, ImprintTag.BUILD, null, true)) {
                buildAccepted++;
            }
        }
        long buildNs = System.nanoTime() - buildStart;

        LoadedChunkMemory.clear(level.getChunkAt(pos));
        ImprintTag[] tags = ImprintTag.values();
        for (int i = 0; i < 8; i++) {
            ImprintWriter.tryWrite(level, pos, tags[i], null, false);
        }
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        final int scoreCalls = 1000;
        long scoreStart = System.nanoTime();
        int score = 0;
        if (memory != null) {
            for (int i = 0; i < scoreCalls; i++) {
                score = MemoryPressure.score(memory);
            }
        }
        long scoreNs = System.nanoTime() - scoreStart;

        long syncStart = System.nanoTime();
        int syncWalks = 0;
        int syncSkipped = 0;
        for (int i = 0; i < 2; i++) {
            if (PressureSync.timedPoll(level, pos) == 0) {
                syncSkipped++;
            } else {
                syncWalks++;
            }
        }
        long syncNs = System.nanoTime() - syncStart;

        final int sensorCalls = 200;
        long sensorStart = System.nanoTime();
        for (int i = 0; i < sensorCalls; i++) {
            PathLedger.nearestImprint(level, pos, null);
            PathLedger.higherPressure(level, pos);
        }
        long sensorNs = System.nanoTime() - sensorStart;

        int reportedScore = score;
        long reportedPathNs = pathNs;
        long reportedBuildNs = buildNs;
        long reportedScoreNs = scoreNs;
        long reportedSyncNs = syncNs;
        int reportedSkipped = syncSkipped;
        long reportedSensorNs = sensorNs;
        Mnemolith.LOGGER.info(
                "Mnemolith perf pathWrites={} pathAccepted={} pathNs={} buildAttempts={} buildAccepted={} buildNs={} scoreCalls={} scoreNs={} score={} syncWalks={} syncSkipped={} syncNs={} sensorCalls={} sensorNs={}",
                pathWrites,
                pathAccepted,
                pathNs,
                buildAttempts,
                buildAccepted,
                buildNs,
                scoreCalls,
                scoreNs,
                reportedScore,
                syncWalks,
                syncSkipped,
                syncNs,
                sensorCalls,
                sensorNs);
        source.sendSuccess(() -> Component.translatable(
                "mnemolith.command.perf",
                reportedPathNs,
                reportedBuildNs,
                reportedScoreNs,
                reportedSyncNs,
                reportedSkipped,
                reportedSensorNs), true);
        return score;
    }
}
