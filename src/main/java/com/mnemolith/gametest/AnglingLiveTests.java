package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

/**
 * A real server player (live site 23) holding a rod, with a hook in water over a chunk that remembers: catches posted
 * on the event bus (as the hook does on a reel-in) bring up a slip of the chunk's oldest imprint, and the flung slip
 * reaches the player's inventory. The roll is the level's own, so the test reels until a memory bites (at most 60
 * catches; a miss every time has a chance under 1e-9).
 */
final class AnglingLiveTests {
    private AnglingLiveTests() {}

    static void anglerLandsMemory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 23);
        LivePlayers.force(level, site);
        ServerPlayer player = LivePlayers.join(helper, "LiveAngler", Vec3.atBottomCenterOf(site));
        BlockPos water = site.south(2);
        FishingHook[] hook = new FishingHook[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                    level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(water));
                    ImprintWriter.write(level, water, List.of(ImprintTag.FALL), null, false);
                    for (int i = 0; i < 70; i++) {
                        ImprintWriter.write(level, water, List.of(ImprintTag.BUILD), null, false);
                    }
                    hook[0] = new FishingHook(player, level, 0, 0);
                    hook[0].setPos(water.getX() + 0.5D, water.getY() + 0.4D, water.getZ() + 0.5D);
                    level.addFreshEntity(hook[0]);
                    ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(water));
                    int before = memory == null ? 0 : memory.imprintCount();
                    int catches = 0;
                    while (catches < 60 && (memory = LoadedChunkMemory.existing(level.getChunkAt(water))) != null && memory.imprintCount() == before) {
                        NeoForge.EVENT_BUS.post(new ItemFishedEvent(List.of(new ItemStack(Items.COD)), 1, hook[0]));
                        catches++;
                    }
                    memory = LoadedChunkMemory.existing(level.getChunkAt(water));
                    helper.assertTrue(memory != null && memory.imprintCount() == before - 1
                            && memory.imprintsCopy().stream().noneMatch(i -> i.tag() == ImprintTag.FALL),
                            "no memory came up after " + catches + " catches, or not the oldest");
                })
                .thenWaitUntil(() -> helper.assertTrue(player.getInventory().getNonEquipmentItems().stream()
                        .anyMatch(s -> s.get(ModDataComponents.IMPRINT_CAST.get()) != null
                                && s.get(ModDataComponents.IMPRINT_CAST.get()).tag() == ImprintTag.FALL),
                        "the fall slip has not reached the player"))
                .thenExecute(() -> {
                    hook[0].discard();
                    level.setBlock(water, Blocks.AIR.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(water));
                    player.getInventory().clearContent();
                })
                .thenSucceed();
    }
}
