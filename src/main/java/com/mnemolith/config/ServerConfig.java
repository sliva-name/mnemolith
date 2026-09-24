package com.mnemolith.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config ({@code mnemolith-server.toml}). Loaded for the integrated server and the dedicated server,
 * and synced to clients. A per-world copy may live under the world's {@code serverconfig} folder.
 */
public final class ServerConfig {
    public static final ModConfigSpec.BooleanValue ALLOW_RECOLLECTION_STORMS;
    public static final ModConfigSpec.IntValue MAX_STORMS_PER_DIMENSION;
    public static final ModConfigSpec.BooleanValue LOG_PRESSURE_CHANGES;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Authority rules for recollection storms. The logical server owns these values.")
                .translation("mnemolith.configuration.server")
                .push("server");
        ALLOW_RECOLLECTION_STORMS = builder
                .comment("Whether recollection storms are allowed to start.")
                .translation("mnemolith.configuration.allowRecollectionStorms")
                .define("allowRecollectionStorms", true);
        MAX_STORMS_PER_DIMENSION = builder
                .comment("Maximum recollection storms active in one dimension at once.")
                .translation("mnemolith.configuration.maxStormsPerDimension")
                .defineInRange("maxStormsPerDimension", 1, 0, 16);
        LOG_PRESSURE_CHANGES = builder
                .comment("Whether pressure changes are written to the server log. Intended for later debugging.")
                .translation("mnemolith.configuration.logPressureChanges")
                .define("logPressureChanges", false);
        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
