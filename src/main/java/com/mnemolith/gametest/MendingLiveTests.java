package com.mnemolith.gametest;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.echo.storm.ScarSites;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mending with a real server player ({@link LivePlayers}): the player right-clicks a Scar heart with posies through
 * the real block use, three times a day apart (the day is wound back between them); the heart becomes a memorial stone
 * and the site a memorial; three posies are spent.
 */
final class MendingLiveTests {
    private MendingLiveTests() {}

    static void mendingTendsHeart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 11);
        LivePlayers.force(level, site);
        BlockPos heart = site.offset(2, 0, 0);
        ServerPlayer mender = LivePlayers.join(helper, "LiveMender", Vec3.atBottomCenterOf(site));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var chunk = level.getChunkAt(heart);
                    LoadedChunkMemory.clear(chunk);
                    var memory = LoadedChunkMemory.getOrCreate(chunk);
                    memory.setScar(new ScarSite(1, level.getGameTime()));
                    level.setBlockAndUpdate(heart, ModBlocks.SCAR_HEART.get().defaultBlockState());
                    mender.getInventory().clearContent();
                    mender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEMBRANCE_POSY.get(), 4));
                    for (int i = 0; i < ScarSites.TENDINGS; i++) {
                        ScarSite s = memory.scar();
                        memory.setScar(new ScarSite(s.tempers(), s.seededAt(), s.tended(), s.tendedAt() - ScarSites.RESEED_TICKS, s.memorial()));
                        mender.gameMode.useItemOn(mender, level, mender.getMainHandItem(), InteractionHand.MAIN_HAND,
                                new BlockHitResult(Vec3.atCenterOf(heart), Direction.UP, heart, false));
                    }
                    boolean stone = level.getBlockState(heart).is(ModBlocks.MEMORIAL_STONE.get());
                    ScarSite after = memory.scar();
                    int left = mender.getMainHandItem().getCount();
                    level.setBlockAndUpdate(heart, Blocks.AIR.defaultBlockState());
                    LoadedChunkMemory.clear(chunk);
                    helper.assertTrue(stone, "the heart did not become a memorial stone");
                    helper.assertTrue(after != null && after.memorial() && after.tended() == ScarSites.TENDINGS, "the site is not a memorial: " + after);
                    helper.assertTrue(left == 1, "three posies were not spent: " + left + " left");
                })
                .thenSucceed();
    }
}
