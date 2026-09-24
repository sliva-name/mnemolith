package com.mnemolith.entity;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.WorldgenTuning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** Natural-spawn gates and the fracture / composition-failure replicant. */
public final class MobSpawns {
    private MobSpawns() {}

    public static boolean forced(EntitySpawnReason reason) {
        return reason == EntitySpawnReason.COMMAND
                || reason == EntitySpawnReason.SPAWN_ITEM_USE
                || reason == EntitySpawnReason.MOB_SUMMONED
                || reason == EntitySpawnReason.EVENT;
    }

    public static boolean allowStrider(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        if (level instanceof ServerLevel server && LoadedChunkMemory.isMuted(server, pos)) {
            return false;
        }
        int min = MobTuning.striderMinPressure();
        if (WorldgenTuning.striderPathBias() && hasPath(level, pos)) {
            min = Math.max(0, min - WorldgenTuning.PATH_RELIEF);
        }
        return allowNatural(level, pos, random, MobTuning.striderEnabled(), MobTuning.striderWeight(), min);
    }

    public static boolean allowArchivist(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        int min = MobTuning.archivistMinPressure();
        if (WorldgenTuning.archivistObservatoryBias() && level instanceof ServerLevel server
                && LoadedChunkMemory.observatoryNearby(server, pos, WorldgenTuning.OBSERVATORY_CHUNK_RADIUS)) {
            min = Math.max(0, min - WorldgenTuning.OBSERVATORY_RELIEF);
        }
        return allowNatural(level, pos, random, MobTuning.archivistEnabled(), MobTuning.archivistWeight(), min);
    }

    public static boolean allowNatural(ServerLevelAccessor level, BlockPos pos, RandomSource random, boolean enabled, int weight, int minPressure) {
        if (!enabled || weight <= 0 || random.nextInt(100) >= weight) {
            return false;
        }
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        LevelChunk chunk = server.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        int pressure = memory == null ? 0 : memory.cachedPressure();
        return pressure >= minPressure;
    }

    private static boolean hasPath(ServerLevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        ChunkMemory memory = LoadedChunkMemory.existing(server.getChunkAt(pos));
        if (memory == null) {
            return false;
        }
        for (Imprint imprint : memory.imprintsCopy()) {
            if (imprint.tag() == ImprintTag.PATH) {
                return true;
            }
        }
        return false;
    }

    public static void trySpawnReplicant(ServerLevel level, BlockPos pos) {
        if (!MobTuning.replicantEnabled()) {
            return;
        }
        AABB box = new AABB(pos).inflate(MobTuning.REPLICANT_CLEARANCE);
        if (!level.getEntitiesOfClass(MomentReplicant.class, box).isEmpty()) {
            return;
        }
        MomentReplicant replicant = ModEntities.MOMENT_REPLICANT.get().spawn(level, pos, EntitySpawnReason.EVENT);
        if (replicant == null) {
            replicant = ModEntities.MOMENT_REPLICANT.get().spawn(level, pos.above(), EntitySpawnReason.EVENT);
        }
        if (replicant != null) {
            BlockPos at = replicant.blockPosition();
            Mnemolith.LOGGER.info("Mnemolith replicant spawn at {},{},{}", at.getX(), at.getY(), at.getZ());
        }
    }

    public static <T extends Mob> T summon(EntityType<T> type, ServerLevel level, BlockPos pos) {
        T entity = type.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (entity == null) {
            entity = type.spawn(level, pos.above(), EntitySpawnReason.COMMAND);
        }
        return entity;
    }

    public static Entity summonNamed(ServerLevel level, BlockPos pos, String name) {
        return switch (name) {
            case "echo_strider" -> summon(ModEntities.ECHO_STRIDER.get(), level, pos);
            case "archivist" -> summon(ModEntities.ARCHIVIST.get(), level, pos);
            case "moment_replicant" -> summon(ModEntities.MOMENT_REPLICANT.get(), level, pos);
            default -> null;
        };
    }

    public static EchoStrider summonStrider(ServerLevel level, BlockPos pos) {
        return summon(ModEntities.ECHO_STRIDER.get(), level, pos);
    }

    public static Archivist summonArchivist(ServerLevel level, BlockPos pos) {
        return summon(ModEntities.ARCHIVIST.get(), level, pos);
    }

    public static MomentReplicant summonReplicant(ServerLevel level, BlockPos pos) {
        return summon(ModEntities.MOMENT_REPLICANT.get(), level, pos);
    }
}
