package com.mnemolith.world;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Reads and updates chunk memory that is already loaded.
 * Mute checks walk a config-bounded Chebyshev radius of loaded chunks only.
 */
public final class LoadedChunkMemory {
    private LoadedChunkMemory() {}

    public static ChunkMemory getOrCreate(ChunkAccess chunk) {
        ChunkMemory existing = chunk.getExistingDataOrNull(ModAttachments.CHUNK_MEMORY.get());
        if (existing != null) {
            return existing;
        }
        ChunkMemory created = new ChunkMemory();
        chunk.setData(ModAttachments.CHUNK_MEMORY.get(), created);
        return created;
    }

    public static ChunkMemory existing(ChunkAccess chunk) {
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
        addMuteStone(level.getChunkAt(pos), pos);
    }

    public static void addMuteStone(ChunkAccess chunk, BlockPos pos) {
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

    public static void addResonator(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = getOrCreate(chunk);
        if (memory.addResonator(pos)) {
            chunk.markUnsaved();
        }
    }

    public static void removeResonator(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = existing(chunk);
        if (memory != null && memory.removeResonator(pos)) {
            chunk.markUnsaved();
        }
    }

    /** Loaded chunks in a one-chunk ring. Range is a few blocks, so the ring is enough. */
    public static boolean resonatorNearby(ServerLevel level, BlockPos pos, double range) {
        double rangeSqr = range * range;
        int originX = pos.getX() >> 4;
        int originZ = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int chunkX = originX + dx;
                int chunkZ = originZ + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkMemory memory = existing(level.getChunk(chunkX, chunkZ));
                if (memory == null) {
                    continue;
                }
                for (BlockPos resonator : memory.resonatorsCopy()) {
                    if (resonator.distSqr(pos) <= rangeSqr) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean noteStratum(ChunkAccess chunk, BlockPos pos) {
        ChunkMemory memory = getOrCreate(chunk);
        if (!memory.noteStratum(pos)) {
            return false;
        }
        chunk.markUnsaved();
        return true;
    }

    public static boolean forgetStratum(ChunkAccess chunk, BlockPos pos) {
        ChunkMemory memory = existing(chunk);
        if (memory == null || !memory.forgetStratum(pos)) {
            return false;
        }
        chunk.markUnsaved();
        return true;
    }

    public static boolean markObservatory(ChunkAccess chunk) {
        ChunkMemory memory = getOrCreate(chunk);
        if (memory.observatory()) {
            return false;
        }
        memory.setObservatory(true);
        chunk.markUnsaved();
        return true;
    }

    public static boolean observatoryNearby(ServerLevel level, BlockPos pos, int radius) {
        int originX = pos.getX() >> 4;
        int originZ = pos.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = originX + dx;
                int chunkZ = originZ + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkMemory memory = existing(level.getChunk(chunkX, chunkZ));
                if (memory != null && memory.observatory()) {
                    return true;
                }
            }
        }
        return false;
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
