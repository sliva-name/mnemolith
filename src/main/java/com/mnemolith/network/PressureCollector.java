package com.mnemolith.network;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** One lens-sized walk of loaded chunks around a position. The list is capped at {@link ImprintConstants#LENS_CHUNK_LIMIT}. */
final class PressureCollector {
    private PressureCollector() {}

    static List<ChunkPressure> collect(ServerLevel level, BlockPos playerPos) {
        ChunkPos origin = ChunkPos.containing(playerPos);
        ChunkMemory originMemory = LoadedChunkMemory.existing(level.getChunk(origin.x(), origin.z()));
        int radius = ImprintConstants.LENS_CHUNK_RADIUS;
        if (originMemory != null && originMemory.strataCount() > 0) {
            radius += 1;
        }
        int muteRadius = CommonConfig.MUTE_RADIUS_CHUNKS.get();
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
                int pressure = memory == null ? 0 : memory.cachedPressure();
                PressureBand band = MemoryPressure.band(pressure);
                BlockPos sample = new BlockPos((chunkX << 4) + 8, playerPos.getY(), (chunkZ << 4) + 8);
                boolean muted = memory != null && memory.hasMuteStone();
                if (!muted && muteRadius > 0) {
                    muted = LoadedChunkMemory.isMuted(level, sample);
                }
                chunks.add(new ChunkPressure(chunkX, chunkZ, pressure, band.ordinal(), LoadedChunkMemory.stateOf(memory, muted).ordinal()));
            }
        }
        return chunks;
    }
}
