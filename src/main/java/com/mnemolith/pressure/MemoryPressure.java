package com.mnemolith.pressure;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.config.ServerConfig;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.network.PressureSync;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Pressure is the clamped sum of imprint contributions plus instability. Recomputed when memory changes or a chunk loads. */
public final class MemoryPressure {
    /** Copies of one tag, after the strongest, that still add pressure. */
    private static final int DIMINISHED_COPIES = 3;
    private static final int TOP = 1 + DIMINISHED_COPIES;
    private static final int TAGS = ImprintTag.values().length;
    /** Server-thread scratch. A nested score falls back to a fresh list. */
    private static final int[] TOP_VALUES = new int[TAGS * TOP];
    private static final int[] TOP_COUNTS = new int[TAGS];
    private static int scoring;
    /** Chunk loads may run off the tick loop's usual path, so the queue is concurrent. */
    private static final Queue<DeferredSpawn> DEFERRED_SPAWNS = new ConcurrentLinkedQueue<>();

    private MemoryPressure() {}

    public static int score(ChunkMemory memory) {
        if (scoring != 0) {
            return scoreAllocating(memory);
        }
        scoring = 1;
        try {
            return scoreScratch(memory);
        } finally {
            scoring = 0;
        }
    }

    private static int scoreScratch(ChunkMemory memory) {
        return scoreInto(memory, TOP_VALUES, TOP_COUNTS);
    }

    private static int scoreAllocating(ChunkMemory memory) {
        return scoreInto(memory, new int[TAGS * TOP], new int[TAGS]);
    }

    private static int scoreInto(ChunkMemory memory, int[] values, int[] counts) {
        Arrays.fill(counts, 0);
        int count = memory.imprintCount();
        for (int index = 0; index < count; index++) {
            Imprint imprint = memory.imprintAt(index);
            insertTop(values, counts, imprint.tag().ordinal(), imprint.pressureContribution());
        }
        int sum = memory.instability();
        for (int tag = 0; tag < TAGS; tag++) {
            int kept = counts[tag];
            int base = tag * TOP;
            for (int index = 0; index < kept; index++) {
                int contribution = values[base + index];
                if (index == 0) {
                    sum += contribution;
                } else {
                    sum += Math.max(1, contribution / 4);
                }
            }
        }
        return finishScore(memory, sum);
    }

    private static void insertTop(int[] values, int[] counts, int tag, int value) {
        int base = tag * TOP;
        int count = counts[tag];
        if (count == TOP && value <= values[base + TOP - 1]) {
            return;
        }
        int limit = Math.min(count + 1, TOP);
        int slot = limit - 1;
        while (slot > 0 && value > values[base + slot - 1]) {
            values[base + slot] = values[base + slot - 1];
            slot--;
        }
        values[base + slot] = value;
        counts[tag] = limit;
    }

    private static int finishScore(ChunkMemory memory, int sum) {
        int strata = Math.min(memory.strataCount(), CommonConfig.ARCHIVAL_BLEED_CAP.get());
        sum += strata * CommonConfig.ARCHIVAL_BLEED.get();
        return Math.min(CommonConfig.PRESSURE_SOFT_CAP.get(), Math.max(0, sum));
    }

    public static PressureBand band(int pressure) {
        double scale = CommonConfig.RECOLLECTION_STORM_THRESHOLD.get();
        int fracture = scale(CommonConfig.FRACTURE_THRESHOLD.get(), scale);
        int overloaded = scale(CommonConfig.OVERLOADED_THRESHOLD.get(), scale);
        int saturated = scale(CommonConfig.SATURATED_THRESHOLD.get(), scale);
        if (pressure >= fracture) {
            return PressureBand.FRACTURE;
        }
        if (pressure >= overloaded) {
            return PressureBand.OVERLOADED;
        }
        if (pressure >= saturated) {
            return PressureBand.SATURATED;
        }
        return PressureBand.CALM;
    }

    /**
     * Memory was just changed by the caller. Always marks the chunk unsaved: a change can leave the clamped score
     * where it was (a chunk at the soft cap, a fifth copy of a tag), and the attachment is not saved on its own.
     */
    public static PressureBand recompute(LevelChunk chunk, ChunkMemory memory) {
        return recompute(chunk, memory, true, true);
    }

    /**
     * The {@code ChunkEvent.Load} pass. Marks the chunk unsaved only when {@code mutated} (a quiet imprint faded) or
     * the score or fracture flag moved. No spawn, sound, or particles run inside the load event; a chunk that
     * newly fractures here queues its replicant attempt for the end of the server tick.
     */
    public static PressureBand recomputeOnLoad(LevelChunk chunk, ChunkMemory memory, boolean mutated) {
        return recompute(chunk, memory, mutated, false);
    }

    private static PressureBand recompute(LevelChunk chunk, ChunkMemory memory, boolean mutated, boolean effects) {
        int previous = memory.cachedPressure();
        boolean wasFractured = memory.fractured();
        PressureBand previousBand = band(previous);
        int next = score(memory);
        memory.setCachedPressure(next);
        PressureBand nextBand = band(next);
        if (nextBand == PressureBand.FRACTURE && !wasFractured) {
            memory.setFractured(true);
            Mnemolith.LOGGER.info(
                    "Mnemolith fracture at chunk {} {} pressure={}",
                    chunk.getPos().x(),
                    chunk.getPos().z(),
                    next);
            if (chunk.getLevel() instanceof ServerLevel server) {
                if (effects) {
                    spawnReplicant(server, chunk.getPos());
                } else {
                    DEFERRED_SPAWNS.add(new DeferredSpawn(server.dimension(), chunk.getPos()));
                }
            }
        } else if (ServerConfig.LOG_PRESSURE_CHANGES.get() && previousBand != nextBand) {
            Mnemolith.LOGGER.info(
                    "Mnemolith pressure chunk {} {} {} -> {} ({})",
                    chunk.getPos().x(),
                    chunk.getPos().z(),
                    previous,
                    next,
                    nextBand);
        }
        if (effects
                && chunk.getLevel() instanceof ServerLevel server
                && previousBand.ordinal() < nextBand.ordinal()
                && nextBand.ordinal() >= PressureBand.OVERLOADED.ordinal()) {
            int x = chunk.getPos().getMiddleBlockX();
            int z = chunk.getPos().getMiddleBlockZ();
            int y = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            MemoryFx.pressure(server, new BlockPos(x, y, z));
        }
        boolean scoreMoved = next != previous || memory.fractured() != wasFractured;
        if (mutated || scoreMoved) {
            chunk.markUnsaved();
        }
        if (scoreMoved) {
            PressureSync.markDirty();
        }
        return nextBand;
    }

    private static void spawnReplicant(ServerLevel server, ChunkPos pos) {
        int x = pos.getMiddleBlockX();
        int z = pos.getMiddleBlockZ();
        int y = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        MobSpawns.trySpawnReplicant(server, new BlockPos(x, y, z));
    }

    /** Runs replicant attempts queued by chunk loads. Server thread, end of the server tick. */
    public static void runDeferred(MinecraftServer server) {
        DeferredSpawn pending;
        while ((pending = DEFERRED_SPAWNS.poll()) != null) {
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null || !level.getChunkSource().hasChunk(pending.chunk().x(), pending.chunk().z())) {
                continue;
            }
            spawnReplicant(level, pending.chunk());
        }
    }

    /** Drops queued attempts (server stopped). */
    public static void clearDeferred() {
        DEFERRED_SPAWNS.clear();
    }

    private record DeferredSpawn(ResourceKey<Level> dimension, ChunkPos chunk) {}

    private static int scale(int threshold, double multiplier) {
        return Math.max(1, (int) Math.round(threshold * multiplier));
    }
}
