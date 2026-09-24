package com.mnemolith.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.world.ChunkState;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.WorldgenTuning;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server answers a lens request with the chunks around that player. Unchanged snapshots are not resent. */
public final class PressureSync {
    private static int memoryEpoch;
    private static int perfEpoch = -1;
    private static int perfChunkX;
    private static int perfChunkZ;
    private static ResourceKey<Level> perfDimension;
    private static final Map<UUID, Stamp> STAMPS = new HashMap<>();

    private PressureSync() {}

    /** Any pressure, mute, or fracture change. Lens polls skip their chunk walk until this moves. */
    public static void markDirty() {
        memoryEpoch++;
    }

    public static void forget(UUID player) {
        STAMPS.remove(player);
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
        if (!lens && !payload.ambient()) {
            return;
        }
        ChunkPos origin = ChunkPos.containing(player.blockPosition());
        Stamp stamp = STAMPS.get(player.getUUID());
        if (stamp != null && stamp.matches(memoryEpoch, level.dimension(), origin.x(), origin.z(), lens, payload.ambient())) {
            int interval = CommonConfig.VEIN_SHIMMER_TICKS.get();
            if (lens && interval > 0 && level.getGameTime() - stamp.shimmer >= interval) {
                shimmerVeins(level, player, origin);
                stamp.shimmer = level.getGameTime();
            }
            return;
        }
        List<ChunkPressure> chunks = collect(level, player.blockPosition());
        if (lens) {
            shimmerVeins(level, player, origin);
        }
        STAMPS.put(player.getUUID(), new Stamp(memoryEpoch, level.dimension(), origin.x(), origin.z(), lens, payload.ambient(), level.getGameTime()));
        PacketDistributor.sendToPlayer(player, new PressureSnapshotPayload(List.copyOf(chunks)));
    }

    /**
     * One lens-sized chunk walk, used by {@code /mnemolith perf}. Returns 1 when the walk ran.
     * A repeat with the same memory epoch and chunk returns 0.
     */
    public static int timedPoll(ServerLevel level, BlockPos pos) {
        ChunkPos origin = ChunkPos.containing(pos);
        if (perfEpoch == memoryEpoch && perfChunkX == origin.x() && perfChunkZ == origin.z() && level.dimension().equals(perfDimension)) {
            return 0;
        }
        collect(level, pos);
        perfEpoch = memoryEpoch;
        perfChunkX = origin.x();
        perfChunkZ = origin.z();
        perfDimension = level.dimension();
        return 1;
    }

    /**
     * True when the last lens-sized walk was for this dimension and chunk and memory has not changed since.
     * A dimension change fails the match, which is the stamp {@code /mnemolith qa} checks.
     */
    public static boolean perfStampMatches(ServerLevel level, BlockPos pos) {
        if (perfDimension == null) {
            return false;
        }
        ChunkPos origin = ChunkPos.containing(pos);
        return perfEpoch == memoryEpoch
                && perfChunkX == origin.x()
                && perfChunkZ == origin.z()
                && perfDimension.equals(level.dimension());
    }

    /** Band ordinal for the chunk containing {@code pos}, using the same walk a lens snapshot uses. */
    public static int originBand(ServerLevel level, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (ChunkPressure chunk : collect(level, pos)) {
            if (chunk.chunkX() == chunkX && chunk.chunkZ() == chunkZ) {
                return chunk.band();
            }
        }
        return -1;
    }

    private static List<ChunkPressure> collect(ServerLevel level, BlockPos playerPos) {
        ChunkPos origin = ChunkPos.containing(playerPos);
        ChunkMemory originMemory = LoadedChunkMemory.existing(level.getChunk(origin.x(), origin.z()));
        int radius = ImprintConstants.LENS_CHUNK_RADIUS;
        if (originMemory != null && originMemory.strataCount() > 0) {
            radius += 1;
        }
        int muteRadius = CommonConfig.MUTE_RADIUS_CHUNKS.get();
        List<ChunkPressure> chunks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = origin.x() + dx;
                int chunkZ = origin.z() + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                if (chunks.size() >= ImprintConstants.LENS_CHUNK_LIMIT) {
                    break;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                int pressure = memory == null ? 0 : memory.cachedPressure();
                PressureBand band = MemoryPressure.band(pressure);
                BlockPos sample = new BlockPos((chunkX << 4) + 8, playerPos.getY(), (chunkZ << 4) + 8);
                boolean muted = memory != null && memory.hasMuteStone();
                if (!muted && muteRadius > 0) {
                    muted = LoadedChunkMemory.isMuted(level, sample);
                }
                chunks.add(new ChunkPressure(chunkX, chunkZ, pressure, band.ordinal(), LoadedChunkMemory.stateOf(memory, muted).ordinal()));
            }
        }
        return chunks;
    }

    private static void shimmerVeins(ServerLevel level, ServerPlayer player, ChunkPos origin) {
        BlockPos playerPos = player.blockPosition();
        int hinted = 0;
        long rangeSqr = (long) WorldgenTuning.LENS_VEIN_RANGE * WorldgenTuning.LENS_VEIN_RANGE;
        for (int dx = -1; dx <= 1 && hinted < 8; dx++) {
            for (int dz = -1; dz <= 1 && hinted < 8; dz++) {
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
                    if (hinted >= 8 || mark.distSqr(playerPos) > rangeSqr) {
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

    public static boolean holdsLens(ServerPlayer player) {
        return player.getMainHandItem().getItem() == ModItems.CHRONICLE_LENS.get()
                || player.getOffhandItem().getItem() == ModItems.CHRONICLE_LENS.get();
    }

    private static final class Stamp {
        private final int epoch;
        private final ResourceKey<Level> dimension;
        private final int x;
        private final int z;
        private final boolean lens;
        private final boolean ambient;
        private long shimmer;

        private Stamp(int epoch, ResourceKey<Level> dimension, int x, int z, boolean lens, boolean ambient, long shimmer) {
            this.epoch = epoch;
            this.dimension = dimension;
            this.x = x;
            this.z = z;
            this.lens = lens;
            this.ambient = ambient;
            this.shimmer = shimmer;
        }

        private boolean matches(int epoch, ResourceKey<Level> dimension, int x, int z, boolean lens, boolean ambient) {
            return this.epoch == epoch
                    && this.dimension.equals(dimension)
                    && this.x == x
                    && this.z == z
                    && this.lens == lens
                    && this.ambient == ambient;
        }
    }
}
