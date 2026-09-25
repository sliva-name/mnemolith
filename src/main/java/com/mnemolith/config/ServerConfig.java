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

        SpecValues.section(builder, "server", "Authority rules for recollection storms. Instability cooling and imprint strengths live in the common gameplay section. The logical server owns these values.");
        ALLOW_RECOLLECTION_STORMS = SpecValues.bool(builder, "allowRecollectionStorms", "Whether recollection storms are allowed to start.", true);
        MAX_STORMS_PER_DIMENSION = SpecValues.integer(builder, "maxStormsPerDimension", "Maximum recollection storms active in one dimension at once.", 1, 0, 16);
        LOG_PRESSURE_CHANGES = SpecValues.bool(builder, "logPressureChanges", "Whether a pressure band change, other than fracture, is written to the server log.", false);
        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
