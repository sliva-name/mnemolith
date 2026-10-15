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

    /** Whether a lantern stands near {@code pos}. Scans loaded blocks only; used on rare events (forgetting, flickers). */
    public static boolean guarded(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int x = pos.getX() + dx;
                int z = pos.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                for (int dy = -HEIGHT; dy <= HEIGHT; dy++) {
                    at.set(x, pos.getY() + dy, z);
                    if (level.getBlockState(at).is(ModBlocks.RECOLLITE_LANTERN.get())) {
                        return true;
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
