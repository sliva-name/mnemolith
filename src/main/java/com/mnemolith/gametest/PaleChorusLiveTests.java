package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.HushLoomBlock;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Hush;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Two real players (live site 22). A hushling with its AI on is set on A and hits them: A is hushed and A's writes
 * are dropped, while B, a few chunks' worth of steps away, is not hushed and still writes. Then both thread the same
 * hush loom by hand: each spends a filament, the loom keeps the loudest tag at each threading, and both earn Keep This.
 */
final class PaleChorusLiveTests {
    private PaleChorusLiveTests() {}

    static void hushPersonalLoomShared(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 22);
        LivePlayers.force(level, site);
        LivePlayers.force(level, site.east(24));
        ServerPlayer a = LivePlayers.join(helper, "LiveChorusA", Vec3.atBottomCenterOf(site));
        ServerPlayer b = LivePlayers.join(helper, "LiveChorusB", Vec3.atBottomCenterOf(site.east(24)));
        BlockPos loom = site.east(24).north(2);
        Hushling[] mob = new Hushling[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (ServerPlayer p : List.of(a, b)) {
                        p.setGameMode(GameType.SURVIVAL);
                        p.removeAllEffects();
                        Hush.forget(p.getUUID());
                    }
                    for (Hushling old : level.getEntitiesOfClass(Hushling.class, new AABB(site).inflate(48.0D))) {
                        old.discard();
                    }
                    mob[0] = ModEntities.HUSHLING.get().create(level, EntitySpawnReason.COMMAND);
                    helper.assertTrue(mob[0] != null, "no hushling");
                    mob[0].snapTo(site.getX() + 2.5D, site.getY(), site.getZ() + 0.5D, 0.0F, 0.0F);
                    level.addFreshEntity(mob[0]);
                    mob[0].setTarget(a);
                })
                .thenWaitUntil(() -> {
                    a.setHealth(a.getMaxHealth());
                    b.setHealth(b.getMaxHealth());
                    helper.assertTrue(Hush.hushed(level, a.getUUID()), "the hushling has not hushed A yet: target=" + mob[0].getTarget());
                })
                .thenExecute(() -> {
                    mob[0].discard();
                    helper.assertTrue(!Hush.hushed(level, b.getUUID()), "B was hushed too");
                    BlockPos spotA = site.south(3);
                    BlockPos spotB = site.east(24).south(3);
                    LoadedChunkMemory.clear(level.getChunkAt(spotA));
                    LoadedChunkMemory.clear(level.getChunkAt(spotB));
                    ImprintWriter.write(level, spotA, List.of(ImprintTag.BUILD), a.getUUID(), false);
                    ImprintWriter.write(level, spotB, List.of(ImprintTag.BUILD), b.getUUID(), false);
                    ChunkMemory ma = LoadedChunkMemory.existing(level.getChunkAt(spotA));
                    ChunkMemory mb = LoadedChunkMemory.existing(level.getChunkAt(spotB));
                    helper.assertTrue(ma == null || ma.imprintsCopy().stream().noneMatch(i -> i.tag() == ImprintTag.BUILD),
                            "A's write while hushed was kept: " + (ma == null ? "none" : ma.imprintsCopy()));
                    helper.assertTrue(mb != null && mb.imprintsCopy().stream().anyMatch(i -> i.tag() == ImprintTag.BUILD),
                            "B's write was dropped: " + (mb == null ? "none" : mb.imprintsCopy()));
                    Hush.forget(a.getUUID());
                    a.removeAllEffects();
                })
                .thenExecute(() -> {
                    // both thread one loom
                    a.teleportTo(loom.getX() + 0.5D, loom.getY(), loom.getZ() - 1.5D);
                    level.setBlock(loom, ModBlocks.HUSH_LOOM.get().defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(loom));
                    BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(loom).add(0.0D, 0.5D, 0.0D), Direction.UP, loom, false);
                    InteractionHand hand = InteractionHand.MAIN_HAND;
                    ImprintWriter.write(level, loom, List.of(ImprintTag.BUILD), null, false);
                    a.setItemInHand(hand, new ItemStack(ModItems.HUSH_FILAMENT.get(), 2));
                    a.gameMode.useItemOn(a, level, a.getMainHandItem(), hand, top);
                    ImprintTag first = HushLoomBlock.kept(level.getBlockState(loom)).orElse(null);
                    ImprintWriter.write(level, loom, List.of(ImprintTag.EXPLOSION), null, false);
                    b.setItemInHand(hand, new ItemStack(ModItems.HUSH_FILAMENT.get(), 2));
                    b.gameMode.useItemOn(b, level, b.getMainHandItem(), hand, top);
                    ImprintTag second = HushLoomBlock.kept(level.getBlockState(loom)).orElse(null);
                    var advancement = level.getServer().getAdvancements().get(HushLoomBlock.ADVANCEMENT);
                    boolean aDone = advancement != null && a.getAdvancements().getOrStartProgress(advancement).isDone();
                    boolean bDone = advancement != null && b.getAdvancements().getOrStartProgress(advancement).isDone();
                    int left = a.getMainHandItem().getCount() + b.getMainHandItem().getCount();
                    level.setBlock(loom, Blocks.AIR.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(loom));
                    a.getInventory().clearContent();
                    b.getInventory().clearContent();
                    helper.assertTrue(first == ImprintTag.BUILD && second == ImprintTag.EXPLOSION, "kept " + first + " then " + second);
                    helper.assertTrue(left == 2, "filament left " + left);
                    helper.assertTrue(aDone && bDone, "Keep This: a=" + aDone + " b=" + bDone);
                })
                .thenSucceed();
    }
}
