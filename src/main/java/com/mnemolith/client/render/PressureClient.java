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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Lens polling and the latest pressure snapshot. Loaded only from the client entrypoint. */
public final class PressureClient {
    private static List<ChunkPressure> snapshot = List.of();
    private static ResourceKey<Level> snapshotDimension;
    private static int ticksUntilPoll;
    private static int ticksUntilShimmer;
    private static int lastChimeBand = -1;

    private PressureClient() {}

    public static void accept(PressureSnapshotPayload payload) {
        snapshot = List.copyOf(payload.chunks());
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        snapshotDimension = player == null || player.level() == null ? null : player.level().dimension();
        if (player == null || !ClientConfig.IMPRINT_PARTICLES.get()) {
            return;
        }
        float volume = ClientConfig.MEMORY_AUDIO_VOLUME.get().floatValue();
        int loudest = PressureBand.CALM.ordinal();
        for (ChunkPressure chunk : snapshot) {
            loudest = Math.max(loudest, PressureBand.byOrdinal(chunk.band()).ordinal());
        }
        if (loudest != lastChimeBand && loudest >= PressureBand.SATURATED.ordinal() && volume > 0.0F) {
            player.playSound(ModSounds.LENS_FOCUS.get(), volume * 0.35F, 1.4F);
        }
        lastChimeBand = loudest;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            snapshot = List.of();
            snapshotDimension = null;
            return;
        }
        if (snapshotDimension != null && !player.level().dimension().equals(snapshotDimension)) {
            snapshot = List.of();
            snapshotDimension = null;
            lastChimeBand = -1;
        }
        boolean lens = holdsLens(player);
        boolean ambient = ClientConfig.AMBIENT_WITHOUT_LENS.get();
        if (!lens && !ambient) {
            snapshot = List.of();
            snapshotDimension = null;
            lastChimeBand = -1;
            ticksUntilPoll = 0;
            return;
        }
        shimmerCached(player);
        if (ticksUntilPoll > 0) {
            ticksUntilPoll--;
            return;
        }
        ticksUntilPoll = ClientConfig.LENS_POLL_INTERVAL.get();
        ClientPacketDistributor.sendToServer(new RequestPressurePayload(!lens));
    }

    /** Saturated motes come from the cached snapshot, so a skipped server packet does not stop them. */
    private static void shimmerCached(LocalPlayer player) {
        if (ticksUntilShimmer > 0) {
            ticksUntilShimmer--;
            return;
        }
        ticksUntilShimmer = ClientConfig.LENS_POLL_INTERVAL.get();
        ChunkPos origin = player.chunkPosition();
        int shown = 0;
        for (ChunkPressure chunk : snapshot) {
            if (PressureBand.byOrdinal(chunk.band()).ordinal() < PressureBand.SATURATED.ordinal() || shown >= 4) {
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
