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
import net.minecraft.world.level.ChunkPos;
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

    /**
     * Faded keep to Memory Hollows, day or night, at most {@link #FADED_LOCAL_CAP} within {@link #FADED_CAP_RADIUS}
     * blocks; {@code fadedSpawnWeight} out of 100 attempts are kept.
     */
    public static boolean allowFaded(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        int weight = com.mnemolith.config.CommonConfig.FADED_SPAWN_WEIGHT.get();
        if (weight <= 0 || random.nextInt(100) >= weight || !(level instanceof ServerLevel server)) {
            return false;
        }
        return fadedSpotOk(server, pos);
    }

    public static final int FADED_LOCAL_CAP = 3;
    public static final double FADED_CAP_RADIUS = 32.0D;

    /**
     * The biome, surface and local-cap half of {@link #allowFaded}, without the random gate (QA). Biomes are 3D, so
     * the caves under a hollow are Memory Hollows too; faded walk the turf, not the caves, so the spot must be at the
     * open surface (no solid block above it, leaves aside).
     */
    public static boolean fadedSpotOk(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ())
                || !com.mnemolith.worldgen.hollows.Hollows.is(level.getBiome(pos))) {
            return false;
        }
        return level.getEntitiesOfClass(com.mnemolith.entity.mob.Faded.class, new AABB(pos).inflate(FADED_CAP_RADIUS)).size() < FADED_LOCAL_CAP;
    }

    /** A calm chunk: mites keep to quiet ground. */
    public static boolean allowCalm(ServerLevelAccessor level, BlockPos pos, RandomSource random, int weight) {
        return allowBelow(level, pos, random, weight, 20);
    }

    /** Pressure strictly under {@code ceiling}. */
    public static boolean allowBelow(ServerLevelAccessor level, BlockPos pos, RandomSource random, int weight, int ceiling) {
        if (weight <= 0 || random.nextInt(100) >= weight || !(level instanceof ServerLevel server)) {
            return false;
        }
        if (LoadedChunkMemory.isMuted(server, pos)) {
            return false;
        }
        LevelChunk chunk = server.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        int pressure = memory == null ? 0 : memory.cachedPressure();
        return pressure < ceiling;
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
        if (replicantNear(level, pos)) {
            return;
        }
        MomentReplicant replicant = ModEntities.MOMENT_REPLICANT.get().spawn(level, pos, EntitySpawnReason.EVENT);
        if (replicant == null) {
            replicant = ModEntities.MOMENT_REPLICANT.get().spawn(level, pos.above(), EntitySpawnReason.EVENT);
        }
        if (replicant != null) {
            BlockPos at = replicant.blockPosition();
            Mnemolith.LOGGER.debug("Mnemolith replicant spawn at {},{},{}", at.getX(), at.getY(), at.getZ());
        }
    }

    /** True when a replicant is already within 24 blocks or anywhere in this chunk's column. */
    public static boolean replicantNear(ServerLevel level, BlockPos pos) {
        if (!level.getEntitiesOfClass(MomentReplicant.class, new AABB(pos).inflate(MobTuning.REPLICANT_CLEARANCE)).isEmpty()) {
            return true;
        }
        ChunkPos chunk = ChunkPos.containing(pos);
        AABB column = new AABB(
                chunk.getMinBlockX(),
                level.getMinY(),
                chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1.0D,
                level.getMaxY(),
                chunk.getMaxBlockZ() + 1.0D);
        return !level.getEntitiesOfClass(MomentReplicant.class, column).isEmpty();
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
            case "ledger_mite" -> summon(ModEntities.LEDGER_MITE.get(), level, pos);
            case "kin_witness" -> summon(ModEntities.KIN_WITNESS.get(), level, pos);
            case "fracture_stalker" -> summon(ModEntities.FRACTURE_STALKER.get(), level, pos);
            case "faded" -> summon(ModEntities.FADED.get(), level, pos);
            case "pleading_chair" -> com.mnemolith.entity.PleadingChair.summon(level, pos);
            case "silence_mirror" -> summonSilenceMirror(level, pos);
            case "archive_guardian" -> com.mnemolith.echo.storm.ArchiveShrines.summon(level, pos);
            default -> null;
        };
    }

    private static Entity summonSilenceMirror(ServerLevel level, BlockPos pos) {
        var mirror = summon(ModEntities.SILENCE_MIRROR.get(), level, pos);
        if (mirror instanceof com.mnemolith.entity.echo.ScarEntity scar) {
            scar.setup(1 << com.mnemolith.echo.graft.Temper.HUSHED.id(), 3, pos);
        }
        return mirror;
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
