package com.mnemolith.pressure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.config.ServerConfig;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Pressure is the clamped sum of imprint contributions plus instability. Recomputed when memory changes or a chunk loads. */
public final class MemoryPressure {
    /** Copies of one tag, after the strongest, that still add pressure. */
    private static final int DIMINISHED_COPIES = 3;

    private MemoryPressure() {}

    public static int score(ChunkMemory memory) {
        int sum = memory.instability();
        EnumMap<ImprintTag, List<Integer>> byTag = new EnumMap<>(ImprintTag.class);
        for (Imprint imprint : memory.imprintsCopy()) {
            byTag.computeIfAbsent(imprint.tag(), ignored -> new ArrayList<>()).add(imprint.pressureContribution());
        }
        for (List<Integer> contributions : byTag.values()) {
            contributions.sort(Comparator.reverseOrder());
            int counted = Math.min(contributions.size(), 1 + DIMINISHED_COPIES);
            for (int index = 0; index < counted; index++) {
                int contribution = contributions.get(index);
                if (index == 0) {
                    sum += contribution;
                } else {
                    sum += Math.max(1, contribution / 4);
                }
            }
        }
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

    public static PressureBand recompute(LevelChunk chunk, ChunkMemory memory) {
        int previous = memory.cachedPressure();
        PressureBand previousBand = band(previous);
        int next = score(memory);
        memory.setCachedPressure(next);
        PressureBand nextBand = band(next);
        if (nextBand == PressureBand.FRACTURE && !memory.fractured()) {
            memory.setFractured(true);
            Mnemolith.LOGGER.info(
                    "Mnemolith fracture at chunk {} {} pressure={}",
                    chunk.getPos().x(),
                    chunk.getPos().z(),
                    next);
            if (chunk.getLevel() instanceof ServerLevel server) {
                int x = chunk.getPos().getMiddleBlockX();
                int z = chunk.getPos().getMiddleBlockZ();
                int y = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                MobSpawns.trySpawnReplicant(server, new BlockPos(x, y, z));
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
        if (chunk.getLevel() instanceof ServerLevel server
                && previousBand.ordinal() < nextBand.ordinal()
                && nextBand.ordinal() >= PressureBand.OVERLOADED.ordinal()) {
            int x = chunk.getPos().getMiddleBlockX();
            int z = chunk.getPos().getMiddleBlockZ();
            int y = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            MemoryFx.pressure(server, new BlockPos(x, y, z));
        }
        chunk.markUnsaved();
        return nextBand;
    }

    private static int scale(int threshold, double multiplier) {
        return Math.max(1, (int) Math.round(threshold * multiplier));
    }
}
