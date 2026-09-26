package com.mnemolith.client.render;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.client.particle.ClientParticles;
import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.content.item.ChronicleLensItem;
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

/**
 * Lens polling and the latest pressure snapshot. Loaded only from the client entrypoint.
 * Fracture feel reuses this snapshot. It does not scan chunks.
 * <p>
 * A {@link PressureSnapshotPayload.Scope#BANDS} snapshot (no lens, server ambient rule off) only feeds fracture feel:
 * {@link #origin} returns null for it, so the pill has no reading, and shimmer and the chime skip it.
 */
public final class PressureClient {
    private static List<ChunkPressure> snapshot = List.of();
    private static PressureSnapshotPayload.Scope scope = PressureSnapshotPayload.Scope.BANDS;
    private static boolean lastLens;
    private static ResourceKey<Level> snapshotDimension;
    private static int ticksUntilPoll;
    private static int ticksUntilShimmer;
    private static int lastChimeBand = -1;
    private static int polledChunkX = Integer.MIN_VALUE;
    private static int polledChunkZ = Integer.MIN_VALUE;

    private PressureClient() {}

    public static void accept(PressureSnapshotPayload payload) {
        snapshot = List.copyOf(payload.chunks());
        scope = payload.scope();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        snapshotDimension = player == null || player.level() == null ? null : player.level().dimension();
        if (player == null || !full() || !holdsLens(player) || !ClientConfig.IMPRINT_PARTICLES.get()) {
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
            scope = PressureSnapshotPayload.Scope.BANDS;
            snapshotDimension = null;
            polledChunkX = Integer.MIN_VALUE;
            polledChunkZ = Integer.MIN_VALUE;
            FractureFeel.reset();
            return;
        }
        if (snapshotDimension != null && !player.level().dimension().equals(snapshotDimension)) {
            snapshot = List.of();
            scope = PressureSnapshotPayload.Scope.BANDS;
            snapshotDimension = null;
            lastChimeBand = -1;
            polledChunkX = Integer.MIN_VALUE;
            polledChunkZ = Integer.MIN_VALUE;
            FractureFeel.reset();
        }
        boolean lens = holdsLens(player);
        boolean ambient = ClientConfig.AMBIENT_WITHOUT_LENS.get();
        boolean feel = FractureFeel.wantsSnapshot();
        if (!lens) {
            lastChimeBand = -1;
        }
        if (lens != lastLens) {
            // Picking up or putting away the lens asks at once. Until the answer arrives the previous snapshot
            // stays, so fracture feel keeps its bands and does not blink.
            lastLens = lens;
            ticksUntilPoll = 0;
        }
        if ((lens || ambient) && full()) {
            shimmerCached(player);
        }
        if (!lens && !ambient && !feel) {
            ticksUntilPoll = 0;
            return;
        }
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        if (chunkX != polledChunkX || chunkZ != polledChunkZ) {
            ticksUntilPoll = 0;
            polledChunkX = chunkX;
            polledChunkZ = chunkZ;
        }
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
        return ChronicleLensItem.isHeld(player);
    }

    /** The lens reading for the chunk under the player, or null when there is none or the snapshot is band-only. */
    public static ChunkPressure origin(LocalPlayer player) {
        if (!full()) {
            return null;
        }
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        for (ChunkPressure chunk : snapshot) {
            if (chunk.chunkX() == chunkX && chunk.chunkZ() == chunkZ) {
                return chunk;
            }
        }
        return null;
    }

    /** True when the latest snapshot is a full reading rather than the band-only fracture-feel read. */
    public static boolean full() {
        return scope == PressureSnapshotPayload.Scope.FULL;
    }

    /** Every chunk in the latest snapshot, full or band-only. Fracture feel reads this. */
    public static List<ChunkPressure> snapshot() {
        return new ArrayList<>(snapshot);
    }
}
