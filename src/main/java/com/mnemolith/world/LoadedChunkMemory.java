package com.mnemolith.world;

import java.util.function.Predicate;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.PressureSync;
import com.mnemolith.particle.MemoryFx;

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
            PressureSync.markDirty();
        }
    }

    /**
     * True if any already-loaded chunk with memory in the Chebyshev {@code radius} around {@code pos} passes
     * {@code test}. Never loads chunks; walks x-major, z-minor and stops at the first match.
     */
    public static boolean anyLoaded(ServerLevel level, BlockPos pos, int radius, Predicate<ChunkMemory> test) {
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
                if (memory != null && test.test(memory)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isMuted(ServerLevel level, BlockPos pos) {
        return anyLoaded(level, pos, CommonConfig.MUTE_RADIUS_CHUNKS.get(), ChunkMemory::hasMuteStone);
    }

    public static void addMuteStone(ServerLevel level, BlockPos pos) {
        if (addMuteStone(level.getChunkAt(pos), pos)) {
            MemoryFx.mute(level, pos);
        }
    }

    public static boolean addMuteStone(ChunkAccess chunk, BlockPos pos) {
        ChunkMemory memory = getOrCreate(chunk);
        if (memory.addMuteStone(pos)) {
            chunk.markUnsaved();
            PressureSync.markDirty();
            return true;
        }
        return false;
    }

    public static void removeMuteStone(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = existing(chunk);
        if (memory != null && memory.removeMuteStone(pos)) {
            chunk.markUnsaved();
            PressureSync.markDirty();
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
        return anyLoaded(level, pos, 1, memory -> {
            for (BlockPos resonator : memory.resonatorsCopy()) {
                if (resonator.distSqr(pos) <= rangeSqr) {
                    return true;
                }
            }
            return false;
        });
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
        return anyLoaded(level, pos, radius, ChunkMemory::observatory);
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
