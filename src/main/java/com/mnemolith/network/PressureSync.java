package com.mnemolith.network;

import java.util.List;
import java.util.UUID;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.network.LensPollCache.Stamp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server answers a pressure request with the chunks around that player. A held lens, or
 * {@code gameplay.allowAmbientPressure}, gets the full snapshot; anyone else gets the band-only snapshot
 * fracture feel needs. Unchanged snapshots are not resent.
 */
public final class PressureSync {
    private PressureSync() {}

    /** Any pressure, mute, or fracture change. Lens polls skip their chunk walk until this moves. */
    public static void markDirty() {
        LensPollCache.markDirty();
    }

    public static void forget(UUID player) {
        LensPollCache.forget(player);
    }

    public static void handleRequest(RequestPressurePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.hasDisconnected() || player.isRemoved()) {
            return;
        }
        boolean lens = holdsLens(player);
        // payload.ambient() is not permission. A full snapshot without a lens exists only when the common config allows it.
        boolean ambient = !lens && CommonConfig.ALLOW_AMBIENT_PRESSURE.get();
        if (!lens && !ambient) {
            sendBands(player, level);
            return;
        }
        ChunkPos origin = ChunkPos.containing(player.blockPosition());
        Stamp stamp = LensPollCache.get(player.getUUID());
        if (stamp != null && stamp.matches(LensPollCache.epoch(), level.dimension(), origin.x(), origin.z(), lens, ambient)) {
            int interval = CommonConfig.VEIN_SHIMMER_TICKS.get();
            if (lens && interval > 0 && level.getGameTime() - stamp.shimmer() >= interval) {
                VeinShimmer.send(level, player, origin);
                stamp.setShimmer(level.getGameTime());
            }
            return;
        }
        List<ChunkPressure> chunks = PressureCollector.collect(level, player.blockPosition());
        if (lens) {
            VeinShimmer.send(level, player, origin);
        }
        LensPollCache.put(player.getUUID(), new Stamp(LensPollCache.epoch(), level.dimension(), origin.x(), origin.z(), lens, ambient, level.getGameTime()));
        PacketDistributor.sendToPlayer(player, new PressureSnapshotPayload(PressureSnapshotPayload.Scope.FULL, List.copyOf(chunks)));
    }

    /**
     * No lens and no server ambient rule: the player still gets the band-only read fracture feel needs, whatever the
     * request bit says. No vein shimmer, no pressure numbers, no chunk state, nothing below overloaded. The stamp
     * with lens and ambient both false is this mode; a repeat for the same chunk and memory epoch is not resent.
     */
    private static void sendBands(ServerPlayer player, ServerLevel level) {
        ChunkPos origin = ChunkPos.containing(player.blockPosition());
        Stamp stamp = LensPollCache.get(player.getUUID());
        if (stamp != null && stamp.matches(LensPollCache.epoch(), level.dimension(), origin.x(), origin.z(), false, false)) {
            return;
        }
        List<ChunkPressure> chunks = PressureCollector.collectBands(level, player.blockPosition());
        LensPollCache.put(player.getUUID(), new Stamp(LensPollCache.epoch(), level.dimension(), origin.x(), origin.z(), false, false, level.getGameTime()));
        PacketDistributor.sendToPlayer(player, new PressureSnapshotPayload(PressureSnapshotPayload.Scope.BANDS, List.copyOf(chunks)));
    }

    /**
     * One lens-sized chunk walk, used by {@code /mnemolith perf}. Returns 1 when the walk ran.
     * A repeat with the same memory epoch and chunk returns 0.
     */
    public static int timedPoll(ServerLevel level, BlockPos pos) {
        ChunkPos origin = ChunkPos.containing(pos);
        if (LensPollCache.skipPerf(level, origin)) {
            return 0;
        }
        PressureCollector.collect(level, pos);
        LensPollCache.rememberPerf(level, origin);
        return 1;
    }

    /**
     * True when the last lens-sized walk was for this dimension and chunk and memory has not changed since.
     * A dimension change fails the match, which is the stamp {@code /mnemolith qa} checks.
     */
    public static boolean perfStampMatches(ServerLevel level, BlockPos pos) {
        return LensPollCache.perfStampMatches(level, ChunkPos.containing(pos));
    }

    /** Band ordinal for the chunk containing {@code pos}, using the same walk a lens snapshot uses. */
    public static int originBand(ServerLevel level, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (ChunkPressure chunk : PressureCollector.collect(level, pos)) {
            if (chunk.chunkX() == chunkX && chunk.chunkZ() == chunkZ) {
                return chunk.band();
            }
        }
        return -1;
    }

    public static boolean holdsLens(ServerPlayer player) {
        return ChronicleLensItem.isHeld(player);
    }
}
