package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.HushLoomBlock;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Hushling;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A real server player (live site 21) threads a hush loom by hand ({@code gameMode.useItemOn}): one filament is
 * spent, the loom keeps the chunk's loudest tag, and a hushling's thinning then takes everything but that tag.
 */
final class HushLoomLiveTests {
    private HushLoomLiveTests() {}

    static void loomKeepsThread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 21);
        LivePlayers.force(level, site);
        ServerPlayer player = LivePlayers.join(helper, "LiveLoomWeaver", Vec3.atBottomCenterOf(site));
        BlockPos loom = site.east(2);
        Hushling[] mob = new Hushling[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    player.setGameMode(GameType.SURVIVAL);
                    level.setBlock(loom, ModBlocks.HUSH_LOOM.get().defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(loom));
                    ImprintWriter.write(level, loom, List.of(ImprintTag.EXPLOSION), null, false);
                    ImprintWriter.write(level, loom, List.of(ImprintTag.BUILD), null, false);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HUSH_FILAMENT.get(), 2));
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(loom).add(0.0D, 0.5D, 0.0D), Direction.UP, loom, false);
                    player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    helper.assertTrue(HushLoomBlock.kept(level.getBlockState(loom)).orElse(null) == ImprintTag.EXPLOSION,
                            "the loom was not threaded with explosion: " + level.getBlockState(loom));
                    helper.assertTrue(player.getMainHandItem().getCount() == 1, "filament not spent: " + player.getMainHandItem());
                    mob[0] = ModEntities.HUSHLING.get().create(level, EntitySpawnReason.COMMAND);
                    helper.assertTrue(mob[0] != null, "no hushling");
                    mob[0].snapTo(loom.getX() + 0.5D, loom.getY() + 1, loom.getZ() + 0.5D, 0.0F, 0.0F);
                    mob[0].setNoAi(true);
                    level.addFreshEntity(mob[0]);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    for (int i = 0; i < 6; i++) {
                        mob[0].thin(level);
                    }
                    ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(loom));
                    helper.assertTrue(memory != null && memory.imprintsCopy().stream().allMatch(i -> i.tag() == ImprintTag.EXPLOSION)
                            && memory.imprintsCopy().size() == 1,
                            "thinning should leave only the kept explosion: " + (memory == null ? "none" : memory.imprintsCopy()));
                })
                .thenExecute(() -> {
                    mob[0].discard();
                    level.setBlock(loom, Blocks.AIR.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(loom));
                    player.getInventory().clearContent();
                })
                .thenSucceed();
    }
}
