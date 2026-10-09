package com.mnemolith.entity;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Ground placement. Natural attempts also ask the chunk's cached pressure. */
public final class ModSpawnPlacements {
    private ModSpawnPlacements() {}

    public static void onRegister(RegisterSpawnPlacementsEvent event) {
        event.register(
                ModEntities.ECHO_STRIDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (EntityType<com.mnemolith.entity.mob.EchoStrider> type, net.minecraft.world.level.ServerLevelAccessor level, EntitySpawnReason reason, net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) ->
                        MobSpawns.forced(reason) || MobSpawns.allowStrider(level, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                ModEntities.ARCHIVIST.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> MobSpawns.forced(reason) || MobSpawns.allowArchivist(level, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                ModEntities.MOMENT_REPLICANT.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> MobSpawns.forced(reason) || MobSpawns.allowNatural(level, pos, random, MobTuning.replicantEnabled(), MobTuning.replicantWeight(), MobTuning.replicantMinPressure()),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                ModEntities.LEDGER_MITE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> MobSpawns.forced(reason) || MobSpawns.allowCalm(level, pos, random, 12),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                ModEntities.KIN_WITNESS.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> MobSpawns.forced(reason) || MobSpawns.allowBelow(level, pos, random, 8, 50),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                ModEntities.FRACTURE_STALKER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> MobSpawns.forced(reason) || MobSpawns.allowNatural(level, pos, random, true, com.mnemolith.config.CommonConfig.STALKER_SPAWN_WEIGHT.get(), com.mnemolith.config.CommonConfig.STALKER_MIN_PRESSURE.get()),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                ModEntities.FADED.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> MobSpawns.forced(reason)
                        || (net.minecraft.world.entity.monster.Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)
                        && MobSpawns.allowFaded(level, pos, random)),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
