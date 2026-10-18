package com.mnemolith.particle;

import com.mnemolith.common.MemoryGlyphs;
import com.mnemolith.common.MemoryPalette;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.pressure.PressureBand;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

/**
 * Visible memory: how a chunk's pressure band reads in the world itself, with no lens and no HUD.
 * Common code (the client spawner and provider read it; {@code tracesqa} verifies it on a server).
 * <p>
 * Traces are not dust. They hug the ground as slow, lingering stains clustered at a few spots in the
 * chunk — density thins with distance from a cluster centre, the way a place remembers events, not
 * weather. The band is carried by how many clusters grow, how many motes each round drops, and their
 * size, lifetime and glow. The loudest kind of memory is carried by colour ({@link MemoryGlyphs}, the
 * lens glyph palette) and by silhouette ({@link Pattern}, grown from {@link AfterEcho#motionFor}):
 * an explosion leaves a scorched ring, a path a dotted trail, building a block's outline, fire drifting
 * embers, death pale wisps that sink to the ground. Fracture additionally runs a glowing seam — a
 * cracked line of bright motes along the ground across the chunk. Calm chunks grow nothing.
 * <p>
 * The snapshot names no imprint positions, so cluster centres are chosen deterministically from the
 * chunk position: the same place shows its clusters in the same spots every round instead of crawling.
 * Exact origins remain the job of after-images when standing still (and of the lens).
 */
public final class MemoryTrace {
    /** Client ticks between trace spawn rounds. */
    public static final int SPAWN_PERIOD = 5;
    /** Chebyshev distance, in chunks from the player, that grows traces. */
    public static final int RADIUS = 2;
    /** Hard cap on spawn attempts in one round, whatever the neighbourhood looks like. */
    public static final int MAX_PER_ROUND = 16;
    /** Ticks stood still before traces in the player's own chunk bloom (the after-image threshold). */
    public static final int STILL_TICKS = 60;

    /** The silhouette a kind of memory leaves on the ground. */
    public enum Pattern { PATINA, RING, TRAIL, OUTLINE, EMBERS, WISPS }

    /** One band's trace look. {@code motes} is per chunk per spawn round at density 1. */
    public record Style(int motes, float size, int life, double rise, float alpha, boolean seam, int clusters, double spread) {}

    private static final Style CALM = new Style(0, 0.0F, 0, 0.0D, 0.0F, false, 0, 0.0D);
    private static final Style SATURATED = new Style(2, 0.30F, 150, 0.002D, 0.55F, false, 1, 1.8D);
    private static final Style OVERLOADED = new Style(4, 0.34F, 170, 0.003D, 0.75F, false, 2, 2.4D);
    private static final Style FRACTURE = new Style(9, 0.40F, 200, 0.004D, 0.92F, true, 3, 2.8D);

    private MemoryTrace() {}

    public static Style style(PressureBand band) {
        return switch (band) {
            case CALM -> CALM;
            case SATURATED -> SATURATED;
            case OVERLOADED -> OVERLOADED;
            case FRACTURE -> FRACTURE;
        };
    }

    /** Trace colour for a {@code ChunkPressure.loudest()} ordinal; none (band-only snapshot) is the pale after-image colour. */
    public static int colour(int loudestOrdinal) {
        int colour = MemoryGlyphs.colour(loudestOrdinal);
        return colour == 0 ? MemoryPalette.AFTERIMAGE : colour;
    }

    /** The kind whose colour and silhouette a trace wears; null when the snapshot names none. */
    public static ImprintTag tag(int loudestOrdinal) {
        return loudestOrdinal < 0 || loudestOrdinal >= ImprintTag.values().length ? null : ImprintTag.values()[loudestOrdinal];
    }

    /** Silhouette for a kind, grown from its after-image motion so traces and after-images agree. */
    public static Pattern pattern(ImprintTag tag) {
        if (tag == null) {
            return Pattern.PATINA;
        }
        return switch (AfterEcho.motionFor(tag)) {
            case RIPPLE -> Pattern.RING;
            case GROUND -> Pattern.TRAIL;
            case OUTLINE -> Pattern.OUTLINE;
            case COLUMN -> Pattern.EMBERS;
            case SINK -> Pattern.WISPS;
        };
    }

    private static long hash(ChunkPos chunk, int salt) {
        long hash = (chunk.x() * 341873128712L + chunk.z() * 132897987541L + salt * 0x9E3779B97F4A7C15L) ^ 0x5DEECE66DL;
        hash = (hash ^ (hash >>> 33)) * 0xFF51AFD7ED558CCDL;
        return hash ^ (hash >>> 29);
    }

    /**
     * Centre of cluster {@code index} in {@code chunk}: deterministic, inside the chunk with a margin,
     * so a place's clusters sit in the same spots every round. Returns world {@code [x, z]} (block centres).
     */
    public static double[] clusterCenter(ChunkPos chunk, int index) {
        long hash = hash(chunk, index + 1);
        double lx = 3.5D + (double) ((hash >>> 40) % 9);
        double lz = 3.5D + (double) ((hash >>> 48) % 9);
        return new double[] {chunk.getMinBlockX() + lx, chunk.getMinBlockZ() + lz};
    }

    /**
     * One trace point of {@code pattern} around cluster {@code cluster} of {@code chunk}, {@code t} in
     * 0..1 along the silhouette. Returns world {@code [x, z]}, always inside the chunk. Stain-like
     * patterns scatter with a gaussian falloff around the centre (dense in the middle, thinning out);
     * ring, trail and outline follow their line.
     */
    public static double[] patternPoint(Pattern pattern, ChunkPos chunk, int cluster, double t, RandomSource random, double spread) {
        double[] center = clusterCenter(chunk, cluster);
        double x;
        double z;
        switch (pattern) {
            case RING -> {
                double angle = t * Math.PI * 2.0D;
                double radius = 2.6D + (random.nextDouble() - 0.5D) * 0.5D;
                x = center[0] + Math.cos(angle) * radius;
                z = center[1] + Math.sin(angle) * radius;
            }
            case TRAIL -> {
                int direction = (int) (hash(chunk, 77) >>> 40) & 3;
                double along = (t - 0.5D) * 11.0D;
                double jitter = (random.nextDouble() - 0.5D) * 0.6D;
                switch (direction) {
                    case 0 -> { x = center[0] + along; z = center[1] + jitter; }
                    case 1 -> { x = center[0] + jitter; z = center[1] + along; }
                    case 2 -> { x = center[0] + along * 0.71D; z = center[1] + along * 0.71D + jitter; }
                    default -> { x = center[0] + along * 0.71D; z = center[1] - along * 0.71D + jitter; }
                }
            }
            case OUTLINE -> {
                double half = 2.0D;
                double side = t * 4.0D;
                int edge = Math.min(3, (int) side);
                double at = (side - edge) * 2.0D * half - half;
                double jitter = (random.nextDouble() - 0.5D) * 0.3D;
                switch (edge) {
                    case 0 -> { x = center[0] + at; z = center[1] - half + jitter; }
                    case 1 -> { x = center[0] + half + jitter; z = center[1] + at; }
                    case 2 -> { x = center[0] - at; z = center[1] + half + jitter; }
                    default -> { x = center[0] - half + jitter; z = center[1] - at; }
                }
            }
            default -> {
                double falloff = pattern == Pattern.WISPS ? spread * 0.38D : spread * 0.55D;
                x = center[0] + random.nextGaussian() * falloff;
                z = center[1] + random.nextGaussian() * falloff;
            }
        }
        double minX = chunk.getMinBlockX() + 0.3D;
        double minZ = chunk.getMinBlockZ() + 0.3D;
        return new double[] {Math.clamp(x, minX, minX + 15.4D), Math.clamp(z, minZ, minZ + 15.4D)};
    }

    /**
     * One point on a chunk's fracture seam: a straight line across the chunk (one of four directions,
     * chosen deterministically from the chunk position so the seam does not crawl between rounds),
     * {@code t} in 0..1 along it. Returns block coordinates {@code [x, z]} inside the chunk.
     */
    public static int[] seamPoint(ChunkPos chunk, double t, RandomSource random) {
        long hash = hash(chunk, 0);
        int direction = (int) ((hash >>> 40) & 3);
        int offset = 3 + (int) ((hash >>> 48) % 10);
        double along = t * 15.0D;
        double jitter = (random.nextDouble() - 0.5D) * 0.8D;
        double lx;
        double lz;
        switch (direction) {
            case 0 -> { lx = along; lz = offset + jitter; }
            case 1 -> { lx = offset + jitter; lz = along; }
            case 2 -> { lx = along; lz = Math.clamp(along * (offset - 7.5D) / 7.5D + 7.5D + jitter, 0.0D, 15.0D); }
            default -> { lx = along; lz = Math.clamp(15.0D - along * (offset - 7.5D) / 7.5D - 7.5D + jitter, 0.0D, 15.0D); }
        }
        return new int[] {chunk.getMinBlockX() + (int) Math.round(lx), chunk.getMinBlockZ() + (int) Math.round(lz)};
    }
}
