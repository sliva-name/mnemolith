package com.mnemolith.network;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.ChunkState;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.WorldgenTuning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server answers a lens request with the chunks around that player. */
public final class PressureSync {
    private PressureSync() {}

    public static void handleRequest(RequestPressurePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!holdsLens(player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos origin = new ChunkPos(player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
        ChunkMemory originMemory = LoadedChunkMemory.existing(level.getChunk(origin.x(), origin.z()));
        int radius = ImprintConstants.LENS_CHUNK_RADIUS;
        if (originMemory != null && originMemory.strataCount() > 0) {
            radius += 1;
        }
        List<ChunkPressure> chunks = new ArrayList<>();
        int hinted = 0;
        BlockPos playerPos = player.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = origin.x() + dx;
                int chunkZ = origin.z() + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                int pressure = memory == null ? 0 : memory.cachedPressure();
                PressureBand band = MemoryPressure.band(pressure);
                if (chunks.size() < ImprintConstants.LENS_CHUNK_LIMIT) {
                    BlockPos sample = new BlockPos((chunkX << 4) + 8, playerPos.getY(), (chunkZ << 4) + 8);
                    boolean muted = LoadedChunkMemory.isMuted(level, sample);
                    ChunkState state = LoadedChunkMemory.stateOf(memory, muted);
                    chunks.add(new ChunkPressure(chunkX, chunkZ, pressure, band.ordinal(), state.ordinal()));
                }
                if (memory != null && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    for (BlockPos mark : memory.strataCopy()) {
                        if (hinted >= 8 || mark.distSqr(playerPos) > (long) WorldgenTuning.LENS_VEIN_RANGE * WorldgenTuning.LENS_VEIN_RANGE) {
                            continue;
                        }
                        level.sendParticles(ParticleTypes.END_ROD, mark.getX() + 0.5D, mark.getY() + 1.1D, mark.getZ() + 0.5D, 2, 0.15D, 0.2D, 0.15D, 0.01D);
                        hinted++;
                    }
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new PressureSnapshotPayload(List.copyOf(chunks)));
    }

    public static boolean holdsLens(ServerPlayer player) {
        return player.getMainHandItem().getItem() == ModItems.CHRONICLE_LENS.get()
                || player.getOffhandItem().getItem() == ModItems.CHRONICLE_LENS.get();
    }
}
