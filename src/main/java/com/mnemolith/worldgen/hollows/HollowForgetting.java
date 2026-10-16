package com.mnemolith.worldgen.hollows;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Memory Hollows, stage 4: the forgotten imprint. A hollow is a place that remembered too often; there memories also
 * wear out. When a flicker plays out uncaught, or a faded dies, the chunk may let that memory go and keep only a
 * {@link ImprintTag#FORGOTTEN forgotten} imprint where it was. Nothing writes the tag outside Memory Hollows
 * ({@link ImprintWriter#write} refuses it there).
 */
public final class HollowForgetting {
    /** Chance an uncaught flicker that replayed an imprint is forgotten when its catch window closes. */
    public static final double FORGET_CHANCE = 0.25D;

    private static long forgotten;

    private HollowForgetting() {}

    /**
     * In Memory Hollows only: the strongest imprint of {@code replaced} (if any, and if not itself forgotten) leaves
     * the chunk at {@code pos}, and a forgotten imprint is written there. Returns whether the forgotten imprint was
     * written (false outside the biome, in a muted chunk, or with imprints off).
     */
    public static boolean forget(ServerLevel level, BlockPos pos, @Nullable ImprintTag replaced) {
        if (!Hollows.is(level.getBiome(pos))) {
            return false;
        }
        if (HollowLanterns.guarded(level, pos) || com.mnemolith.content.item.RememberingCirclet.wornNear(level, pos)) {
            // a recollite lantern (or someone wearing a remembering circlet) keeps the memory: nothing leaves, nothing is written
            HollowLanterns.noteKept();
            com.mnemolith.particle.MemoryFeel.cueThrottled(level, com.mnemolith.particle.MemoryFeel.Kind.KEPT, pos, 3, 60);
            return false;
        }
        if (replaced != null && replaced != ImprintTag.FORGOTTEN) {
            LevelChunk chunk = level.getChunkAt(pos);
            ChunkMemory memory = LoadedChunkMemory.existing(chunk);
            if (memory != null && memory.removeStrongest(replaced).isPresent()) {
                MemoryPressure.recompute(chunk, memory);
            }
        }
        boolean written = ImprintWriter.write(level, pos, List.of(ImprintTag.FORGOTTEN), null, false);
        if (written) {
            forgotten++;
            com.mnemolith.particle.MemoryFeel.cueThrottled(level, com.mnemolith.particle.MemoryFeel.Kind.FORGOTTEN, pos, 4, 20);
            Mnemolith.LOGGER.debug("Mnemolith hollow forgot {} at {}", replaced, pos.toShortString());
        }
        return written;
    }

    /** Forgotten imprints written since the server started (QA). */
    public static long forgottenCount() {
        return forgotten;
    }
}
