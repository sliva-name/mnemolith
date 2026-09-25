package com.mnemolith.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config entries. The key, comment, translation, default, and range are the toml contract.
 * Translation keys stay {@code mnemolith.configuration.<key>}.
 */
public final class SpecValues {
    private SpecValues() {}

    public static void section(ModConfigSpec.Builder builder, String name, String comment) {
        builder.comment(comment).translation("mnemolith.configuration." + name).push(name);
    }

    public static ModConfigSpec.BooleanValue bool(ModConfigSpec.Builder builder, String key, String comment, boolean value) {
        return builder.comment(comment).translation("mnemolith.configuration." + key).define(key, value);
    }

    public static ModConfigSpec.BooleanValue boolRestart(ModConfigSpec.Builder builder, String key, String comment, boolean value) {
        return builder.comment(comment).translation("mnemolith.configuration." + key).worldRestart().define(key, value);
    }

    public static ModConfigSpec.IntValue integer(ModConfigSpec.Builder builder, String key, String comment, int value, int min, int max) {
        return builder.comment(comment).translation("mnemolith.configuration." + key).defineInRange(key, value, min, max);
    }

    public static ModConfigSpec.IntValue integerRestart(ModConfigSpec.Builder builder, String key, String comment, int value, int min, int max) {
        return builder.comment(comment).translation("mnemolith.configuration." + key).worldRestart().defineInRange(key, value, min, max);
    }

    public static ModConfigSpec.DoubleValue decimal(ModConfigSpec.Builder builder, String key, String comment, double value, double min, double max) {
        return builder.comment(comment).translation("mnemolith.configuration." + key).defineInRange(key, value, min, max);
    }
}
