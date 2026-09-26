package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.*;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.CompositionReelBlockEntity;
import com.mnemolith.content.composition.CompositionFormula;
import com.mnemolith.content.menu.CompositionMenu;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.network.PressureSync;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import com.mojang.brigadier.context.CommandContext;

/** {@code /mnemolith mpsmoke}. Two simulated players on this server. */
public final class MultiplayerSmoke {
    private MultiplayerSmoke() {}

    /**
     * Two simulated players on this server. It does not open a second client.
     * The chunk is 96 blocks from the source so {@code smoke} and {@code perf} keep their chunks.
     */
    public static int mpsmoke(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition()).offset(96, 0, 0);
        // Entity-ticking for the whole smoke: the replicant cap counts replicants with an entity query, and a chunk
        // with no player near it hides them, so a second one would slip past the cap.
        tickColumn(level, pos);
        try {
            return smoke(source, level, pos);
        } finally {
            releaseColumn(level, net.minecraft.world.level.ChunkPos.containing(pos));
        }
    }

    private static int smoke(CommandSourceStack source, ServerLevel level, BlockPos pos) {
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

        level.setBlock(pos.above(), ModBlocks.MUTE_STONE.get().defaultBlockState(), Block.UPDATE_ALL);
        boolean muteBlocks = LoadedChunkMemory.isMuted(level, pos)
                && !ImprintWriter.tryWrite(level, pos, ImprintTag.BUILD, second.getUUID(), false);
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        clear(level, pos);
        discardReplicants(level, pos);
        ImprintWriter.tryWrite(level, pos, ImprintTag.DEATH, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.EXPLOSION, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.FALL, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.FIRE, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.SILENCE, null, false);
        ImprintWriter.tryWrite(level, pos, ImprintTag.PLAYER, null, false);
        int spawned = replicantCount(level, pos);
        compose(level, pos, first, ImprintTag.BUILD, ImprintTag.BUILD);
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
        return count(sameBand, discoveryIsolated, steal, reel, muteBlocks, replicants, guarded);
    }

    private static FakePlayer fake(ServerLevel level, String name, UUID id) {
        return FakePlayerFactory.get(level, new GameProfile(id, name));
    }

    private static void resetPlayer(ServerPlayer player) {
        reset(player);
        player.containerMenu = player.inventoryMenu;
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
        level.setBlock(reelPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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
        boolean composed = menu.clickMenuButton(first, ImprintConstants.COMPOSE_BUTTON_ID);
        boolean otherUntouched = filled(right) == 2 && left.getItem(0).isEmpty() && left.getItem(1).isEmpty();
        second.containerMenu = second.inventoryMenu;
        level.setBlock(leftPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(rightPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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
        boolean closed = !menu.clickMenuButton(player, ImprintConstants.COMPOSE_BUTTON_ID) && filled(reel) == 2;
        player.containerMenu = menu;
        boolean open = menu.clickMenuButton(player, ImprintConstants.COMPOSE_BUTTON_ID) && filled(reel) == 1;
        player.containerMenu = player.inventoryMenu;
        boolean afterClose = !menu.clickMenuButton(player, ImprintConstants.COMPOSE_BUTTON_ID) && filled(reel) == 1;
        level.setBlock(reelPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return closed && open && afterClose;
    }

    private static CompositionReelBlockEntity placeReel(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.COMPOSITION_REEL.get().defaultBlockState(), Block.UPDATE_ALL);
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
}
