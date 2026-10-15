package com.mnemolith.worldgen.hollows;

import com.mnemolith.content.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The recollite lantern: a light for the places a hollow is about to let go of. Within {@link #RADIUS} blocks
 * (and {@link #HEIGHT} up or down) of a lantern, nothing is forgotten (an uncaught flicker or a dying faded keeps its
 * memory in the chunk) and a flicker stays catchable twice as long.
 */
public final class HollowLanterns {
    public static final int RADIUS = 8;
    public static final int HEIGHT = 4;

    private static long kept;

    private HollowLanterns() {}

    /**
     * Whether a lantern stands near {@code pos} (within {@link #RADIUS} blocks across and {@link #HEIGHT} up or down).
     * Loaded chunks only. A chunk section whose palette cannot hold a lantern is skipped without reading its blocks,
     * so the usual answer (no lantern anywhere near) costs a few palette checks instead of 2601 block reads.
     */
    public static boolean guarded(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.Block lantern = ModBlocks.RECOLLITE_LANTERN.get();
        int minX = pos.getX() - RADIUS;
        int maxX = pos.getX() + RADIUS;
        int minZ = pos.getZ() - RADIUS;
        int maxZ = pos.getZ() + RADIUS;
        int minY = Math.max(level.getMinY(), pos.getY() - HEIGHT);
        int maxY = Math.min(level.getMaxY(), pos.getY() + HEIGHT);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    int index = level.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSections().length) {
                        continue;
                    }
                    net.minecraft.world.level.chunk.LevelChunkSection section = chunk.getSections()[index];
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(lantern))) {
                        continue;
                    }
                    int x0 = Math.max(minX, cx << 4);
                    int x1 = Math.min(maxX, (cx << 4) + 15);
                    int z0 = Math.max(minZ, cz << 4);
                    int z1 = Math.min(maxZ, (cz << 4) + 15);
                    int y0 = Math.max(minY, sy << 4);
                    int y1 = Math.min(maxY, (sy << 4) + 15);
                    for (int x = x0; x <= x1; x++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int y = y0; y <= y1; y++) {
                                if (section.getBlockState(x & 15, y & 15, z & 15).is(lantern)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Catch window for a flicker at {@code pos}: doubled beside a lantern. */
    public static int catchWindow(ServerLevel level, BlockPos pos) {
        return guarded(level, pos) ? HollowFlickers.CATCH_WINDOW * 2 : HollowFlickers.CATCH_WINDOW;
    }

    static void noteKept() {
        kept++;
    }

    /** Memories a lantern kept from being forgotten since the server started (QA). */
    public static long keptCount() {
        return kept;
    }
}
