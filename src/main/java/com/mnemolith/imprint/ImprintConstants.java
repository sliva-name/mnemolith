package com.mnemolith.imprint;

/**
 * Fixed rules for imprint intensity, caps, and effect timing.
 * Tunable rates live in config; these are the scales those rates are written against.
 */
public final class ImprintConstants {
    public static final int INTENSITY_MIN = 1;
    public static final int INTENSITY_MAX = 10;

    public static final int DEATH_INTENSITY = 3;
    public static final int EXPLOSION_INTENSITY = 3;
    public static final int FALL_INTENSITY = 2;
    public static final int FIRE_INTENSITY = 2;
    public static final int SILENCE_INTENSITY = 2;
    public static final int PLAYER_INTENSITY = 2;
    public static final int BUILD_INTENSITY = 1;
    public static final int REDSTONE_INTENSITY = 1;

    /** Codec and mute-list ceiling. Gameplay uses {@code gameplay.maxImprintsPerChunk}, which cannot exceed this. */
    public static final int ABSOLUTE_LIST_CAP = 64;

    public static final int NEEDLE_DURABILITY = 64;
    public static final int SLIP_STACK_SIZE = 16;

    public static final int UNRECORDED_DURATION_TICKS = 200;
    public static final int FIRE_TRAIL_DURATION_TICKS = 160;
    public static final int LANDING_BURST_DURATION_TICKS = 600;
    public static final int FIRE_TRAIL_INTERVAL_TICKS = 10;
    public static final double LANDING_BURST_MIN_DISTANCE = 2.0D;
    public static final float LANDING_BURST_DAMAGE_MULTIPLIER = 0.2F;
    public static final double FIRE_TRAIL_SPEED_BONUS = 0.08D;

    /** Blocks. An unwitnessed death inside this distance of a player does not gain the silence tag. */
    public static final double WITNESS_RANGE = 32.0D;
    public static final double WITNESS_RANGE_SQR = WITNESS_RANGE * WITNESS_RANGE;

    /** Chebyshev radius, in chunks, of a lens pressure request. 2 → 25 chunks. */
    public static final int LENS_CHUNK_RADIUS = 2;
    public static final int LENS_CHUNK_LIMIT = 25;

    public static final int SERVER_PARTICLE_COUNT = 6;
    public static final int LENS_PARTICLES_PER_CHUNK = 4;

    public static final int COMPOSE_BUTTON_ID = 0;
    public static final int COMPOSITION_SLOTS = 3;

    private ImprintConstants() {}
}
