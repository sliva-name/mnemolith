package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.composition.Composition;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.ai.CopiedActionKind;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.entity.ai.PathLedger;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.network.PressureSync;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.ChunkState;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.ModFeatures;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import com.mojang.brigadier.context.CommandContext;

/** {@code /mnemolith inspect}, {@code smoke}, {@code spawn}, {@code mobs}, and {@code worldgen}. */
public final class MnemolithCommands {
    private static final double SMOKE_FALL_DISTANCE = 5.0D;

    private MnemolithCommands() {}

    public static int inspect(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        boolean muted = LoadedChunkMemory.isMuted(level, pos);
        int pressure = memory == null ? 0 : memory.cachedPressure();
        PressureBand band = MemoryPressure.band(pressure);
        ChunkState state = LoadedChunkMemory.stateOf(memory, muted);
        int count = memory == null ? 0 : memory.imprintCount();
        String tags = memory == null ? "-" : memory.tags().toString();
        source.sendSuccess(() -> Component.translatable(
                "mnemolith.command.inspect",
                pressure,
                Component.translatable(band.translationKey()),
                state.name().toLowerCase(),
                count,
                tags), false);
        return pressure;
    }

    public static int smoke(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        BlockPos pos = BlockPos.containing(position);
        ServerPlayer player = source.getPlayer();
        LevelChunk chunk = level.getChunkAt(pos);
        LoadedChunkMemory.clear(chunk);
        BlockPos quiet = pos.offset(16, 0, 0);
        LoadedChunkMemory.clear(level.getChunkAt(quiet));
        composePair(level, quiet, player, ImprintTag.BUILD, ImprintTag.BUILD);

        LivingEntity subject = EntityTypes.CHICKEN.spawn(level, pos.above(), EntitySpawnReason.EVENT);
        if (subject != null) {
            subject.causeFallDamage(SMOKE_FALL_DISTANCE, 1.0F, level.damageSources().fall());
        }
        level.explode(null, position.x, position.y, position.z, 1.0F, Level.ExplosionInteraction.NONE);
        if (subject != null && subject.isAlive()) {
            subject.kill(level);
        }

        chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        int pressure = memory == null ? 0 : memory.cachedPressure();

        level.setBlock(pos, ModBlocks.MUTE_STONE.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        boolean muted = LoadedChunkMemory.isMuted(level, pos);
        boolean writeBlocked = !ImprintWriter.tryWrite(level, pos, ImprintTag.BUILD, null, false);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        ImprintWriter.extract(level, pos, player);

        int compose = 0;
        if (composePair(level, pos, player, ImprintTag.DEATH, ImprintTag.SILENCE)) {
            compose++;
        }
        if (composePair(level, pos, player, ImprintTag.FIRE, ImprintTag.BUILD)) {
            compose++;
        }
        if (composePair(level, pos, player, ImprintTag.FALL, ImprintTag.PLAYER)) {
            compose++;
        }
        composePair(level, pos, player, ImprintTag.BUILD, ImprintTag.BUILD);

        PressureBand smokeBand = MemoryPressure.band(pressure);
        Mnemolith.LOGGER.info("Mnemolith smoke pressure={} band={} muted={} writeBlocked={} compose={}", pressure, smokeBand, muted, writeBlocked, compose);
        int reported = compose;
        boolean reportedMuted = muted;
        boolean reportedBlocked = writeBlocked;
        source.sendSuccess(() -> Component.translatable("mnemolith.command.smoke", pressure, reportedMuted, reportedBlocked, reported), true);
        return compose;
    }

    private static boolean composePair(ServerLevel level, BlockPos pos, ServerPlayer player, ImprintTag first, ImprintTag second) {
        SimpleContainer container = new SimpleContainer(3);
        container.setItem(0, ImprintSlips.of(first, pos));
        container.setItem(1, ImprintSlips.of(second, pos));
        return Composition.compose(level, pos, player, container).success();
    }

    public static int spawn(CommandContext<CommandSourceStack> context, String name) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Entity entity = MobSpawns.summonNamed(level, pos, name);
        if (entity == null) {
            source.sendSuccess(() -> Component.translatable("mnemolith.command.spawn_fail", name), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("mnemolith.command.spawn", name), true);
        return 1;
    }

    public static int mobs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        EchoStrider strider = MobSpawns.summonStrider(level, pos);
        Archivist archivist = MobSpawns.summonArchivist(level, pos.relative(Direction.EAST, 2));
        MomentReplicant replicant = MobSpawns.summonReplicant(level, pos.relative(Direction.WEST, 2));
        boolean striderOk = false;
        if (strider != null) {
            strider.beginCharge();
            striderOk = true;
        }
        boolean stole = false;
        if (archivist != null) {
            SimpleContainer container = new SimpleContainer(1);
            container.setItem(0, ImprintSlips.of(ImprintTag.DEATH, pos));
            stole = archivist.snatch(level, container, null, true);
        }
        boolean replicantOk = false;
        if (replicant != null) {
            replicant.beginTelegraph(CopiedActionKind.MELEE, null);
            replicantOk = true;
        }
        Mnemolith.LOGGER.info("Mnemolith mobs strider={} archivistStole={} replicant={}", striderOk, stole, replicantOk);
        boolean reportedStrider = striderOk;
        boolean reportedStole = stole;
        boolean reportedReplicant = replicantOk;
        source.sendSuccess(() -> Component.translatable("mnemolith.command.mobs", reportedStrider, reportedStole, reportedReplicant), true);
        return (striderOk ? 1 : 0) + (stole ? 1 : 0) + (replicantOk ? 1 : 0);
    }

    public static int worldgen(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        RandomSource random = level.getRandom();
        boolean vein = ModFeatures.ARCHIVAL_VEIN.get().placeVein(level, pos.below(8), random, true);
        boolean pocket = ModFeatures.MUTE_POCKET.get().placePocket(level, pos, random, true);
        LevelChunk chunk = level.getChunkAt(pos.below(4));
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        boolean muted = memory != null && memory.hasMuteStone();
        int strata = memory == null ? 0 : memory.strataCount();
        Mnemolith.LOGGER.info("Mnemolith worldgen vein={} pocket={} muted={} strata={}", vein, pocket, muted, strata);
        source.sendSuccess(() -> Component.translatable("mnemolith.command.worldgen", vein, pocket, muted, strata), true);
        return (vein ? 1 : 0) + (pocket ? 1 : 0);
    }

    /**
     * Times the write path, a throttled burst, pressure scoring, a lens walk, and strider sensor scans.
     * The chunk is beside the command source so {@code smoke} still owns the source chunk.
     */
    public static int perf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition()).offset(48, 0, 0);
        LevelChunk chunk = level.getChunkAt(pos);
        LoadedChunkMemory.clear(chunk);
        ImprintWriter.tryWrite(level, pos, ImprintTag.PATH, null, false);
        ChunkMemory warmed = LoadedChunkMemory.existing(chunk);
        if (warmed != null) {
            MemoryPressure.score(warmed);
        }
        PressureSync.timedPoll(level, pos);
        PathLedger.nearestImprint(level, pos, null);
        LoadedChunkMemory.clear(chunk);

        final int pathWrites = 32;
        long pathStart = System.nanoTime();
        int pathAccepted = 0;
        for (int i = 0; i < pathWrites; i++) {
            if (ImprintWriter.tryWrite(level, pos, ImprintTag.PATH, null, false)) {
                pathAccepted++;
            }
        }
        long pathNs = System.nanoTime() - pathStart;

        LoadedChunkMemory.clear(level.getChunkAt(pos));
        final int buildAttempts = 128;
        long buildStart = System.nanoTime();
        int buildAccepted = 0;
        for (int i = 0; i < buildAttempts; i++) {
            if (ImprintWriter.tryWrite(level, pos, ImprintTag.BUILD, null, true)) {
                buildAccepted++;
            }
        }
        long buildNs = System.nanoTime() - buildStart;

        LoadedChunkMemory.clear(level.getChunkAt(pos));
        ImprintTag[] tags = ImprintTag.values();
        for (int i = 0; i < 8; i++) {
            ImprintWriter.tryWrite(level, pos, tags[i], null, false);
        }
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        final int scoreCalls = 1000;
        long scoreStart = System.nanoTime();
        int score = 0;
        if (memory != null) {
            for (int i = 0; i < scoreCalls; i++) {
                score = MemoryPressure.score(memory);
            }
        }
        long scoreNs = System.nanoTime() - scoreStart;

        long syncStart = System.nanoTime();
        int syncWalks = 0;
        int syncSkipped = 0;
        for (int i = 0; i < 2; i++) {
            if (PressureSync.timedPoll(level, pos) == 0) {
                syncSkipped++;
            } else {
                syncWalks++;
            }
        }
        long syncNs = System.nanoTime() - syncStart;

        final int sensorCalls = 200;
        long sensorStart = System.nanoTime();
        for (int i = 0; i < sensorCalls; i++) {
            PathLedger.nearestImprint(level, pos, null);
            PathLedger.higherPressure(level, pos);
        }
        long sensorNs = System.nanoTime() - sensorStart;

        int reportedScore = score;
        long reportedPathNs = pathNs;
        long reportedBuildNs = buildNs;
        long reportedScoreNs = scoreNs;
        long reportedSyncNs = syncNs;
        int reportedSkipped = syncSkipped;
        long reportedSensorNs = sensorNs;
        Mnemolith.LOGGER.info(
                "Mnemolith perf pathWrites={} pathAccepted={} pathNs={} buildAttempts={} buildAccepted={} buildNs={} scoreCalls={} scoreNs={} score={} syncWalks={} syncSkipped={} syncNs={} sensorCalls={} sensorNs={}",
                pathWrites,
                pathAccepted,
                pathNs,
                buildAttempts,
                buildAccepted,
                buildNs,
                scoreCalls,
                scoreNs,
                reportedScore,
                syncWalks,
                syncSkipped,
                syncNs,
                sensorCalls,
                sensorNs);
        source.sendSuccess(() -> Component.translatable(
                "mnemolith.command.perf",
                reportedPathNs,
                reportedBuildNs,
                reportedScoreNs,
                reportedSyncNs,
                reportedSkipped,
                reportedSensorNs), true);
        return score;
    }
}
