package com.mnemolith.world;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Reads and updates chunk memory that is already loaded.
 * Mute checks walk a config-bounded Chebyshev radius of loaded chunks only.
 */
public final class LoadedChunkMemory {
    private LoadedChunkMemory() {}

    public static ChunkMemory getOrCreate(LevelChunk chunk) {
        ChunkMemory existing = chunk.getExistingDataOrNull(ModAttachments.CHUNK_MEMORY.get());
        if (existing != null) {
            return existing;
        }
        ChunkMemory created = new ChunkMemory();
        chunk.setData(ModAttachments.CHUNK_MEMORY.get(), created);
        return created;
    }

    public static ChunkMemory existing(LevelChunk chunk) {
        return chunk.getExistingDataOrNull(ModAttachments.CHUNK_MEMORY.get());
    }

    public static void clear(LevelChunk chunk) {
        if (chunk.hasData(ModAttachments.CHUNK_MEMORY.get())) {
            chunk.removeData(ModAttachments.CHUNK_MEMORY.get());
            chunk.markUnsaved();
        }
    }

    public static boolean isMuted(ServerLevel level, BlockPos pos) {
        int radius = CommonConfig.MUTE_RADIUS_CHUNKS.get();
        int originX = pos.getX() >> 4;
        int originZ = pos.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = originX + dx;
                int chunkZ = originZ + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                ChunkMemory memory = existing(chunk);
                if (memory != null && memory.hasMuteStone()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void addMuteStone(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = getOrCreate(chunk);
        if (memory.addMuteStone(pos)) {
            chunk.markUnsaved();
        }
    }

    public static void removeMuteStone(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = existing(chunk);
        if (memory != null && memory.removeMuteStone(pos)) {
            chunk.markUnsaved();
        }
    }

    public static ChunkState stateOf(ChunkMemory memory, boolean muted) {
        if (memory != null && memory.fractured()) {
            return ChunkState.FRACTURED;
        }
        if (muted) {
            return ChunkState.MUTED;
        }
        if (memory != null && memory.archival()) {
            return ChunkState.ARCHIVAL;
        }
        return ChunkState.NORMAL;
    }
}
