package com.mnemolith.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config ({@code mnemolith-common.toml}). Loaded on the physical client and the dedicated server.
 * Gameplay does not read these values yet; they are the defaults later phases will use.
 */
public final class CommonConfig {
    public static final ModConfigSpec.DoubleValue RECOLLECTION_STORM_THRESHOLD;
    public static final ModConfigSpec.IntValue PRESSURE_SOFT_CAP;
    public static final ModConfigSpec.IntValue IMPRINT_NODE_WEIGHT;
    public static final ModConfigSpec.DoubleValue STORM_ATTEMPT_CHANCE;
    public static final ModConfigSpec.BooleanValue STRUCTURES_ENABLED;
    public static final ModConfigSpec.IntValue STRUCTURE_SPACING;
    public static final ModConfigSpec.BooleanValue COMPOSITION_ENABLED;
    public static final ModConfigSpec.IntValue MAX_IMPRINTS_PER_CHUNK;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Thresholds that decide when recollection becomes dangerous.")
                .translation("mnemolith.configuration.difficulty")
                .push("difficulty");
        RECOLLECTION_STORM_THRESHOLD = builder
                .comment("Multiplier applied to memory-pressure thresholds before a recollection storm can start.")
                .translation("mnemolith.configuration.recollectionStormThreshold")
                .defineInRange("recollectionStormThreshold", 1.0D, 0.1D, 10.0D);
        PRESSURE_SOFT_CAP = builder
                .comment("Soft cap for memory pressure accumulated from imprints in loaded chunks.")
                .translation("mnemolith.configuration.pressureSoftCap")
                .defineInRange("pressureSoftCap", 100, 1, 10_000);
        builder.pop();

        builder.comment("Relative weights for future imprint nodes and storm attempts. Worldgen does not use these yet.")
                .translation("mnemolith.configuration.spawnRates")
                .push("spawnRates");
        IMPRINT_NODE_WEIGHT = builder
                .comment("Relative weight of imprint-bearing nodes when structures are placed.")
                .translation("mnemolith.configuration.imprintNodeWeight")
                .defineInRange("imprintNodeWeight", 4, 0, 100);
        STORM_ATTEMPT_CHANCE = builder
                .comment("Chance, from 0.0 to 1.0, that a pressure check attempts a recollection storm.")
                .translation("mnemolith.configuration.stormAttemptChance")
                .defineInRange("stormAttemptChance", 0.02D, 0.0D, 1.0D);
        builder.pop();

        builder.comment("Structure placement defaults. Changing these takes effect the next time a world loads.")
                .translation("mnemolith.configuration.worldGen")
                .push("worldGen");
        STRUCTURES_ENABLED = builder
                .comment("Whether Mnemolith structures may generate.")
                .translation("mnemolith.configuration.structuresEnabled")
                .worldRestart()
                .define("structuresEnabled", true);
        STRUCTURE_SPACING = builder
                .comment("Target spacing, in chunks, between Mnemolith structures.")
                .translation("mnemolith.configuration.structureSpacing")
                .worldRestart()
                .defineInRange("structureSpacing", 32, 8, 256);
        builder.pop();

        builder.comment("Player-facing memory rules. Composition is not implemented yet.")
                .translation("mnemolith.configuration.gameplay")
                .push("gameplay");
        COMPOSITION_ENABLED = builder
                .comment("Whether players may compose extracted imprints.")
                .translation("mnemolith.configuration.compositionEnabled")
                .define("compositionEnabled", true);
        MAX_IMPRINTS_PER_CHUNK = builder
                .comment("Maximum imprints stored on a single chunk.")
                .translation("mnemolith.configuration.maxImprintsPerChunk")
                .defineInRange("maxImprintsPerChunk", 8, 1, 64);
        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {}
}
