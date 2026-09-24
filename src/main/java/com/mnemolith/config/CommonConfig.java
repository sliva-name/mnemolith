package com.mnemolith.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config ({@code mnemolith-common.toml}). Loaded on the physical client and the dedicated server.
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
    public static final ModConfigSpec.BooleanValue WRITE_IMPRINTS;
    public static final ModConfigSpec.IntValue WRITE_DEBOUNCE_TICKS;
    public static final ModConfigSpec.BooleanValue WRITE_DEATH;
    public static final ModConfigSpec.BooleanValue WRITE_EXPLOSION;
    public static final ModConfigSpec.BooleanValue WRITE_FALL;
    public static final ModConfigSpec.BooleanValue WRITE_BUILD;
    public static final ModConfigSpec.DoubleValue FALL_DISTANCE_MIN;
    public static final ModConfigSpec.IntValue EXTRACTION_DURABILITY_COST;
    public static final ModConfigSpec.IntValue EXTRACTION_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue FAILURE_PRESSURE_SPIKE;
    public static final ModConfigSpec.IntValue INSTABILITY_DECAY;
    public static final ModConfigSpec.IntValue INSTABILITY_DECAY_TICKS;
    public static final ModConfigSpec.IntValue QUIET_FADE_TICKS;
    public static final ModConfigSpec.IntValue VEIN_SHIMMER_TICKS;
    public static final ModConfigSpec.IntValue SATURATED_THRESHOLD;
    public static final ModConfigSpec.IntValue OVERLOADED_THRESHOLD;
    public static final ModConfigSpec.IntValue FRACTURE_THRESHOLD;
    public static final ModConfigSpec.IntValue MUTE_RADIUS_CHUNKS;
    public static final ModConfigSpec.BooleanValue CATALOG_ENABLED;
    public static final ModConfigSpec.BooleanValue DISCOVERY_HINTS;
    public static final ModConfigSpec.BooleanValue ECHO_STRIDER_ENABLED;
    public static final ModConfigSpec.BooleanValue ARCHIVIST_ENABLED;
    public static final ModConfigSpec.BooleanValue MOMENT_REPLICANT_ENABLED;
    public static final ModConfigSpec.DoubleValue ECHO_STRIDER_DAMAGE;
    public static final ModConfigSpec.DoubleValue ARCHIVIST_DAMAGE;
    public static final ModConfigSpec.DoubleValue REPLICANT_DAMAGE;
    public static final ModConfigSpec.IntValue ARCHIVIST_STEAL_COOLDOWN;
    public static final ModConfigSpec.IntValue ECHO_STRIDER_SPAWN_WEIGHT;
    public static final ModConfigSpec.IntValue ARCHIVIST_SPAWN_WEIGHT;
    public static final ModConfigSpec.IntValue REPLICANT_SPAWN_WEIGHT;
    public static final ModConfigSpec.IntValue ECHO_STRIDER_MIN_PRESSURE;
    public static final ModConfigSpec.IntValue ARCHIVIST_MIN_PRESSURE;
    public static final ModConfigSpec.IntValue REPLICANT_MIN_PRESSURE;
    public static final ModConfigSpec.IntValue SENSOR_INTERVAL;
    public static final ModConfigSpec.BooleanValue ARCHIVAL_VEINS_ENABLED;
    public static final ModConfigSpec.IntValue ARCHIVAL_VEIN_CHANCE;
    public static final ModConfigSpec.IntValue ARCHIVAL_VEIN_MIN_Y;
    public static final ModConfigSpec.IntValue ARCHIVAL_VEIN_MAX_Y;
    public static final ModConfigSpec.IntValue ARCHIVAL_VEIN_SIZE;
    public static final ModConfigSpec.IntValue ARCHIVAL_BLEED;
    public static final ModConfigSpec.IntValue ARCHIVAL_BLEED_CAP;
    public static final ModConfigSpec.BooleanValue MUTE_POCKETS_ENABLED;
    public static final ModConfigSpec.IntValue MUTE_POCKET_CHANCE;
    public static final ModConfigSpec.IntValue MUTE_POCKET_MIN_Y;
    public static final ModConfigSpec.IntValue MUTE_POCKET_MAX_Y;
    public static final ModConfigSpec.BooleanValue OBSERVATORY_ENABLED;
    public static final ModConfigSpec.BooleanValue ARCHIVIST_OBSERVATORY_BIAS;
    public static final ModConfigSpec.BooleanValue STRIDER_PATH_BIAS;
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
                .comment("Whether the chronicle observatory may generate. Veins and mute pockets have their own toggles.")
                .translation("mnemolith.configuration.structuresEnabled")
                .worldRestart()
                .define("structuresEnabled", true);
        STRUCTURE_SPACING = builder
                .comment("Documented spacing, in chunks, of the chronicle observatory. The structure set json uses 32 and a separation of 12. Editing this number does not move structures.")
                .translation("mnemolith.configuration.structureSpacing")
                .worldRestart()
                .defineInRange("structureSpacing", 32, 8, 256);
        ARCHIVAL_VEINS_ENABLED = builder
                .comment("Whether archival veins may generate. The biome modifier uses #minecraft:is_overworld. The Y range keeps them underground.")
                .translation("mnemolith.configuration.archivalVeinsEnabled")
                .define("archivalVeinsEnabled", true);
        ARCHIVAL_VEIN_CHANCE = builder
                .comment("Chance, out of 100, that a chunk attempts an archival vein.")
                .translation("mnemolith.configuration.archivalVeinChance")
                .defineInRange("archivalVeinChance", 8, 0, 100);
        ARCHIVAL_VEIN_MIN_Y = builder
                .comment("Lowest Y of an archival vein.")
                .translation("mnemolith.configuration.archivalVeinMinY")
                .defineInRange("archivalVeinMinY", -48, -64, 320);
        ARCHIVAL_VEIN_MAX_Y = builder
                .comment("Highest Y of an archival vein.")
                .translation("mnemolith.configuration.archivalVeinMaxY")
                .defineInRange("archivalVeinMaxY", 32, -64, 320);
        ARCHIVAL_VEIN_SIZE = builder
                .comment("Length, in blocks, of one archival vein.")
                .translation("mnemolith.configuration.archivalVeinSize")
                .defineInRange("archivalVeinSize", 7, 3, 16);
        ARCHIVAL_BLEED = builder
                .comment("Pressure added per archival stratum block when the chunk's pressure is scored.")
                .translation("mnemolith.configuration.archivalBleed")
                .defineInRange("archivalBleed", 1, 0, 20);
        ARCHIVAL_BLEED_CAP = builder
                .comment("Most stratum blocks that contribute pressure in one chunk.")
                .translation("mnemolith.configuration.archivalBleedCap")
                .defineInRange("archivalBleedCap", 6, 0, 64);
        MUTE_POCKETS_ENABLED = builder
                .comment("Whether mute pockets may generate. Placed mute stone uses the same write suppression as a crafted mute stone.")
                .translation("mnemolith.configuration.mutePocketsEnabled")
                .define("mutePocketsEnabled", true);
        MUTE_POCKET_CHANCE = builder
                .comment("Chance, out of 100, that a chunk attempts a mute pocket.")
                .translation("mnemolith.configuration.mutePocketChance")
                .defineInRange("mutePocketChance", 4, 0, 100);
        MUTE_POCKET_MIN_Y = builder
                .comment("Lowest Y of a mute pocket.")
                .translation("mnemolith.configuration.mutePocketMinY")
                .defineInRange("mutePocketMinY", -32, -64, 320);
        MUTE_POCKET_MAX_Y = builder
                .comment("Highest Y of a mute pocket.")
                .translation("mnemolith.configuration.mutePocketMaxY")
                .defineInRange("mutePocketMaxY", 48, -64, 320);
        OBSERVATORY_ENABLED = builder
                .comment("Whether chronicle observatories may generate. Also requires structuresEnabled.")
                .translation("mnemolith.configuration.observatoryEnabled")
                .worldRestart()
                .define("observatoryEnabled", true);
        ARCHIVIST_OBSERVATORY_BIAS = builder
                .comment("Whether an archivist's natural pressure gate is lower in and near an observatory chunk.")
                .translation("mnemolith.configuration.archivistObservatoryBias")
                .define("archivistObservatoryBias", true);
        STRIDER_PATH_BIAS = builder
                .comment("Whether an echo strider's natural pressure gate is lower in a chunk that already holds a path imprint.")
                .translation("mnemolith.configuration.striderPathBias")
                .define("striderPathBias", true);
        builder.pop();

        builder.comment("Player-facing memory rules for writing, extraction, and composition.")
                .translation("mnemolith.configuration.gameplay")
                .push("gameplay");
        COMPOSITION_ENABLED = builder
                .comment("Whether players may compose extracted imprints.")
                .translation("mnemolith.configuration.compositionEnabled")
                .define("compositionEnabled", true);
        MAX_IMPRINTS_PER_CHUNK = builder
                .comment("Maximum imprints stored on a single chunk. Lowest intensity is dropped first.")
                .translation("mnemolith.configuration.maxImprintsPerChunk")
                .defineInRange("maxImprintsPerChunk", 8, 1, 64);
        WRITE_IMPRINTS = builder
                .comment("Whether world events write imprints.")
                .translation("mnemolith.configuration.writeImprints")
                .define("writeImprints", true);
        WRITE_DEBOUNCE_TICKS = builder
                .comment("Ticks that must pass before another build or redstone imprint can be written in the same chunk.")
                .translation("mnemolith.configuration.writeDebounceTicks")
                .defineInRange("writeDebounceTicks", 80, 1, 200);
        WRITE_DEATH = builder
                .comment("Whether a death writes a death imprint.")
                .translation("mnemolith.configuration.writeDeath")
                .define("writeDeath", true);
        WRITE_EXPLOSION = builder
                .comment("Whether an explosion writes an explosion imprint.")
                .translation("mnemolith.configuration.writeExplosion")
                .define("writeExplosion", true);
        WRITE_FALL = builder
                .comment("Whether a significant fall writes a fall imprint.")
                .translation("mnemolith.configuration.writeFall")
                .define("writeFall", true);
        WRITE_BUILD = builder
                .comment("Whether placing or breaking a block writes a build or redstone imprint.")
                .translation("mnemolith.configuration.writeBuild")
                .define("writeBuild", true);
        FALL_DISTANCE_MIN = builder
                .comment("Minimum fall distance, in blocks, before a fall imprint is written.")
                .translation("mnemolith.configuration.fallDistanceMin")
                .defineInRange("fallDistanceMin", 4.0D, 1.0D, 40.0D);
        EXTRACTION_DURABILITY_COST = builder
                .comment("Durability the extraction needle loses on a successful extract.")
                .translation("mnemolith.configuration.extractionDurabilityCost")
                .defineInRange("extractionDurabilityCost", 2, 0, 32);
        EXTRACTION_COOLDOWN_TICKS = builder
                .comment("Ticks before the extraction needle can extract again. 0 disables the cooldown.")
                .translation("mnemolith.configuration.extractionCooldownTicks")
                .defineInRange("extractionCooldownTicks", 20, 0, 200);
        FAILURE_PRESSURE_SPIKE = builder
                .comment("Instability added to the composition reel's chunk when a formula fails. A moment replicant is asked only if the chunk is then overloaded or fractured.")
                .translation("mnemolith.configuration.failurePressureSpike")
                .defineInRange("failurePressureSpike", 18, 0, 100);
        INSTABILITY_DECAY = builder
                .comment("Instability removed from a player's chunk on each decay pulse. 0 disables cooling. Loud imprints stay until extracted.")
                .translation("mnemolith.configuration.instabilityDecay")
                .defineInRange("instabilityDecay", 1, 0, 20);
        INSTABILITY_DECAY_TICKS = builder
                .comment("Ticks between instability and quiet-imprint pulses while a player is in the chunk. 0 disables the pulse.")
                .translation("mnemolith.configuration.instabilityDecayTicks")
                .defineInRange("instabilityDecayTicks", 200, 0, 20_000);
        QUIET_FADE_TICKS = builder
                .comment("Game ticks before the oldest build, redstone, or path imprint in a visited chunk can fade. One fades per pulse, and one may fade when the chunk loads. 0 disables the fade. Deaths, explosions, falls, fire, silence, and player imprints stay until extracted.")
                .translation("mnemolith.configuration.quietFadeTicks")
                .defineInRange("quietFadeTicks", 6000, 0, 72_000);
        VEIN_SHIMMER_TICKS = builder
                .comment("Ticks between vein particle repeats while a held lens snapshot has not changed. 0 repeats only when the snapshot is new. A changed chunk still shimmers immediately.")
                .translation("mnemolith.configuration.veinShimmerTicks")
                .defineInRange("veinShimmerTicks", 40, 0, 200);
        SATURATED_THRESHOLD = builder
                .comment("Pressure at which a chunk becomes saturated. Multiplied by recollectionStormThreshold.")
                .translation("mnemolith.configuration.saturatedThreshold")
                .defineInRange("saturatedThreshold", 20, 1, 10_000);
        OVERLOADED_THRESHOLD = builder
                .comment("Pressure at which a chunk becomes overloaded. Multiplied by recollectionStormThreshold.")
                .translation("mnemolith.configuration.overloadedThreshold")
                .defineInRange("overloadedThreshold", 50, 1, 10_000);
        FRACTURE_THRESHOLD = builder
                .comment("Pressure at which a chunk fractures. Fracture is logged and can spawn a moment replicant.")
                .translation("mnemolith.configuration.fractureThreshold")
                .defineInRange("fractureThreshold", 80, 1, 10_000);
        MUTE_RADIUS_CHUNKS = builder
                .comment("Chebyshev radius, in chunks, of loaded chunks where a mute stone blocks imprint writes. 0 is the stone's own chunk.")
                .translation("mnemolith.configuration.muteRadiusChunks")
                .defineInRange("muteRadiusChunks", 0, 0, 2);
        CATALOG_ENABLED = builder
                .comment("Whether a catalog fragment opens the discovery catalog.")
                .translation("mnemolith.configuration.catalogEnabled")
                .define("catalogEnabled", true);
        DISCOVERY_HINTS = builder
                .comment("Whether the catalog and reel may show how many stable patterns are still unread. They never list an unread pattern.")
                .translation("mnemolith.configuration.discoveryHints")
                .define("discoveryHints", true);
        builder.pop();

        builder.comment("Spawn gates, damage, and the archivist steal cooldown. Eggs and /mnemolith spawn ignore the weight and pressure gates.")
                .translation("mnemolith.configuration.mobs")
                .push("mobs");
        ECHO_STRIDER_ENABLED = builder
                .comment("Whether echo striders may spawn from pressure and whether their AI runs.")
                .translation("mnemolith.configuration.echoStriderEnabled")
                .define("echoStriderEnabled", true);
        ARCHIVIST_ENABLED = builder
                .comment("Whether archivists may spawn from pressure and whether they steal slips.")
                .translation("mnemolith.configuration.archivistEnabled")
                .define("archivistEnabled", true);
        MOMENT_REPLICANT_ENABLED = builder
                .comment("Whether moment replicants may spawn from fracture, an overloaded composition failure, or natural pressure.")
                .translation("mnemolith.configuration.momentReplicantEnabled")
                .define("momentReplicantEnabled", true);
        ECHO_STRIDER_DAMAGE = builder
                .comment("Attack damage of an echo strider charge.")
                .translation("mnemolith.configuration.echoStriderDamage")
                .defineInRange("echoStriderDamage", 4.0D, 0.0D, 40.0D);
        ARCHIVIST_DAMAGE = builder
                .comment("Attack damage of an archivist. Theft is the threat; this is only a shove.")
                .translation("mnemolith.configuration.archivistDamage")
                .defineInRange("archivistDamage", 2.0D, 0.0D, 40.0D);
        REPLICANT_DAMAGE = builder
                .comment("Attack damage when a moment replicant copies a melee hit.")
                .translation("mnemolith.configuration.replicantDamage")
                .defineInRange("replicantDamage", 5.0D, 0.0D, 40.0D);
        ARCHIVIST_STEAL_COOLDOWN = builder
                .comment("Ticks an archivist waits after a successful theft before it can steal again.")
                .translation("mnemolith.configuration.archivistStealCooldown")
                .defineInRange("archivistStealCooldown", 300, 20, 20_000);
        ECHO_STRIDER_SPAWN_WEIGHT = builder
                .comment("Chance, out of 100, that a natural echo strider spawn attempt is kept. 0 disables natural spawns.")
                .translation("mnemolith.configuration.echoStriderSpawnWeight")
                .defineInRange("echoStriderSpawnWeight", 35, 0, 100);
        ARCHIVIST_SPAWN_WEIGHT = builder
                .comment("Chance, out of 100, that a natural archivist spawn attempt is kept. 0 disables natural spawns.")
                .translation("mnemolith.configuration.archivistSpawnWeight")
                .defineInRange("archivistSpawnWeight", 25, 0, 100);
        REPLICANT_SPAWN_WEIGHT = builder
                .comment("Chance, out of 100, that a natural moment replicant spawn attempt is kept. 0 disables natural spawns.")
                .translation("mnemolith.configuration.replicantSpawnWeight")
                .defineInRange("replicantSpawnWeight", 12, 0, 100);
        ECHO_STRIDER_MIN_PRESSURE = builder
                .comment("Minimum cached pressure before an echo strider can spawn naturally. Defaults to the saturated band.")
                .translation("mnemolith.configuration.echoStriderMinPressure")
                .defineInRange("echoStriderMinPressure", 20, 0, 10_000);
        ARCHIVIST_MIN_PRESSURE = builder
                .comment("Minimum cached pressure before an archivist can spawn naturally. Defaults to the overloaded band.")
                .translation("mnemolith.configuration.archivistMinPressure")
                .defineInRange("archivistMinPressure", 50, 0, 10_000);
        REPLICANT_MIN_PRESSURE = builder
                .comment("Minimum cached pressure before a moment replicant can spawn naturally. Defaults to the fracture band.")
                .translation("mnemolith.configuration.replicantMinPressure")
                .defineInRange("replicantMinPressure", 80, 0, 10_000);
        SENSOR_INTERVAL = builder
                .comment("Ticks between archivist resonator scans, flee pressure scans, and idle strider repaths. A charge still aims every tick. 1 checks every tick.")
                .translation("mnemolith.configuration.sensorInterval")
                .defineInRange("sensorInterval", 10, 1, 100);
        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {}
}
