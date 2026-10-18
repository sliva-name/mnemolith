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
 * The band is carried by density, size, lifetime and rise of pale ground motes; the loudest kind of
 * memory is carried by colour ({@link MemoryGlyphs}, the lens glyph palette) and by motion
 * ({@link AfterEcho#motionFor}, the after-image shapes), so traces, after-images and the lens all
 * speak one language. Fracture additionally lays most of its motes along one glowing seam line
 * across the chunk. Calm chunks grow nothing: the absence is the reading.
 */
public final class MemoryTrace {
    /** Client ticks between trace spawn rounds. */
    public static final int SPAWN_PERIOD = 5;
    /** Chebyshev distance, in chunks from the player, that grows traces. */
    public static final int RADIUS = 2;
    /** Hard cap on spawn attempts in one round, whatever the neighbourhood looks like. */
    public static final int MAX_PER_ROUND = 12;
    /** Ticks stood still before traces in the player's own chunk bloom (the after-image threshold). */
    public static final int STILL_TICKS = 60;

    /** One band's trace look. {@code motes} is per chunk per spawn round at density 1. */
    public record Style(int motes, float size, int life, double rise, float alpha, boolean seam) {}

    private static final Style CALM = new Style(0, 0.0F, 0, 0.0D, 0.0F, false);
    private static final Style SATURATED = new Style(2, 0.24F, 80, 0.006D, 0.70F, false);
    private static final Style OVERLOADED = new Style(3, 0.30F, 95, 0.010D, 0.90F, false);
    private static final Style FRACTURE = new Style(4, 0.36F, 120, 0.013D, 1.0F, true);

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

    /** The kind whose colour and motion a trace wears; null when the snapshot names none. */
    public static ImprintTag tag(int loudestOrdinal) {
        return loudestOrdinal < 0 || loudestOrdinal >= ImprintTag.values().length ? null : ImprintTag.values()[loudestOrdinal];
    }

    /**
     * One point on a chunk's fracture seam: a straight line across the chunk (one of four directions,
     * chosen deterministically from the chunk position so the seam does not crawl between rounds),
     * {@code t} in 0..1 along it. Returns block coordinates {@code [x, z]} inside the chunk.
     */
    public static int[] seamPoint(ChunkPos chunk, double t, RandomSource random) {
        long hash = (chunk.x() * 341873128712L + chunk.z() * 132897987541L) ^ 0x5DEECE66DL;
        hash = (hash ^ (hash >>> 33)) * 0xFF51AFD7ED558CCDL;
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
