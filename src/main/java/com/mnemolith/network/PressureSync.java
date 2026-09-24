package com.mnemolith.network;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.network.chat.Component;
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
        List<ChunkPressure> chunks = new ArrayList<>();
        int here = 0;
        PressureBand hereBand = PressureBand.CALM;
        for (int dx = -ImprintConstants.LENS_CHUNK_RADIUS; dx <= ImprintConstants.LENS_CHUNK_RADIUS; dx++) {
            for (int dz = -ImprintConstants.LENS_CHUNK_RADIUS; dz <= ImprintConstants.LENS_CHUNK_RADIUS; dz++) {
                int chunkX = origin.x() + dx;
                int chunkZ = origin.z() + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                int pressure = memory == null ? 0 : memory.cachedPressure();
                PressureBand band = MemoryPressure.band(pressure);
                if (dx == 0 && dz == 0) {
                    here = pressure;
                    hereBand = band;
                }
                if (chunks.size() < ImprintConstants.LENS_CHUNK_LIMIT) {
                    chunks.add(new ChunkPressure(chunkX, chunkZ, pressure, band.ordinal()));
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new PressureSnapshotPayload(List.copyOf(chunks)));
        player.sendOverlayMessage(Component.translatable("mnemolith.message.pressure", Component.translatable(hereBand.translationKey()), here));
    }

    public static boolean holdsLens(ServerPlayer player) {
        return player.getMainHandItem().getItem() == ModItems.CHRONICLE_LENS.get()
                || player.getOffhandItem().getItem() == ModItems.CHRONICLE_LENS.get();
    }
}
