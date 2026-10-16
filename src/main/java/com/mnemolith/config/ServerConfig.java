package com.mnemolith.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config ({@code mnemolith-server.toml}). Loaded for the integrated server and the dedicated server,
 * and synced to clients. A per-world copy may live under the world's {@code serverconfig} folder.
 */
public final class ServerConfig {
    public static final ModConfigSpec.BooleanValue ALLOW_RECOLLECTION_STORMS;
    public static final ModConfigSpec.IntValue MAX_STORMS_PER_DIMENSION;
    public static final ModConfigSpec.IntValue STORM_UNWATCHED_TICKS;
    public static final ModConfigSpec.IntValue STORM_LULL_TICKS;
    public static final ModConfigSpec.BooleanValue LOG_PRESSURE_CHANGES;
    public static final ModConfigSpec.BooleanValue UNREMEMBERED_ENABLED;
    public static final ModConfigSpec.DoubleValue UNREMEMBERED_HEALTH;
    public static final ModConfigSpec.DoubleValue UNREMEMBERED_HEALTH_PER_PLAYER;
    public static final ModConfigSpec.IntValue UNREMEMBERED_CALL_FORGOTTEN;
    public static final ModConfigSpec.BooleanValue CINDERKIN_SPAWNS;
    public static final ModConfigSpec.IntValue EMBER_BRAZIER_BURN_TICKS;
    public static final ModConfigSpec.IntValue EMBER_BRAZIER_CHARGE_PER_EMBER;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SpecValues.section(builder, "server", "Authority rules for recollection storms (they run only on the logical server). How often they are attempted is stormAttemptChance in the common spawnRates section. Instability cooling and imprint strengths live in the common gameplay section.");
        ALLOW_RECOLLECTION_STORMS = SpecValues.bool(builder, "allowRecollectionStorms", "Whether recollection storms may gather, naturally or called by a freed residual shard. Turning it off ends storms that are running (their residues stay as ordinary residues); Scar sites and the Scar stay.", true);
        MAX_STORMS_PER_DIMENSION = SpecValues.integer(builder, "maxStormsPerDimension", "Maximum recollection storms gathering or raging in one dimension at once. 0 stops new storms like allowRecollectionStorms false, without ending running ones.", 1, 0, 16);
        STORM_UNWATCHED_TICKS = SpecValues.integer(builder, "stormUnwatchedTicks", "Ticks a recollection storm may go unwatched (its centre chunk unloaded, or its last wave waiting on residues in unloaded chunks) before it fades: it ends without a Scar, frees the storm cap, and halves the instability of its area the next time the centre loads. 0 keeps the old behaviour (an unwatched storm waits, frozen).", 2400, 0, 72000);
        STORM_LULL_TICKS = SpecValues.integer(builder, "stormLullTicks", "Ticks a spent or contained recollection storm keeps its surroundings (two chunks from its centre) free of new storms. 24000 is one Minecraft day. 0 turns lulls off: a cleared area can storm again at once.", 24000, 0, 168000);
        LOG_PRESSURE_CHANGES = SpecValues.bool(builder, "logPressureChanges", "Whether a pressure band change, other than fracture, is written to the server log.", false);
        builder.pop();

        SpecValues.section(builder, "unremembered", "The Unremembered, the Memory Hollows boss called with a hollow effigy.");
        UNREMEMBERED_ENABLED = SpecValues.bool(builder, "unrememberedEnabled", "Whether a hollow effigy can call the Unremembered. One already risen stays.", true);
        UNREMEMBERED_HEALTH = SpecValues.decimal(builder, "unrememberedHealth", "Health of the Unremembered when one player takes part.", 160.0D, 40.0D, 1024.0D);
        UNREMEMBERED_HEALTH_PER_PLAYER = SpecValues.decimal(builder, "unrememberedHealthPerPlayer", "Extra health for each further player within 32 blocks when it is called, as a share of unrememberedHealth (0.5: two players face 1.5x). 0 turns scaling off.", 0.5D, 0.0D, 2.0D);
        UNREMEMBERED_CALL_FORGOTTEN = SpecValues.integer(builder, "unrememberedCallForgotten", "Forgotten imprints a hollow chunk must hold before a hollow effigy can call the Unremembered there.", 3, 1, 32);
        builder.pop();

        SpecValues.section(builder, "embers", "Embers of memory: the cinderkin of the Nether and the ember brazier.");
        CINDERKIN_SPAWNS = SpecValues.bool(builder, "cinderkinSpawns", "Whether cinderkin spawn naturally in the Nether. Spawn eggs and commands still work; cinderkin already out stay.", true);
        EMBER_BRAZIER_BURN_TICKS = SpecValues.integer(builder, "emberBrazierBurnTicks", "Ticks between two burns of a lit ember brazier (each burns the loudest imprint of its chunk). 100 is 5 seconds.", 100, 20, 1200);
        EMBER_BRAZIER_CHARGE_PER_EMBER = SpecValues.integer(builder, "emberBrazierChargePerEmber", "Burns one memory ember gives an ember brazier. A brazier holds 16.", 4, 1, 16);
        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
