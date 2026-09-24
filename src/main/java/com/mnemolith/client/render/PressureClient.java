package com.mnemolith.client.render;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.client.particle.ClientParticles;
import com.mnemolith.config.ClientConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.network.ChunkPressure;
import com.mnemolith.network.PressureSnapshotPayload;
import com.mnemolith.network.RequestPressurePayload;
import com.mnemolith.pressure.PressureBand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Lens polling and the latest pressure snapshot. Loaded only from the client entrypoint. */
public final class PressureClient {
    private static List<ChunkPressure> snapshot = List.of();
    private static int ticksUntilPoll;
    private static int lastChimeBand = -1;

    private PressureClient() {}

    public static void accept(PressureSnapshotPayload payload) {
        snapshot = List.copyOf(payload.chunks());
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !ClientConfig.IMPRINT_PARTICLES.get()) {
            return;
        }
        float volume = ClientConfig.MEMORY_AUDIO_VOLUME.get().floatValue();
        int loudest = PressureBand.CALM.ordinal();
        ChunkPos origin = player.chunkPosition();
        int shown = 0;
        for (ChunkPressure chunk : snapshot) {
            int band = PressureBand.byOrdinal(chunk.band()).ordinal();
            loudest = Math.max(loudest, band);
            if (band < PressureBand.SATURATED.ordinal() || shown >= 4) {
                continue;
            }
            int dx = Math.abs(chunk.chunkX() - origin.x());
            int dz = Math.abs(chunk.chunkZ() - origin.z());
            if (dx > 1 || dz > 1) {
                continue;
            }
            shown++;
            ClientParticles.shimmer(player, new ChunkPos(chunk.chunkX(), chunk.chunkZ()));
        }
        if (loudest != lastChimeBand && loudest >= PressureBand.SATURATED.ordinal() && volume > 0.0F) {
            player.playSound(ModSounds.EXTRACT.get(), volume * 0.35F, 1.4F);
        }
        lastChimeBand = loudest;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            snapshot = List.of();
            return;
        }
        boolean lens = holdsLens(player);
        boolean ambient = ClientConfig.AMBIENT_WITHOUT_LENS.get();
        if (!lens && !ambient) {
            snapshot = List.of();
            lastChimeBand = -1;
            ticksUntilPoll = 0;
            return;
        }
        if (ticksUntilPoll > 0) {
            ticksUntilPoll--;
            return;
        }
        ticksUntilPoll = ClientConfig.LENS_POLL_INTERVAL.get();
        ClientPacketDistributor.sendToServer(new RequestPressurePayload(!lens));
    }

    public static boolean holdsLens(LocalPlayer player) {
        return player.getMainHandItem().getItem() == ModItems.CHRONICLE_LENS.get()
                || player.getOffhandItem().getItem() == ModItems.CHRONICLE_LENS.get();
    }

    public static ChunkPressure origin(LocalPlayer player) {
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        for (ChunkPressure chunk : snapshot) {
            if (chunk.chunkX() == chunkX && chunk.chunkZ() == chunkZ) {
                return chunk;
            }
        }
        return null;
    }

    public static List<ChunkPressure> snapshot() {
        return new ArrayList<>(snapshot);
    }
}
