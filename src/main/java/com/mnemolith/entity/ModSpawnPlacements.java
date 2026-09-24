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
                        MobSpawns.forced(reason) || MobSpawns.allowNatural(level, pos, random, MobTuning.striderEnabled(), MobTuning.striderWeight(), MobTuning.striderMinPressure()),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                ModEntities.ARCHIVIST.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> MobSpawns.forced(reason) || MobSpawns.allowNatural(level, pos, random, MobTuning.archivistEnabled(), MobTuning.archivistWeight(), MobTuning.archivistMinPressure()),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                ModEntities.MOMENT_REPLICANT.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> MobSpawns.forced(reason) || MobSpawns.allowNatural(level, pos, random, MobTuning.replicantEnabled(), MobTuning.replicantWeight(), MobTuning.replicantMinPressure()),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
