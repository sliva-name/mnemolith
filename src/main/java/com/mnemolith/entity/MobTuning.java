package com.mnemolith.entity;

import com.mnemolith.config.CommonConfig;

/** Distances and timings that are part of the mob fantasy, plus config reads. */
public final class MobTuning {
    public static final double TWIN_CHANCE = 0.1D;
    public static final int CHARGE_TELEGRAPH_TICKS = 40;
    public static final int PHASE_LIMIT = 12;
    public static final double LENS_FLEE_RANGE = 8.0D;
    public static final double PATH_ARRIVE_SQR = 4.0D;
    public static final int INTEREST_RANGE = 16;
    public static final double SNATCH_RANGE_SQR = 36.0D;
    public static final double BAIT_RANGE = 10.0D;
    public static final double RESONATOR_RANGE = 4.0D;
    public static final int ACTION_WINDOW_TICKS = 100;
    public static final int BLIND_TICKS = 80;
    public static final int REPLICANT_TELEGRAPH_TICKS = 40;
    public static final double REPLICANT_CLEARANCE = 24.0D;
    public static final int HIGH_VALUE_WEIGHT = 8;

    private MobTuning() {}

    public static boolean striderEnabled() {
        return CommonConfig.ECHO_STRIDER_ENABLED.get();
    }

    public static boolean archivistEnabled() {
        return CommonConfig.ARCHIVIST_ENABLED.get();
    }

    public static boolean replicantEnabled() {
        return CommonConfig.MOMENT_REPLICANT_ENABLED.get();
    }

    public static double striderDamage() {
        return CommonConfig.ECHO_STRIDER_DAMAGE.get();
    }

    public static double archivistDamage() {
        return CommonConfig.ARCHIVIST_DAMAGE.get();
    }

    public static double replicantDamage() {
        return CommonConfig.REPLICANT_DAMAGE.get();
    }

    public static int stealCooldown() {
        return CommonConfig.ARCHIVIST_STEAL_COOLDOWN.get();
    }

    public static int striderWeight() {
        return CommonConfig.ECHO_STRIDER_SPAWN_WEIGHT.get();
    }

    public static int archivistWeight() {
        return CommonConfig.ARCHIVIST_SPAWN_WEIGHT.get();
    }

    public static int replicantWeight() {
        return CommonConfig.REPLICANT_SPAWN_WEIGHT.get();
    }

    public static int striderMinPressure() {
        return CommonConfig.ECHO_STRIDER_MIN_PRESSURE.get();
    }

    public static int archivistMinPressure() {
        return CommonConfig.ARCHIVIST_MIN_PRESSURE.get();
    }

    public static int replicantMinPressure() {
        return CommonConfig.REPLICANT_MIN_PRESSURE.get();
    }
}
