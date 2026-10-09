package com.mnemolith.command.qa;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.ArchiveVaultBlock;
import com.mnemolith.content.block.ArchiveVaultBlockEntity;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.job.JobStatus;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.echo.storm.RecollectionStorm;
import com.mnemolith.echo.storm.StormData;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.vault.ArchiveVaults;
import com.mnemolith.vault.VaultContents;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.config.CommonConfig;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * {@code /mnemolith debug} (operators, permission 2). Drives the states the checklist cannot reach by waiting:
 * any pressure band, a recollection storm and the Scar it raises, vault fill and leak, relay motes and a hop,
 * a chosen misfire, residue variants, a graft, a grown field. Nothing here runs unless an operator asks.
 */
public final class DebugCommands {
    private static final int STEP_CAP = 400;
    /** Gather (200) plus six waves (200 each), with room for a late merge. */
    private static final int SCAR_CAP = 1600;

    private DebugCommands() {}

    public static int pressureAmount(CommandContext<CommandSourceStack> context) {
        return pressure(context, context.getArgument("amount", Integer.class));
    }

    public static int pressureNamed(CommandContext<CommandSourceStack> context, String name) {
        int target = namedTarget(name);
        if (target < 0) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.pressure.bad", name));
            return 0;
        }
        return pressure(context, target);
    }

    /** Sets instability so the chunk's score lands on {@code target} (imprints already there are left in place). */
    public static int applyPressure(ServerLevel level, BlockPos pos, int target) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
        int cap = CommonConfig.PRESSURE_SOFT_CAP.get();
        int want = Math.max(0, Math.min(cap, target));
        int base = MemoryPressure.score(memory) - memory.instability();
        int instability = Math.max(0, Math.min(cap, want - Math.max(0, base)));
        int have = memory.instability();
        if (instability < have) {
            memory.coolInstability(have - instability);
            MemoryPressure.recompute(chunk, memory);
        } else if (instability > have) {
            ImprintWriter.spike(level, pos, instability - have);
        } else {
            MemoryPressure.recompute(chunk, memory);
        }
        return memory.cachedPressure();
    }

    private static int pressure(CommandContext<CommandSourceStack> context, int target) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        int score = applyPressure(level, pos, target);
        PressureBand band = MemoryPressure.band(score);
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.pressure", score, Component.translatable(band.translationKey())), true);
        return score;
    }

    public static int stormStart(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        applyPressure(level, pos, namedTarget("fracture"));
        RecollectionStorm existing = Storms.at(level, level.getChunkAt(pos).getPos());
        if (existing != null) {
            context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.storm.already", existing.id()), true);
            return 1;
        }
        RecollectionStorm storm = Storms.start(level, level.getChunkAt(pos).getPos(), "debug");
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.storm.start", storm.id()), true);
        return 1;
    }

    public static int stormStep(CommandContext<CommandSourceStack> context) {
        int ticks = Math.min(STEP_CAP, context.getArgument("ticks", Integer.class));
        return advance(context, ticks, false);
    }

    public static int stormStop(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        RecollectionStorm storm = Storms.at(level, level.getChunkAt(BlockPos.containing(context.getSource().getPosition())).getPos());
        if (storm == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.storm.none"));
            return 0;
        }
        Storms.finish(level, storm, Storms.End.PASSED);
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.storm.stop", storm.id()), true);
        return 1;
    }

    /**
     * The natural merge: three pinned storm residues survive the waves, and {@link Storms} raises the Scar.
     * Peaceful difficulty never spawns the boss, so a peaceful world is raised to easy for this call only.
     */
    public static int stormScar(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        boolean raised = false;
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            level.getServer().setDifficulty(Difficulty.EASY, true);
            raised = true;
        }
        applyPressure(level, pos, namedTarget("fracture"));
        RecollectionStorm storm = Storms.at(level, level.getChunkAt(pos).getPos());
        if (storm == null) {
            storm = Storms.start(level, level.getChunkAt(pos).getPos(), "debug");
        }
        seedResidues(level, pos, storm);
        int stepped = advanceExisting(level, storm, SCAR_CAP);
        Storms.Ended ended = Storms.lastEnd();
        String end = ended != null && ended.id() == storm.id() ? ended.end().name().toLowerCase(Locale.ROOT) : "open";
        boolean scar = !level.getEntitiesOfClass(ScarEntity.class, new AABB(pos).inflate(24.0D), ScarEntity::isAlive).isEmpty();
        boolean peaceful = raised;
        context.getSource().sendSuccess(() -> Component.translatable(scar ? "mnemolith.debug.storm.scar" : "mnemolith.debug.storm.scar_missed",
                stepped, end, peaceful ? Component.translatable("mnemolith.debug.storm.easy") : Component.literal("")), true);
        return scar ? 1 : 0;
    }

    public static int vaultFill(CommandContext<CommandSourceStack> context) {
        int count = 4;
        try {
            count = context.getArgument("count", Integer.class);
        } catch (IllegalArgumentException ignored) {
            // the no-argument form
        }
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = vaultAt(level, BlockPos.containing(context.getSource().getPosition()));
        if (!(level.getBlockEntity(pos) instanceof ArchiveVaultBlockEntity vault)) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.vault.none"));
            return 0;
        }
        int room = Math.max(0, ArchiveVaults.capacity() - vault.count());
        int added = 0;
        for (int i = 0; i < Math.min(count, room); i++) {
            Imprint imprint = new Imprint(ImprintTag.DEATH, 3, pos, Optional.empty(), Imprint.contextHash(ImprintTag.DEATH, pos, level.getGameTime() + i), level.getGameTime());
            if (ArchiveVaults.keep(level, pos, vault, imprint)) {
                added++;
            }
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(ArchiveVaultBlock.DRAWING) && !state.getValue(ArchiveVaultBlock.DRAWING)) {
            level.setBlock(pos, state.setValue(ArchiveVaultBlock.DRAWING, true), 3);
        }
        int held = vault.count();
        int kept = added;
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.vault.fill", kept, held, ArchiveVaults.capacity()), true);
        return held;
    }

    public static int vaultLeak(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.ARCHIVE_VAULT.get()) || held.get(ModDataComponents.VAULT_CONTENTS.get()) == null) {
            BlockPos pos = findBlock(level, player.blockPosition(), 8, state -> state.is(ModBlocks.ARCHIVE_VAULT.get()));
            List<Imprint> stored = pos != null && level.getBlockEntity(pos) instanceof ArchiveVaultBlockEntity vault ? vault.stored() : List.of();
            if (stored.isEmpty()) {
                stored = List.of(new Imprint(ImprintTag.DEATH, 3, player.blockPosition(), Optional.empty(), 1, level.getGameTime()));
            }
            ItemStack stack = new ItemStack(ModItems.ARCHIVE_VAULT.get());
            stack.set(ModDataComponents.VAULT_CONTENTS.get(), new VaultContents(stored));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), stack);
            held = player.getMainHandItem();
        }
        boolean leaked = ArchiveVaults.leak(level, player, held);
        if (!leaked) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.vault.none"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.vault.leak"), true);
        return 1;
    }

    public static int relayLink(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        java.util.List<EchoEntity> echoes = level.getEntitiesOfClass(EchoEntity.class, new AABB(pos).inflate(32.0D), EchoEntity::isAlive);
        echoes.sort(Comparator.comparingDouble(echo -> echo.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())));
        if (echoes.size() < 2) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.relay.none"));
            return 0;
        }
        com.mnemolith.echo.relay.EchoRelays.link(level, echoes.get(0), echoes.get(1));
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.relay.link"), true);
        return 1;
    }

    public static int relayThread(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        EchoEntity echo = nearest(level, BlockPos.containing(context.getSource().getPosition()), e -> e.relay() != null);
        if (echo == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.relay.none"));
            return 0;
        }
        com.mnemolith.echo.relay.EchoRelays.tick(level, echo);
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.relay.thread"), true);
        return 1;
    }

    public static int relayPossess(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        if (EchoPossession.isPossessing(player)) {
            context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.relay.possess", "already"), true);
            return 1;
        }
        EchoEntity echo = nearest(player.level(), player.blockPosition(), e -> e.isOwnedBy(player));
        if (echo == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.echo.none"));
            return 0;
        }
        EchoPossession.Result result = EchoPossession.possess(player, echo);
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.relay.possess", result.name().toLowerCase(Locale.ROOT)), true);
        return result == EchoPossession.Result.POSSESSED ? 1 : 0;
    }

    public static int relayHop(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        if (!EchoPossession.isPossessing(player)) {
            relayPossess(context);
        }
        com.mnemolith.echo.relay.EchoRelays.HopResult result = com.mnemolith.echo.relay.EchoRelays.hop(player);
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.relay.hop", result.name().toLowerCase(Locale.ROOT)), true);
        return result == com.mnemolith.echo.relay.EchoRelays.HopResult.HOPPED ? 1 : 0;
    }

    public static int misfire(CommandContext<CommandSourceStack> context, String kind) {
        EchoEntity echo = nearestEcho(context);
        if (echo == null) {
            return 0;
        }
        echo.job().notice(JobStatus.of(JobStatus.Kind.MISFIRE, kind), 200);
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.misfire", kind), true);
        return 1;
    }

    public static int residue(CommandContext<CommandSourceStack> context) {
        return residueNamed(context, context.getArgument("tag", String.class));
    }

    public static int residueNamed(CommandContext<CommandSourceStack> context, String name) {
        if ("observatory".equals(name)) {
            return observatory(context);
        }
        ImprintTag tag = tag(name);
        if (tag == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.residue.bad", name));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = Residues.airAbove(level, BlockPos.containing(context.getSource().getPosition()));
        boolean old = false;
        int strength = 3;
        try {
            strength = context.getArgument("strength", Integer.class);
        } catch (IllegalArgumentException ignored) {
            // default
        }
        ResidueEntity residue = Residues.spawn(level, pos, tag, strength, old);
        if (residue == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.residue.bad", name));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.residue", tag.getSerializedName(), residue.strength()), true);
        return 1;
    }

    public static int residueOld(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        ImprintTag tag = ImprintTag.FIRE;
        try {
            ImprintTag parsed = tag(context.getArgument("tag", String.class));
            if (parsed != null) {
                tag = parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // the bare form
        }
        BlockPos pos = Residues.airAbove(level, BlockPos.containing(context.getSource().getPosition()));
        ImprintTag spawned = tag;
        ResidueEntity residue = Residues.spawn(level, pos, spawned, 5, true);
        if (residue == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.residue.bad", spawned.getSerializedName()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.residue", spawned.getSerializedName(), residue.strength()), true);
        return 1;
    }

    public static int graft(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        EchoEntity echo = nearestEcho(context);
        if (player == null || echo == null) {
            return 0;
        }
        String name = context.getArgument("temper", String.class);
        ImprintTag tag = tag(name);
        Temper temper = tag == null ? null : Temper.of(tag);
        if (temper == null) {
            for (Temper candidate : Temper.values()) {
                if (candidate.name().equalsIgnoreCase(name)) {
                    temper = candidate;
                    tag = candidate.tag();
                }
            }
        }
        if (temper == null || tag == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.graft.bad", name));
            return 0;
        }
        ItemStack slip = ImprintSlips.of(tag);
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), slip);
        boolean ok = EchoGrafts.graft(player, echo, player.getMainHandItem());
        if (!ok) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.graft.bad", name));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.graft", echo.graftLine()), true);
        return 1;
    }

    public static int farmGrow(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        int grown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -2, -6), origin.offset(6, 2, 6))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                level.setBlock(pos, crop.getStateForAge(crop.getMaxAge()), 3);
                grown++;
            }
        }
        int count = grown;
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.farm.grow", count), true);
        return grown;
    }

    public static int farmShow(CommandContext<CommandSourceStack> context) {
        EchoEntity echo = nearestEcho(context);
        if (echo == null) {
            return 0;
        }
        int count = context.getArgument("count", Integer.class);
        echo.job().setStatus(new JobStatus(JobStatus.Kind.FARMING, "minecraft:wheat", count, 0));
        echo.job().notice(new JobStatus(JobStatus.Kind.FARMING, "minecraft:wheat", count, 0), 200);
        ServerLevel level = (ServerLevel) echo.level();
        BlockPos chest = findBlock(level, echo.blockPosition(), 8, state -> state.is(Blocks.CHEST));
        if (chest != null && level.getBlockEntity(chest) instanceof Container container) {
            container.setItem(0, new ItemStack(Items.WHEAT, Math.min(64, Math.max(1, count))));
        }
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.farm.show", count), true);
        return 1;
    }

    public static int lava(CommandContext<CommandSourceStack> context) {
        EchoEntity echo = nearestEcho(context);
        if (echo == null) {
            return 0;
        }
        ServerLevel level = (ServerLevel) echo.level();
        BlockPos at = echo.blockPosition();
        if (!level.getBlockState(at).isAir()) {
            at = at.above();
        }
        level.setBlock(at, Blocks.LAVA.defaultBlockState(), 3);
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.lava", echo.blockPosition().toShortString()), true);
        return 1;
    }

    public static int shardGraft(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        EchoEntity echo = nearestEcho(context);
        if (player == null || echo == null) {
            return 0;
        }
        ItemStack shard = Residues.shard(ImprintTag.DEATH, 4, echo.blockPosition(), player.level().getGameTime());
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), shard);
        boolean ok = EchoGrafts.graftShard(player, echo, player.getMainHandItem());
        if (!ok) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.shard.failed"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.shard.graft", echo.graftLine()), true);
        return 1;
    }

    public static int shardRelease(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        ItemStack shard = Residues.shard(ImprintTag.FIRE, 3, player.blockPosition(), level.getGameTime());
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), shard);
        BlockPos at = Residues.airAbove(level, player.blockPosition());
        ResidueEntity residue = Residues.release(level, player.getMainHandItem(), at);
        if (residue == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.residue.release_failed"));
            return 0;
        }
        boolean called = Storms.callByShard(level, residue, player);
        if (!called) {
            player.sendSystemMessage(Component.translatable("mnemolith.residue.released",
                    Component.translatable(residue.tag().translationKey()), residue.strength()), true);
        }
        player.getMainHandItem().consume(1, player);
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.shard.release"), true);
        return 1;
    }

    private static int observatory(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, ModBlocks.COMPOSITION_REEL.get().defaultBlockState(), 3);
        }
        LevelChunk chunk = level.getChunkAt(pos);
        LoadedChunkMemory.markObservatory(chunk);
        ResidueEntity seeded = Residues.trySeed(level, chunk);
        if (seeded == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.observatory.none"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.observatory", seeded.strength()), true);
        return 1;
    }

    private static int advance(CommandContext<CommandSourceStack> context, int ticks, boolean seed) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        RecollectionStorm storm = Storms.at(level, level.getChunkAt(pos).getPos());
        if (storm == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.storm.none"));
            return 0;
        }
        if (seed) {
            seedResidues(level, pos, storm);
        }
        int stepped = advanceExisting(level, storm, ticks);
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.storm.step", stepped, storm.phase().name().toLowerCase(Locale.ROOT)), true);
        return stepped;
    }

    private static int advanceExisting(ServerLevel level, RecollectionStorm storm, int ticks) {
        StormData data = StormData.get(level.getServer());
        int stepped = 0;
        for (; stepped < ticks && Storms.isActive(level, storm.id()); stepped++) {
            if (stepped % 40 == 0) {
                for (ResidueEntity residue : Storms.living(level, storm)) {
                    if (!residue.isPinned()) {
                        residue.pin(1_000_000);
                    }
                }
            }
            Storms.step(level, data, storm);
        }
        return stepped;
    }

    private static void seedResidues(ServerLevel level, BlockPos pos, RecollectionStorm storm) {
        ImprintTag[] tags = { ImprintTag.DEATH, ImprintTag.FIRE, ImprintTag.EXPLOSION };
        int have = Storms.living(level, storm).size();
        for (int i = have; i < tags.length; i++) {
            ResidueEntity residue = Residues.spawn(level, Residues.airAbove(level, pos.offset(i - 1, 0, 0)), tags[i], 3, false);
            if (residue != null) {
                residue.pin(1_000_000);
                Storms.adopt(storm, residue);
            }
        }
    }


    /** Writes {@code count} imprints of one tag where the source stands, so a storm has those memories to condense. */
    public static int imprint(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        String name = context.getArgument("tag", String.class);
        ImprintTag tag = tag(name);
        if (tag == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.residue.bad", name));
            return 0;
        }
        int count = context.getArgument("count", Integer.class);
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        int written = 0;
        for (int i = 0; i < count; i++) {
            if (ImprintWriter.write(level, pos, List.of(tag), null, false)) {
                written++;
            }
        }
        int done = written;
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.imprint", done, tag.getSerializedName()), true);
        return done;
    }

    /** Hands the nearest echo to an offline player so order keys and the needle can be checked against a stranger. */
    public static int stranger(CommandContext<CommandSourceStack> context) {
        EchoEntity echo = nearestEcho(context);
        if (echo == null) {
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        if (echo.ownerId() != null) {
            EchoRegistry.get(level.getServer()).remove(echo.ownerId(), echo.getUUID());
        }
        echo.setOwner(id, "Stranger", ResolvableProfile.createResolved(new GameProfile(id, "Stranger")));
        echo.setGeneration(EchoRegistry.get(level.getServer()).put(id, echo.getUUID()));
        context.getSource().sendSuccess(() -> Component.translatable("mnemolith.debug.stranger", echo.getDisplayName()), true);
        return 1;
    }

    private static BlockPos vaultAt(ServerLevel level, BlockPos origin) {
        BlockPos found = findBlock(level, origin, 8, state -> state.is(ModBlocks.ARCHIVE_VAULT.get()));
        if (found != null) {
            return found;
        }
        BlockPos place = level.getBlockState(origin).isAir() ? origin : origin.above();
        level.setBlock(place, ModBlocks.ARCHIVE_VAULT.get().defaultBlockState(), 3);
        return place;
    }

    private static BlockPos findBlock(ServerLevel level, BlockPos origin, int radius, java.util.function.Predicate<BlockState> test) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 2, radius))) {
            if (!test.test(level.getBlockState(pos))) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    private static EchoEntity nearestEcho(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        EchoEntity echo = nearest(level, BlockPos.containing(context.getSource().getPosition()), EchoEntity::isAlive);
        if (echo == null) {
            context.getSource().sendFailure(Component.translatable("mnemolith.debug.echo.none"));
        }
        return echo;
    }

    private static EchoEntity nearest(ServerLevel level, BlockPos pos, java.util.function.Predicate<EchoEntity> test) {
        return level.getEntitiesOfClass(EchoEntity.class, new AABB(pos).inflate(32.0D), test).stream()
                .min(Comparator.comparingDouble(echo -> echo.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())))
                .orElse(null);
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getPlayer() instanceof ServerPlayer player) {
            return player;
        }
        context.getSource().sendFailure(Component.translatable("mnemolith.debug.need_player"));
        return null;
    }

    private static ImprintTag tag(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        for (Temper temper : Temper.values()) {
            if (temper.name().equalsIgnoreCase(key)) {
                return temper.tag();
            }
        }
        for (ImprintTag tag : ImprintTag.values()) {
            if (tag.getSerializedName().equals(key) || tag.name().equalsIgnoreCase(key)) {
                return tag;
            }
        }
        return null;
    }

    /** The scaled threshold {@link MemoryPressure#band} uses, or -1. */
    private static int namedTarget(String name) {
        double scale = CommonConfig.RECOLLECTION_STORM_THRESHOLD.get();
        int cap = CommonConfig.PRESSURE_SOFT_CAP.get();
        return switch (name) {
            case "calm" -> 0;
            case "saturated" -> Math.min(cap, Math.max(1, (int) Math.round(CommonConfig.SATURATED_THRESHOLD.get() * scale)));
            case "overloaded" -> Math.min(cap, Math.max(1, (int) Math.round(CommonConfig.OVERLOADED_THRESHOLD.get() * scale)));
            case "fracture" -> Math.min(cap, Math.max(1, (int) Math.round(CommonConfig.FRACTURE_THRESHOLD.get() * scale)));
            default -> -1;
        };
    }
}
