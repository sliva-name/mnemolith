package com.mnemolith.config;

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
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Local presentation of memory. These options stay on this client.")
                .translation("mnemolith.configuration.visuals")
                .push("visuals");
        IMPRINT_PARTICLES = builder
                .comment("Whether custom memory particles are shown.")
                .translation("mnemolith.configuration.imprintParticles")
                .define("imprintParticles", true);
        PRESSURE_VIGNETTE = builder
                .comment("Whether rising memory pressure darkens the screen edge.")
                .translation("mnemolith.configuration.pressureVignette")
                .define("pressureVignette", true);
        STORM_SCREEN_SHAKE = builder
                .comment("Whether a recollection storm shakes the camera.")
                .translation("mnemolith.configuration.stormScreenShake")
                .define("stormScreenShake", true);
        MEMORY_AUDIO_VOLUME = builder
                .comment("Volume scale, from 0.0 to 1.0, for the local chronicle lens chime.")
                .translation("mnemolith.configuration.memoryAudioVolume")
                .defineInRange("memoryAudioVolume", 1.0D, 0.0D, 1.0D);
        PARTICLE_DENSITY = builder
                .comment("Scale, from 0.0 to 1.0, for every custom memory particle. 0 disables them.")
                .translation("mnemolith.configuration.particleDensity")
                .defineInRange("particleDensity", 1.0D, 0.0D, 1.0D);
        MAX_PARTICLES_PER_TICK = builder
                .comment("Hard cap on custom memory particles spawned in one client tick. Density still scales the budget, and 0 density disables them.")
                .translation("mnemolith.configuration.maxParticlesPerTick")
                .defineInRange("maxParticlesPerTick", 48, 1, 256);
        AMBIENT_WITHOUT_LENS = builder
                .comment("Whether saturated chunks shimmer without a chronicle lens. Vein marks stay lens-only.")
                .translation("mnemolith.configuration.ambientWithoutLens")
                .define("ambientWithoutLens", false);
        LENS_POLL_INTERVAL = builder
                .comment("Ticks between chronicle lens pressure requests.")
                .translation("mnemolith.configuration.lensPollInterval")
                .defineInRange("lensPollInterval", 20, 1, 200);
        LENS_OVERLAY = builder
                .comment("Whether the chronicle lens draws the pressure pill above the hotbar.")
                .translation("mnemolith.configuration.lensOverlay")
                .define("lensOverlay", true);
        OVERLAY_OPACITY = builder
                .comment("Opacity, from 0.2 to 1.0, of the lens pressure pill. The band name stays readable.")
                .translation("mnemolith.configuration.overlayOpacity")
                .defineInRange("overlayOpacity", 0.85D, 0.2D, 1.0D);
        SHOW_NUMERIC_PRESSURE = builder
                .comment("Whether the lens pill includes the numeric pressure beside the band name.")
                .translation("mnemolith.configuration.showNumericPressure")
                .define("showNumericPressure", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
