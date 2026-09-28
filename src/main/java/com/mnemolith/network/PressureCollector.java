package com.mnemolith.network;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.content.item.ChronicleLensItem;
import net.minecraft.world.entity.player.Player;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.ChunkState;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** One lens-sized walk of loaded chunks around a position. The list is capped at {@link ImprintConstants#LENS_CHUNK_LIMIT}. */
final class PressureCollector {
    /**
     * Chebyshev radius of a band-only snapshot. Fixed at the base lens radius so an archival stratum under the
     * player does not widen it (that would leak the stratum to a player without a lens).
     */
    static final int BAND_RADIUS = ImprintConstants.LENS_CHUNK_RADIUS;

    private PressureCollector() {}

    /**
     * Band-only walk for fracture feel. Keeps only overloaded and fracture chunks. Pressure is sent as 0 and state
     * as {@code NORMAL}, so the result carries no number, mute, archival, or saturated information. No mute scan.
     */
    static List<ChunkPressure> collectBands(ServerLevel level, BlockPos playerPos) {
        ChunkPos origin = ChunkPos.containing(playerPos);
        List<ChunkPressure> chunks = new ArrayList<>();
        for (int dx = -BAND_RADIUS; dx <= BAND_RADIUS; dx++) {
            for (int dz = -BAND_RADIUS; dz <= BAND_RADIUS; dz++) {
                int chunkX = origin.x() + dx;
                int chunkZ = origin.z() + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkMemory memory = LoadedChunkMemory.existing(level.getChunk(chunkX, chunkZ));
                if (memory == null) {
                    continue;
                }
                PressureBand band = MemoryPressure.band(memory.cachedPressure());
                if (band.ordinal() < PressureBand.OVERLOADED.ordinal()) {
                    continue;
                }
                chunks.add(new ChunkPressure(chunkX, chunkZ, 0, band.ordinal(), ChunkState.NORMAL.ordinal()));
            }
        }
        return chunks;
    }

    static List<ChunkPressure> collect(ServerLevel level, BlockPos playerPos) {
        return collect(level, playerPos, null);
    }

    static List<ChunkPressure> collect(ServerLevel level, BlockPos playerPos, @org.jspecify.annotations.Nullable Player player) {
        ChunkPos origin = ChunkPos.containing(playerPos);
        ChunkMemory originMemory = LoadedChunkMemory.existing(level.getChunk(origin.x(), origin.z()));
        int radius = ImprintConstants.LENS_CHUNK_RADIUS;
        if (originMemory != null && originMemory.strataCount() > 0) {
            radius += 1;
        }
        int muteRadius = CommonConfig.MUTE_RADIUS_CHUNKS.get();
        ImprintTag filter = player == null ? null : ChronicleLensItem.heldFilter(player).orElse(null);
        List<ChunkPressure> chunks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = origin.x() + dx;
                int chunkZ = origin.z() + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                if (chunks.size() >= ImprintConstants.LENS_CHUNK_LIMIT) {
                    break;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                if (filter != null && !matchesFilter(memory, filter)) {
                    continue;
                }
                int pressure = memory == null ? 0 : memory.cachedPressure();
                PressureBand band = MemoryPressure.band(pressure);
                BlockPos sample = new BlockPos((chunkX << 4) + 8, playerPos.getY(), (chunkZ << 4) + 8);
                boolean muted = memory != null && memory.hasAnyMute();
                if (!muted && muteRadius > 0) {
                    muted = LoadedChunkMemory.isMuted(level, sample);
                }
                chunks.add(new ChunkPressure(chunkX, chunkZ, pressure, band.ordinal(), LoadedChunkMemory.stateOf(memory, muted).ordinal()));
            }
        }
        return chunks;
    }

    /** True when the chunk has any imprint of {@code filter}, or its loudest imprint matches. */
    private static boolean matchesFilter(@org.jspecify.annotations.Nullable ChunkMemory memory, ImprintTag filter) {
        if (memory == null || memory.imprintCount() == 0) {
            return false;
        }
        for (int i = 0; i < memory.imprintCount(); i++) {
            if (memory.imprintAt(i).tag() == filter) {
                return true;
            }
        }
        return false;
    }
}
