package com.mnemolith.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Remembers the last lens snapshot each player was sent, and the last perf walk.
 * A matching stamp skips the chunk walk. Logout and a dimension change drop the stamp.
 */
final class LensPollCache {
    private static int memoryEpoch;
    private static int perfEpoch = -1;
    private static int perfChunkX;
    private static int perfChunkZ;
    private static ResourceKey<Level> perfDimension;
    private static final Map<UUID, Stamp> STAMPS = new HashMap<>();

    private LensPollCache() {}

    static void markDirty() {
        memoryEpoch++;
    }

    static int epoch() {
        return memoryEpoch;
    }

    static void forget(UUID player) {
        STAMPS.remove(player);
    }

    static Stamp get(UUID player) {
        return STAMPS.get(player);
    }

    static void put(UUID player, Stamp stamp) {
        STAMPS.put(player, stamp);
    }

    static boolean skipPerf(ServerLevel level, ChunkPos origin) {
        return perfEpoch == memoryEpoch
                && perfChunkX == origin.x()
                && perfChunkZ == origin.z()
                && level.dimension().equals(perfDimension);
    }

    static void rememberPerf(ServerLevel level, ChunkPos origin) {
        perfEpoch = memoryEpoch;
        perfChunkX = origin.x();
        perfChunkZ = origin.z();
        perfDimension = level.dimension();
    }

    static boolean perfStampMatches(ServerLevel level, ChunkPos origin) {
        if (perfDimension == null) {
            return false;
        }
        return skipPerf(level, origin);
    }

    static final class Stamp {
        private final int epoch;
        private final ResourceKey<Level> dimension;
        private final int x;
        private final int z;
        private final boolean lens;
        private final boolean ambient;
        private long shimmer;

        Stamp(int epoch, ResourceKey<Level> dimension, int x, int z, boolean lens, boolean ambient, long shimmer) {
            this.epoch = epoch;
            this.dimension = dimension;
            this.x = x;
            this.z = z;
            this.lens = lens;
            this.ambient = ambient;
            this.shimmer = shimmer;
        }

        boolean matches(int epoch, ResourceKey<Level> dimension, int x, int z, boolean lens, boolean ambient) {
            return this.epoch == epoch
                    && this.dimension.equals(dimension)
                    && this.x == x
                    && this.z == z
                    && this.lens == lens
                    && this.ambient == ambient;
        }

        long shimmer() {
            return this.shimmer;
        }

        void setShimmer(long shimmer) {
            this.shimmer = shimmer;
        }
    }
}
