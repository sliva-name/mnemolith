package com.mnemolith.gametest;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.MemoryFeel;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A real server player (live site 26) threads a hush loom by hand: the shared KEPT cue is sent once, at the loom.
 * The particles and sound themselves are client presentation (by-hand check); this proves the real interaction path
 * reaches the shared language.
 */
final class FeelLiveTests {
    private FeelLiveTests() {}

    static void loomCueReachesPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 26);
        LivePlayers.force(level, site);
        ServerPlayer player = LivePlayers.join(helper, "LiveFeel", Vec3.atBottomCenterOf(site));
        BlockPos loom = site.east(2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    player.setGameMode(GameType.SURVIVAL);
                    level.setBlock(loom, ModBlocks.HUSH_LOOM.get().defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(loom));
                    ImprintWriter.write(level, loom, java.util.List.of(ImprintTag.DEATH), null, false);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HUSH_FILAMENT.get(), 1));
                    int kept = MemoryFeel.sent(MemoryFeel.Kind.KEPT);
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(loom).add(0.0D, 0.5D, 0.0D), Direction.UP, loom, false);
                    player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    int after = MemoryFeel.sent(MemoryFeel.Kind.KEPT);
                    BlockPos at = MemoryFeel.lastPos();
                    level.setBlock(loom, Blocks.AIR.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(loom));
                    player.getInventory().clearContent();
                    helper.assertTrue(after == kept + 1 && at.closerThan(loom, 2.0D), "kept cue: " + kept + " -> " + after + " at " + at);
                })
                .thenSucceed();
    }
}
