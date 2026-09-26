package com.mnemolith.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Remembers the last lens snapshot each player was sent, and the last perf walk.
 * A matching stamp skips the chunk walk. Logout and a dimension change drop the stamp.
 */
final class LensPollCache {
    /** Bumped from the server thread and from worldgen threads (mute pockets), so it must be atomic. */
    private static final AtomicInteger MEMORY_EPOCH = new AtomicInteger();
    private static int perfEpoch = -1;
    private static int perfChunkX;
    private static int perfChunkZ;
    private static ResourceKey<Level> perfDimension;
    private static final Map<UUID, Stamp> STAMPS = new HashMap<>();

    private LensPollCache() {}

    static void markDirty() {
        MEMORY_EPOCH.incrementAndGet();
    }

    static int epoch() {
        return MEMORY_EPOCH.get();
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
        return perfEpoch == MEMORY_EPOCH.get()
                && perfChunkX == origin.x()
                && perfChunkZ == origin.z()
                && level.dimension().equals(perfDimension);
    }

    static void rememberPerf(ServerLevel level, ChunkPos origin) {
        perfEpoch = MEMORY_EPOCH.get();
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
        private final long walked;
        private long shimmer;

        Stamp(int epoch, ResourceKey<Level> dimension, int x, int z, boolean lens, boolean ambient, long shimmer) {
            this.epoch = epoch;
            this.dimension = dimension;
            this.x = x;
            this.z = z;
            this.lens = lens;
            this.ambient = ambient;
            this.walked = shimmer;
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

        /**
         * Same player view (dimension, chunk, lens and ambient mode) walked less than {@code minTicks} ago. Only the
         * global memory epoch can have moved, which busy servers bump constantly; the next poll picks the change up.
         */
        boolean tooSoon(ResourceKey<Level> dimension, int x, int z, boolean lens, boolean ambient, long now, int minTicks) {
            return this.dimension.equals(dimension)
                    && this.x == x
                    && this.z == z
                    && this.lens == lens
                    && this.ambient == ambient
                    && now - this.walked < minTicks;
        }

        long shimmer() {
            return this.shimmer;
        }

        void setShimmer(long shimmer) {
            this.shimmer = shimmer;
        }
    }
}
