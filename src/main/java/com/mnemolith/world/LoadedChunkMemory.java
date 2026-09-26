package com.mnemolith.world;

import java.util.function.Predicate;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.PressureSync;
import com.mnemolith.particle.MemoryFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

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
        if (memory == null) {
            return;
        }
        boolean wasFull = ChunkMemory.markListFull(memory.muteStoneCount());
        if (memory.removeMuteStone(pos)) {
            if (wasFull) {
                refillMarks(chunk, ModBlocks.MUTE_STONE.get(), memory::addMuteStone);
            }
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
        if (memory == null) {
            return;
        }
        boolean wasFull = ChunkMemory.markListFull(memory.resonatorCount());
        if (memory.removeResonator(pos)) {
            if (wasFull) {
                refillMarks(chunk, ModBlocks.RESONATOR_TRAP.get(), memory::addResonator);
            }
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
        if (memory == null) {
            return false;
        }
        boolean wasFull = ChunkMemory.markListFull(memory.strataCount());
        if (!memory.forgetStratum(pos)) {
            return false;
        }
        if (wasFull) {
            refillMarks(chunk, ModBlocks.ARCHIVAL_STRATUM.get(), memory::noteStratum);
        }
        chunk.markUnsaved();
        return true;
    }

    /**
     * A full mark list just lost an entry, so a block beyond the cap may be untracked. Re-adds such blocks until the
     * list is full again. Only sections whose palette may hold the block are walked, and only in this rare case.
     */
    private static void refillMarks(ChunkAccess chunk, Block block, Predicate<BlockPos> add) {
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(block)) {
                            add.test(new BlockPos(baseX + x, baseY + y, baseZ + z));
                        }
                    }
                }
            }
        }
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
