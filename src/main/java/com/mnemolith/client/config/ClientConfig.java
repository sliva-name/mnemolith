package com.mnemolith.client.config;

import com.mnemolith.config.SpecValues;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config ({@code mnemolith-client.toml}). Registered from {@code MnemolithClient}, so this type
 * is not loaded on a dedicated server. Visual options never decide storm outcomes.
 */
public final class ClientConfig {
    public static final ModConfigSpec.BooleanValue IMPRINT_PARTICLES;
    public static final ModConfigSpec.BooleanValue PRESSURE_VIGNETTE;
    public static final ModConfigSpec.BooleanValue STORM_SCREEN_SHAKE;
    public static final ModConfigSpec.DoubleValue MEMORY_AUDIO_VOLUME;
    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ModConfigSpec.IntValue MAX_PARTICLES_PER_TICK;
    public static final ModConfigSpec.BooleanValue AMBIENT_WITHOUT_LENS;
    public static final ModConfigSpec.IntValue LENS_POLL_INTERVAL;
    public static final ModConfigSpec.BooleanValue LENS_OVERLAY;
    public static final ModConfigSpec.DoubleValue OVERLAY_OPACITY;
    public static final ModConfigSpec.BooleanValue SHOW_NUMERIC_PRESSURE;
    public static final ModConfigSpec.BooleanValue THERMAL_VIEW;
    public static final ModConfigSpec.DoubleValue ECHO_AIM_ASSIST;
    public static final ModConfigSpec.BooleanValue ECHO_HINTS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SpecValues.section(builder, "visuals", "Local presentation of memory. These options stay on this client.");
        IMPRINT_PARTICLES = SpecValues.bool(builder, "imprintParticles", "Whether custom memory particles are shown.", true);
        PRESSURE_VIGNETTE = SpecValues.bool(builder, "pressureVignette", "Whether rising memory pressure darkens the screen edge.", true);
        STORM_SCREEN_SHAKE = SpecValues.bool(builder, "stormScreenShake", "Whether a recollection storm shakes the camera.", true);
        MEMORY_AUDIO_VOLUME = SpecValues.decimal(builder, "memoryAudioVolume", "Volume scale, from 0.0 to 1.0, for the local chronicle lens chime.", 1.0D, 0.0D, 1.0D);
        PARTICLE_DENSITY = SpecValues.decimal(builder, "particleDensity", "Scale, from 0.0 to 1.0, for every custom memory particle. 0 disables them.", 1.0D, 0.0D, 1.0D);
        MAX_PARTICLES_PER_TICK = SpecValues.integer(builder, "maxParticlesPerTick", "Hard cap on custom memory particles spawned in one client tick. Density still scales the budget, and 0 density disables them.", 48, 1, 256);
        AMBIENT_WITHOUT_LENS = SpecValues.bool(builder, "ambientWithoutLens", "Whether saturated chunks shimmer without a chronicle lens. Vein marks stay lens-only.", false);
        LENS_POLL_INTERVAL = SpecValues.integer(builder, "lensPollInterval", "Ticks between chronicle lens pressure requests.", 20, 1, 200);
        LENS_OVERLAY = SpecValues.bool(builder, "lensOverlay", "Whether the chronicle lens draws the pressure pill above the hotbar.", true);
        OVERLAY_OPACITY = SpecValues.decimal(builder, "overlayOpacity", "Opacity, from 0.2 to 1.0, of the lens pressure pill. The band name stays readable.", 0.85D, 0.2D, 1.0D);
        SHOW_NUMERIC_PRESSURE = SpecValues.bool(builder, "showNumericPressure", "Whether the lens pill includes the numeric pressure beside the band name.", true);
        THERMAL_VIEW = SpecValues.bool(builder, "thermalView", "Whether holding use with the chronicle lens tints the view dark pink. Echo outlines and targeting stay on either way.", true);
        ECHO_AIM_ASSIST = SpecValues.decimal(builder, "echoAimAssist", "Aim assist cone, in degrees, for targeting your echo through the lens.", 6.0D, 0.0D, 20.0D);
        ECHO_HINTS = SpecValues.bool(builder, "echoHints", "Whether the lens and possession draw short key hints.", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
