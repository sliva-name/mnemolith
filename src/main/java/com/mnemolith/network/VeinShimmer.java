package com.mnemolith.network;

import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.WorldgenTuning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Up to eight stratum hints in the 3×3 around the lens holder. Sent only to that player. */
final class VeinShimmer {
    private VeinShimmer() {}

    static void send(ServerLevel level, ServerPlayer player, ChunkPos origin) {
        BlockPos playerPos = player.blockPosition();
        int hinted = 0;
        int cap = WorldgenTuning.LENS_VEIN_HINTS;
        long rangeSqr = (long) WorldgenTuning.LENS_VEIN_RANGE * WorldgenTuning.LENS_VEIN_RANGE;
        for (int dx = -1; dx <= 1 && hinted < cap; dx++) {
            for (int dz = -1; dz <= 1 && hinted < cap; dz++) {
                int chunkX = origin.x() + dx;
                int chunkZ = origin.z() + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkMemory memory = LoadedChunkMemory.existing(level.getChunk(chunkX, chunkZ));
                if (memory == null) {
                    continue;
                }
                for (BlockPos mark : memory.strataCopy()) {
                    if (hinted >= cap || mark.distSqr(playerPos) > rangeSqr) {
                        continue;
                    }
                    level.sendParticles(
                            player,
                            ModParticles.IMPRINT_SHIMMER.get(),
                            false,
                            false,
                            mark.getX() + 0.5D,
                            mark.getY() + 1.1D,
                            mark.getZ() + 0.5D,
                            2,
                            0.15D,
                            0.2D,
                            0.15D,
                            0.01D);
                    hinted++;
                }
            }
        }
    }
}
