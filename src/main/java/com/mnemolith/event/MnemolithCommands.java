package com.mnemolith.event;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.CompositionReelBlockEntity;
import com.mnemolith.content.composition.Composition;
import com.mnemolith.content.composition.CompositionFormula;
import com.mnemolith.content.menu.CompositionMenu;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.ai.CopiedActionKind;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.entity.ModEntities;
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
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
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

/** {@code /mnemolith inspect}, {@code smoke}, {@code mpsmoke}, {@code spawn}, {@code mobs}, {@code worldgen}, and {@code perf}. */
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

    /**
     * Two simulated players on this server. It does not open a second client.
     * The chunk is 96 blocks from the source so {@code smoke} and {@code perf} keep their chunks.
     */
    public static int mpsmoke(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition()).offset(96, 0, 0);
        FakePlayer first = fake(level, "MnemolithA", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        FakePlayer second = fake(level, "MnemolithB", UUID.fromString("22222222-2222-2222-2222-222222222222"));
        first.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        second.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        resetPlayer(first);
        resetPlayer(second);

        LevelChunk chunk = level.getChunkAt(pos);
        LoadedChunkMemory.clear(chunk);
        discardReplicants(level, pos);
        ImprintWriter.tryWrite(level, pos, ImprintTag.EXPLOSION, null, false);
        int bandA = PressureSync.originBand(level, pos);
        int bandB = PressureSync.originBand(level, pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        int serverBand = memory == null ? -1 : MemoryPressure.band(memory.cachedPressure()).ordinal();
        boolean sameBand = bandA == bandB && bandA == serverBand && bandA == PressureBand.SATURATED.ordinal();

        ImprintWriter.extract(level, pos, first);
        Discovery discoveryA = first.getData(ModAttachments.DISCOVERY.get());
        Discovery discoveryB = second.getData(ModAttachments.DISCOVERY.get());
        boolean discoveryIsolated = discoveryA.hasTag(ImprintTag.EXPLOSION) && discoveryB.tags() == 0 && discoveryB.formulas() == 0;
        resetPlayer(first);
        first.setData(ModAttachments.DISCOVERY.get(), discoveryA);

        boolean steal = archivistPrefersContainer(level, first, pos.above(2));
        boolean reel = separateReels(level, first, second, pos.above(4), pos.above(4).offset(2, 0, 0));
        discoveryA = first.getData(ModAttachments.DISCOVERY.get());
        discoveryB = second.getData(ModAttachments.DISCOVERY.get());
        discoveryIsolated = discoveryIsolated && discoveryA.hasFormula(CompositionFormula.UNRECORDED.ordinal()) && discoveryB.formulas() == 0;

        level.setBlock(pos.above(), ModBlocks.MUTE_STONE.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        boolean muteBlocks = LoadedChunkMemory.isMuted(level, pos)
                && !ImprintWriter.tryWrite(level, pos, ImprintTag.BUILD, second.getUUID(), false);
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        LoadedChunkMemory.clear(level.getChunkAt(pos));
        discardReplicants(level, pos);
        ImprintWriter.tryWrite(level, pos, ImprintTag.DEATH, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.EXPLOSION, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.FALL, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.FIRE, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.SILENCE, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.PLAYER, null, false);
        int spawned = replicantCount(level, pos);
        composePair(level, pos, first, ImprintTag.BUILD, ImprintTag.BUILD);
        int afterFail = replicantCount(level, pos);
        MobSpawns.trySpawnReplicant(level, pos.above());
        int afterAsk = replicantCount(level, pos);
        boolean replicants = spawned == 1 && afterFail == 1 && afterAsk == 1;
        discardReplicants(level, pos);

        boolean guarded = composeGuarded(level, first, pos.above(6));
        discardReplicants(level, pos);
        first.containerMenu = first.inventoryMenu;
        second.containerMenu = second.inventoryMenu;

        Mnemolith.LOGGER.info(
                "Mnemolith mpsmoke sameBand={} discoveryIsolated={} steal={} reel={} muteBlocks={} replicants={} guarded={}",
                sameBand,
                discoveryIsolated,
                steal,
                reel,
                muteBlocks,
                replicants,
                guarded);
        boolean reportedBand = sameBand;
        boolean reportedDiscovery = discoveryIsolated;
        boolean reportedSteal = steal;
        boolean reportedReel = reel;
        boolean reportedMute = muteBlocks;
        boolean reportedReplicants = replicants;
        boolean reportedGuarded = guarded;
        source.sendSuccess(() -> Component.translatable(
                "mnemolith.command.mpsmoke",
                reportedBand,
                reportedDiscovery,
                reportedSteal,
                reportedReel,
                reportedMute,
                reportedReplicants,
                reportedGuarded), true);
        int passed = 0;
        if (sameBand) {
            passed++;
        }
        if (discoveryIsolated) {
            passed++;
        }
        if (steal) {
            passed++;
        }
        if (reel) {
            passed++;
        }
        if (muteBlocks) {
            passed++;
        }
        if (replicants) {
            passed++;
        }
        if (guarded) {
            passed++;
        }
        return passed;
    }

    private static FakePlayer fake(ServerLevel level, String name, UUID id) {
        return FakePlayerFactory.get(level, new GameProfile(id, name));
    }

    private static void resetPlayer(ServerPlayer player) {
        player.getInventory().clearContent();
        player.containerMenu = player.inventoryMenu;
        player.setData(ModAttachments.DISCOVERY.get(), new Discovery());
    }

    private static boolean archivistPrefersContainer(ServerLevel level, ServerPlayer player, BlockPos reelPos) {
        CompositionReelBlockEntity reel = placeReel(level, reelPos);
        if (reel == null) {
            return false;
        }
        player.setPos(reelPos.getX() + 0.5D, reelPos.getY(), reelPos.getZ() + 0.5D);
        player.getInventory().clearContent();
        player.getInventory().add(ImprintSlips.of(ImprintTag.DEATH, reelPos));
        reel.setItem(0, ImprintSlips.of(ImprintTag.BUILD, reelPos));
        CompositionMenu menu = new CompositionMenu(1, player.getInventory(), reel);
        Archivist archivist = new Archivist(ModEntities.ARCHIVIST.get(), level);
        archivist.setPos(reelPos.getX() + 0.5D, reelPos.getY(), reelPos.getZ() + 0.5D);
        boolean stole = archivist.snatchMenu(player, menu, true);
        boolean chestTaken = reel.getItem(0).isEmpty();
        boolean pocketKept = holdsTag(player, ImprintTag.DEATH);
        level.setBlock(reelPos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        return stole && chestTaken && pocketKept;
    }

    private static boolean separateReels(ServerLevel level, ServerPlayer first, ServerPlayer second, BlockPos leftPos, BlockPos rightPos) {
        CompositionReelBlockEntity left = placeReel(level, leftPos);
        CompositionReelBlockEntity right = placeReel(level, rightPos);
        if (left == null || right == null) {
            return false;
        }
        first.setPos(leftPos.getX() + 0.5D, leftPos.getY(), leftPos.getZ() + 0.5D);
        left.setItem(0, ImprintSlips.of(ImprintTag.DEATH, leftPos));
        left.setItem(1, ImprintSlips.of(ImprintTag.SILENCE, leftPos));
        right.setItem(0, ImprintSlips.of(ImprintTag.FIRE, rightPos));
        right.setItem(1, ImprintSlips.of(ImprintTag.BUILD, rightPos));
        CompositionMenu menu = new CompositionMenu(2, first.getInventory(), left);
        first.containerMenu = menu;
        boolean composed = menu.clickMenuButton(first, com.mnemolith.imprint.ImprintConstants.COMPOSE_BUTTON_ID);
        boolean otherUntouched = filled(right) == 2 && left.getItem(0).isEmpty() && left.getItem(1).isEmpty();
        second.containerMenu = second.inventoryMenu;
        level.setBlock(leftPos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(rightPos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        return composed && otherUntouched;
    }

    private static boolean composeGuarded(ServerLevel level, ServerPlayer player, BlockPos reelPos) {
        CompositionReelBlockEntity reel = placeReel(level, reelPos);
        if (reel == null) {
            return false;
        }
        player.setPos(reelPos.getX() + 0.5D, reelPos.getY(), reelPos.getZ() + 0.5D);
        reel.setItem(0, ImprintSlips.of(ImprintTag.BUILD, reelPos));
        reel.setItem(1, ImprintSlips.of(ImprintTag.BUILD, reelPos));
        CompositionMenu menu = new CompositionMenu(3, player.getInventory(), reel);
        player.containerMenu = player.inventoryMenu;
        boolean closed = !menu.clickMenuButton(player, com.mnemolith.imprint.ImprintConstants.COMPOSE_BUTTON_ID) && filled(reel) == 2;
        player.containerMenu = menu;
        boolean open = menu.clickMenuButton(player, com.mnemolith.imprint.ImprintConstants.COMPOSE_BUTTON_ID) && filled(reel) == 1;
        player.containerMenu = player.inventoryMenu;
        boolean afterClose = !menu.clickMenuButton(player, com.mnemolith.imprint.ImprintConstants.COMPOSE_BUTTON_ID) && filled(reel) == 1;
        level.setBlock(reelPos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        return closed && open && afterClose;
    }

    private static CompositionReelBlockEntity placeReel(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.COMPOSITION_REEL.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof CompositionReelBlockEntity reel) {
            return reel;
        }
        return null;
    }

    private static int filled(Container container) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static boolean holdsTag(ServerPlayer player, ImprintTag tag) {
        Container inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ImprintSlips.isSlip(stack)) {
                continue;
            }
            ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
            if (cast != null && cast.tag() == tag) {
                return true;
            }
        }
        return false;
    }

    private static int replicantCount(ServerLevel level, BlockPos pos) {
        return replicants(level, pos).size();
    }

    private static void discardReplicants(ServerLevel level, BlockPos pos) {
        for (MomentReplicant replicant : replicants(level, pos)) {
            replicant.discard();
        }
    }

    private static java.util.List<MomentReplicant> replicants(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = ChunkPos.containing(pos);
        AABB column = new AABB(
                chunk.getMinBlockX(),
                level.getMinY(),
                chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1.0D,
                level.getMaxY(),
                chunk.getMaxBlockZ() + 1.0D);
        return level.getEntitiesOfClass(MomentReplicant.class, column);
    }
}
