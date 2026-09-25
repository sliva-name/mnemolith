package com.mnemolith.worldgen;

import com.mnemolith.config.CommonConfig;

/** Distances and config reads for veins, mute pockets, and the observatory. */
public final class WorldgenTuning {
    /** Chebyshev chunks around an observatory where an archivist's pressure gate is lower. */
    public static final int OBSERVATORY_CHUNK_RADIUS = 2;
    public static final int OBSERVATORY_RELIEF = 14;
    public static final int PATH_RELIEF = 8;
    public static final int LENS_VEIN_RANGE = 24;
    /** Vein marks hinted to the lens holder in one snapshot. */
    public static final int LENS_VEIN_HINTS = 8;
    public static final int POCKET_HALF = 2;

    private WorldgenTuning() {}

    public static boolean veinsEnabled() {
        return CommonConfig.ARCHIVAL_VEINS_ENABLED.get();
    }

    public static int veinChance() {
        return CommonConfig.ARCHIVAL_VEIN_CHANCE.get();
    }

    public static int veinMinY() {
        return CommonConfig.ARCHIVAL_VEIN_MIN_Y.get();
    }

    public static int veinMaxY() {
        return CommonConfig.ARCHIVAL_VEIN_MAX_Y.get();
    }

    public static int veinSize() {
        return CommonConfig.ARCHIVAL_VEIN_SIZE.get();
    }

    public static boolean pocketsEnabled() {
        return CommonConfig.MUTE_POCKETS_ENABLED.get();
    }

    public static int pocketChance() {
        return CommonConfig.MUTE_POCKET_CHANCE.get();
    }

    public static int pocketMinY() {
        return CommonConfig.MUTE_POCKET_MIN_Y.get();
    }

    public static int pocketMaxY() {
        return CommonConfig.MUTE_POCKET_MAX_Y.get();
    }

    public static boolean observatoryEnabled() {
        return CommonConfig.STRUCTURES_ENABLED.get() && CommonConfig.OBSERVATORY_ENABLED.get();
    }

    public static boolean archivistObservatoryBias() {
        return CommonConfig.ARCHIVIST_OBSERVATORY_BIAS.get();
    }

    public static boolean striderPathBias() {
        return CommonConfig.STRIDER_PATH_BIAS.get();
    }
}
