package com.mnemolith.worldgen.hollows;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Faded;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Puts faded on the turf of Memory Hollows near players. Not the vanilla natural spawner: that one picks a random
 * height in the column (so a hollow's caves, which are the same 3D biome, win almost every roll) and stops at the
 * monster cap, which the caves around a hollow usually fill. Here: every {@link #PERIOD} ticks, for each player,
 * {@code fadedSpawnWeight} out of 100 rolls pick one surface spot 20 to 40 blocks away; the spot must pass
 * {@link MobSpawns#fadedSpotOk} (hollows, open surface, fewer than 3 faded within 32 blocks), stand on solid ground
 * with room, and have no player within 16 blocks. Peaceful and the spawn_mobs / spawn_monsters rules turn it off.
 * Faded spawned here are ordinary monsters and despawn as monsters do when nobody is near.
 */
public final class FadedSpawner {
    public static final int PERIOD = 100;
    public static final int MIN_DISTANCE = 20;
    public static final int MAX_DISTANCE = 40;
    public static final double PLAYER_CLEARANCE = 16.0D;
    private static long attempts;
    private static long spawned;

    private FadedSpawner() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD != 13) {
            return;
        }
        int weight = CommonConfig.FADED_SPAWN_WEIGHT.get();
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (weight <= 0 || level == null || level.getDifficulty() == Difficulty.PEACEFUL || !level.isSpawningMonsters()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && random.nextInt(100) < weight) {
                trySpawnNear(level, player.blockPosition(), random);
            }
        }
    }

    /** One roll around {@code origin}: a random surface spot in the ring, then {@link #trySpawnAt}. */
    public static @Nullable Faded trySpawnNear(ServerLevel level, BlockPos origin, RandomSource random) {
        attempts++;
        float angle = random.nextFloat() * Mth.TWO_PI;
        int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
        int x = origin.getX() + Mth.floor(Mth.cos(angle) * distance);
        int z = origin.getZ() + Mth.floor(Mth.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, origin.getY(), z))) {
            return null;
        }
        return trySpawnAt(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z));
    }

    /** Spawns a faded at {@code pos} (feet) if the spot is good. */
    public static @Nullable Faded trySpawnAt(ServerLevel level, BlockPos pos) {
        var type = ModEntities.FADED.get();
        if (!MobSpawns.fadedSpotOk(level, pos)
                || level.getNearestPlayer(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, PLAYER_CLEARANCE, false) != null
                || !SpawnPlacements.isSpawnPositionOk(type, level, pos)
                || !level.noCollision(type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))) {
            return null;
        }
        Faded faded = type.spawn(level, pos, EntitySpawnReason.NATURAL);
        if (faded != null) {
            spawned++;
        }
        return faded;
    }

    /** Rolls that reached the spot check, and faded spawned, since the server started (QA, performance notes). */
    public static long attempts() {
        return attempts;
    }

    public static long spawnedCount() {
        return spawned;
    }
}
