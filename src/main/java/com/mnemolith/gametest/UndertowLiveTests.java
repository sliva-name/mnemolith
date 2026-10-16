package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.content.ModItems;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.fishing.Angling;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
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
 * Two real players (live site 25) fishing one chunk that holds an old fall imprint, many builds and one newer
 * explosion. A with a bare rod lands the oldest (fall); B with a tide lure lands the loudest (explosion). Each slip
 * reaches its own angler; both earn Undertow, only B earns Lure of the Deep. Catches go through the event bus with the
 * level's own roll, reeled until a memory bites (at most 60 each; all misses has a chance under 1e-9).
 */
final class UndertowLiveTests {
    private UndertowLiveTests() {}

    static void twoAnglersOneChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 25);
        LivePlayers.force(level, site);
        ServerPlayer a = LivePlayers.join(helper, "LiveAnglerA", Vec3.atBottomCenterOf(site.west(3)));
        ServerPlayer b = LivePlayers.join(helper, "LiveAnglerB", Vec3.atBottomCenterOf(site.east(3)));
        BlockPos waterA = site.west(3).south(2);
        BlockPos waterB = site.east(3).south(2);
        FishingHook[] hooks = new FishingHook[2];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (ServerPlayer p : List.of(a, b)) {
                        p.setGameMode(GameType.SURVIVAL);
                        p.getInventory().clearContent();
                        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                    }
                    b.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.TIDE_LURE.get()));
                    helper.assertTrue(level.getChunkAt(waterA) == level.getChunkAt(waterB), "both waters should share one chunk");
                    level.setBlock(waterA, Blocks.WATER.defaultBlockState(), 3);
                    level.setBlock(waterB, Blocks.WATER.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(waterA));
                    ImprintWriter.write(level, waterA, List.of(ImprintTag.FALL), null, false);
                    for (int i = 0; i < 30; i++) {
                        ImprintWriter.write(level, waterA, List.of(ImprintTag.BUILD), null, false);
                    }
                    ImprintWriter.write(level, waterA, List.of(ImprintTag.EXPLOSION), null, false);
                    hooks[0] = hook(level, a, waterA);
                    hooks[1] = hook(level, b, waterB);
                    reelUntilBite(level, hooks[0]);
                    reelUntilBite(level, hooks[1]);
                    ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(waterA));
                    helper.assertTrue(memory != null && memory.imprintsCopy().stream().noneMatch(i -> i.tag() == ImprintTag.FALL || i.tag() == ImprintTag.EXPLOSION),
                            "A should take the fall and B the explosion: " + (memory == null ? "none" : memory.imprintsCopy().size()));
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(holds(a, ImprintTag.FALL) && !holds(a, ImprintTag.EXPLOSION), "A has not got the fall slip");
                    helper.assertTrue(holds(b, ImprintTag.EXPLOSION) && !holds(b, ImprintTag.FALL), "B has not got the explosion slip");
                })
                .thenExecute(() -> {
                    boolean aLanded = done(level, a, Angling.ADVANCEMENT);
                    boolean bLanded = done(level, b, Angling.ADVANCEMENT);
                    boolean aLure = done(level, a, Angling.LURE_ADVANCEMENT);
                    boolean bLure = done(level, b, Angling.LURE_ADVANCEMENT);
                    for (FishingHook hook : hooks) {
                        hook.discard();
                    }
                    level.setBlock(waterA, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(waterB, Blocks.AIR.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(waterA));
                    a.getInventory().clearContent();
                    b.getInventory().clearContent();
                    helper.assertTrue(aLanded && bLanded && !aLure && bLure,
                            "advancements: a=" + aLanded + "/" + aLure + " b=" + bLanded + "/" + bLure);
                })
                .thenSucceed();
    }

    private static FishingHook hook(ServerLevel level, ServerPlayer owner, BlockPos water) {
        FishingHook hook = new FishingHook(owner, level, 0, 0);
        hook.setPos(water.getX() + 0.5D, water.getY() + 0.4D, water.getZ() + 0.5D);
        level.addFreshEntity(hook);
        return hook;
    }

    private static void reelUntilBite(ServerLevel level, FishingHook hook) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(hook.blockPosition()));
        int before = memory == null ? 0 : memory.imprintCount();
        for (int i = 0; i < 60; i++) {
            NeoForge.EVENT_BUS.post(new ItemFishedEvent(List.of(new ItemStack(Items.COD)), 1, hook));
            memory = LoadedChunkMemory.existing(level.getChunkAt(hook.blockPosition()));
            if (memory == null || memory.imprintCount() < before) {
                return;
            }
        }
    }

    private static boolean holds(ServerPlayer player, ImprintTag tag) {
        return player.getInventory().getNonEquipmentItems().stream().anyMatch(s -> s.get(ModDataComponents.IMPRINT_CAST.get()) != null
                && s.get(ModDataComponents.IMPRINT_CAST.get()).tag() == tag);
    }

    private static boolean done(ServerLevel level, ServerPlayer player, Identifier id) {
        var advancement = level.getServer().getAdvancements().get(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}
