package com.mnemolith.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.armory.ArmoryItems;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.EchoHomeBlockEntity;
import com.mnemolith.echo.CareLesson;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoProgress;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.EchoRole;
import com.mnemolith.echo.FarmLesson;
import com.mnemolith.echo.LumberLesson;
import com.mnemolith.echo.SlotStack;
import com.mnemolith.echo.StoredEcho;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The echo home pedestal against people it does not belong to and states it cannot take: a stranger retraining,
 * waking or housing someone else's echo, a full pedestal, an owner already at the echo limit, and a housed echo from a
 * damaged save whose items sit in slots an echo does not have. Every refusal leaves the echoes and their items as they were.
 */
final class EchoHomeNegativeTests {
    private EchoHomeNegativeTests() {}

    private static final int LANE = 63;

    static void strangers(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(0);
            BlockPos pedestal = at;
            level.setBlock(pedestal, ModBlocks.ECHO_HOME.get().defaultBlockState(), 3);
            EchoHomeBlockEntity home = (EchoHomeBlockEntity) level.getBlockEntity(pedestal);
            helper.assertTrue(home != null, "the echo home has no block entity");
            ServerPlayer owner = support.player("NegHomeOwner", at.offset(2, 0, 0));
            ServerPlayer stranger = support.player("NegHomeStranger", at.offset(-2, 0, 0));
            try {
                home.offer(stored(owner.getUUID(), List.of(new SlotStack(4, new ItemStack(Items.EMERALD, 3)))));

                // Retraining with a role item: a stranger cannot, the owner can.
                stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArmoryItems.HUSH_SPEAR.get()));
                use(level, pedestal, stranger, true);
                helper.assertTrue(home.housed().get(0).role() == EchoRole.NONE, "a stranger retrained someone else's housed echo to " + home.housed().get(0).role());
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArmoryItems.HUSH_SPEAR.get()));
                use(level, pedestal, owner, true);
                helper.assertTrue(home.housed().get(0).role() == EchoRole.SCOUT, "the owner could not retrain their own echo (the test set-up is wrong)");
                owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                // Waking: a stranger gets nothing, the pedestal keeps the echo.
                InteractionResult woke = use(level, pedestal, stranger, false);
                helper.assertTrue(woke == InteractionResult.FAIL, "a stranger's wake gave " + woke);
                helper.assertTrue(home.size() == 1 && home.housed().get(0).owner().equals(owner.getUUID()), "a stranger's wake changed the pedestal");
                helper.assertTrue(echoesNear(level, at).isEmpty(), "a stranger's wake put an echo into the world");

                // Housing: a stranger sneaking beside the owner's live echo cannot put it away.
                EchoEntity live = support.echo(owner, at.offset(1, 0, 1), EchoLesson.NONE);
                live.inventory().setItem(2, new ItemStack(Items.GOLD_INGOT, 9));
                stranger.setShiftKeyDown(true);
                use(level, pedestal, stranger, false);
                stranger.setShiftKeyDown(false);
                helper.assertTrue(live.isAlive() && !live.isRemoved() && live.inventory().getItem(2).getCount() == 9 && home.size() == 1,
                        "a stranger housed someone else's echo");
                helper.assertTrue(!EchoLife.house(stranger, live, home) && live.isAlive() && home.size() == 1, "EchoLife.house took a stranger's request");
            } finally {
                level.setBlock(pedestal, Blocks.AIR.defaultBlockState(), 2 | 16);
                discardItems(level, at);
            }
            helper.succeed();
        }
    }

    static void limits(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(1);
            BlockPos pedestal = at;
            level.setBlock(pedestal, ModBlocks.ECHO_HOME.get().defaultBlockState(), 3);
            EchoHomeBlockEntity home = (EchoHomeBlockEntity) level.getBlockEntity(pedestal);
            ServerPlayer owner = support.player("NegHomeLimits", at.offset(2, 0, 0));
            EchoRegistry registry = EchoRegistry.get(level.getServer());
            try {
                // A full pedestal: the owner's echo stays in the world with its items.
                for (int i = 0; i < EchoHomeBlockEntity.CAP; i++) {
                    helper.assertTrue(home.offer(stored(UUID.randomUUID(), List.of())), "the pedestal refused echo " + (i + 1) + " of " + EchoHomeBlockEntity.CAP);
                }
                helper.assertTrue(!home.offer(stored(UUID.randomUUID(), List.of())), "a full pedestal took a ninth echo");
                EchoEntity live = support.echo(owner, at.offset(1, 0, 1), EchoLesson.NONE);
                live.inventory().setItem(0, new ItemStack(Items.DIAMOND, 4));
                owner.setShiftKeyDown(true);
                InteractionResult housed = use(level, pedestal, owner, false);
                owner.setShiftKeyDown(false);
                helper.assertTrue(housed == InteractionResult.FAIL && live.isAlive() && live.inventory().getItem(0).getCount() == 4 && home.size() == EchoHomeBlockEntity.CAP,
                        "a full pedestal housed the echo (" + housed + ", size " + home.size() + ")");
                live.discard();
                registry.forget(owner.getUUID());

                // The owner already at the echo limit: the housed echo stays housed, items and all.
                level.setBlock(pedestal, Blocks.AIR.defaultBlockState(), 2 | 16);
                discardItems(level, at);
                level.setBlock(pedestal, ModBlocks.ECHO_HOME.get().defaultBlockState(), 3);
                home = (EchoHomeBlockEntity) level.getBlockEntity(pedestal);
                StoredEcho mine = stored(owner.getUUID(), List.of(new SlotStack(1, new ItemStack(Items.IRON_INGOT, 6))));
                home.offer(mine);
                int limit = EchoProgress.echoLimit(owner);
                for (int i = 0; i < limit; i++) {
                    registry.put(owner.getUUID(), UUID.randomUUID());
                }
                helper.assertTrue(!EchoLife.wake(owner, home, pedestal.above()), "an owner at the echo limit woke another echo");
                helper.assertTrue(home.size() == 1 && home.housed().get(0).echo().equals(mine.echo()) && home.housed().get(0).inventory().get(0).stack().getCount() == 6,
                        "a refused wake lost the housed echo or its items");
                helper.assertTrue(echoesNear(level, at).isEmpty(), "a refused wake put an echo into the world");
                registry.forget(owner.getUUID());
            } finally {
                level.setBlock(pedestal, Blocks.AIR.defaultBlockState(), 2 | 16);
                discardItems(level, at);
            }
            helper.succeed();
        }
    }

    /** A housed echo whose saved items sit in slots an echo does not have (a damaged or hand-edited save) loses none of them on waking. */
    static void strayItemsOnWake(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(2);
            BlockPos pedestal = at;
            level.setBlock(pedestal, ModBlocks.ECHO_HOME.get().defaultBlockState(), 3);
            EchoHomeBlockEntity home = (EchoHomeBlockEntity) level.getBlockEntity(pedestal);
            ServerPlayer owner = support.player("NegHomeStray", at.offset(2, 0, 0));
            try {
                home.offer(stored(owner.getUUID(), List.of(
                        new SlotStack(5, new ItemStack(Items.IRON_INGOT, 4)),
                        new SlotStack(41, new ItemStack(Items.DIAMOND, 3)),
                        new SlotStack(-1, new ItemStack(Items.EMERALD, 2)),
                        new SlotStack(5, new ItemStack(Items.GOLD_INGOT, 1)))));
                helper.assertTrue(EchoLife.wake(owner, home, pedestal.above()), "the owner could not wake their echo");
                List<EchoEntity> woken = echoesNear(level, at);
                helper.assertTrue(woken.size() == 1, "waking put " + woken.size() + " echoes into the world");
                EchoEntity echo = woken.get(0);
                support.track(echo);
                helper.assertTrue(echo.inventory().getItem(5).is(Items.IRON_INGOT) && echo.inventory().getItem(5).getCount() == 4, "the item in a real slot did not come back in that slot");
                for (Item item : new Item[] {Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT}) {
                    int expected = item == Items.DIAMOND ? 3 : item == Items.EMERALD ? 2 : 1;
                    int found = count(echo, item) + dropped(level, at, item);
                    helper.assertTrue(found == expected, "a stray " + item + " stack was lost on waking: found " + found + " of " + expected);
                }
            } finally {
                level.setBlock(pedestal, Blocks.AIR.defaultBlockState(), 2 | 16);
                discardItems(level, at);
            }
            helper.succeed();
        }
    }

    static StoredEcho stored(UUID owner, List<SlotStack> items) {
        return new StoredEcho(UUID.randomUUID(), owner, "Neg", Optional.empty(), EchoRole.NONE, new ArrayList<>(items), Optional.empty(), EchoLesson.NONE,
                FarmLesson.NONE, LumberLesson.NONE, CareLesson.NONE, 20.0F, 0.0D, Optional.empty(), false, 0L);
    }

    private static InteractionResult use(ServerLevel level, BlockPos pos, ServerPlayer player, boolean withItem) {
        BlockState state = level.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        if (withItem) {
            return state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        }
        return state.useWithoutItem(level, player, hit);
    }

    private static List<EchoEntity> echoesNear(ServerLevel level, BlockPos at) {
        return level.getEntitiesOfClass(EchoEntity.class, new AABB(at).inflate(4.0D), e -> !e.isRemoved());
    }

    private static int count(EchoEntity echo, Item item) {
        int total = 0;
        for (int i = 0; i < echo.inventory().getContainerSize(); i++) {
            if (echo.inventory().getItem(i).is(item)) {
                total += echo.inventory().getItem(i).getCount();
            }
        }
        return total;
    }

    private static int dropped(ServerLevel level, BlockPos at, Item item) {
        int total = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4.0D))) {
            if (entity.getItem().is(item)) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }

    private static void discardItems(ServerLevel level, BlockPos at) {
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(6.0D))) {
            entity.discard();
        }
        for (EchoEntity echo : echoesNear(level, at)) {
            echo.discard();
        }
    }
}
