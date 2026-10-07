package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.CompositionReelBlockEntity;
import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.Composition;
import com.mnemolith.content.composition.CompositionRecipes;
import com.mnemolith.content.menu.CompositionMenu;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The composition reel against the wrong things: an empty reel, a foreign item, a formula that does not exist, a
 * missing ingredient, a full inventory, composing switched off, the Compose button from a closed or out-of-reach menu,
 * and a reel full of slips going through a save and a chunk load. Nothing is spent unless the rules say so.
 */
final class ReelNegativeTests {
    private ReelNegativeTests() {}

    private static final int LANE = 62;

    static void compose(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(0);
            ServerPlayer player = support.player("NegReelComposer", at.offset(1, 0, 0));

            // Empty: nothing to do, nothing spent, no pressure.
            SimpleContainer empty = new SimpleContainer(ImprintConstants.COMPOSITION_SLOTS);
            helper.assertTrue(Composition.compose(level, at, player, empty).status() == ComposeResult.EMPTY, "an empty reel did not say EMPTY");
            helper.assertTrue(pressure(level, at) == 0, "an empty compose raised pressure");

            // A foreign item (dirt) among written slips: refused, nothing burned. Hoppers and the slot refuse it too.
            SimpleContainer foreign = slips(ImprintTag.FIRE, ImprintTag.PATH);
            foreign.setItem(2, new ItemStack(Items.DIRT, 5));
            helper.assertTrue(Composition.compose(level, at, player, foreign).status() == ComposeResult.BLANK, "dirt in the reel did not say BLANK");
            helper.assertTrue(count(foreign) == 7 && empty(player.getInventory()), "a compose with dirt in the reel spent or gave something");
            helper.assertTrue(!Composition.composable(new ItemStack(Items.DIRT)) && !Composition.composable(new ItemStack(Items.PAPER)), "dirt or paper counts as composable");

            // A formula that does not exist: exactly one slip is burned, no reward, the rest stay.
            List<ImprintTag> nonsense = nonsense();
            SimpleContainer wrong = slips(nonsense.toArray(ImprintTag[]::new));
            int before = count(wrong);
            ComposeResult failed = Composition.compose(level, at, player, wrong);
            helper.assertTrue(failed.status() == ComposeResult.FAIL && failed.formulaOrdinal() == -1, "a non-formula " + nonsense + " gave status " + failed.status());
            helper.assertTrue(count(wrong) == before - 1, "a failed formula burned " + (before - count(wrong)) + " slips instead of 1");
            helper.assertTrue(empty(player.getInventory()), "a failed formula handed out a reward");
            LoadedChunkMemory.clear(level.getChunkAt(at));

            // A missing ingredient: one half of a two-slip formula is not that formula.
            SimpleContainer half = slips(ImprintTag.FIRE);
            helper.assertTrue(Composition.compose(level, at, player, half).status() == ComposeResult.FAIL && empty(player.getInventory()),
                    "half a formula composed into something");
            helper.assertTrue(count(half) == 0, "the lone slip of a failed formula was not the one burned");
            LoadedChunkMemory.clear(level.getChunkAt(at));

            // A full inventory and a formula that hands over an item (FIRE + PATH: the tuned lens): refused, nothing spent.
            helper.assertTrue(CompositionRecipes.match(List.of(ImprintTag.FIRE, ImprintTag.PATH)).map(r -> r.hasItemResult()).orElse(false),
                    "FIRE + PATH is no longer an item formula; pick another for this test");
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            SimpleContainer lens = slips(ImprintTag.FIRE, ImprintTag.PATH);
            ComposeResult full = Composition.compose(level, at, player, lens);
            helper.assertTrue(full.status() == ComposeResult.FULL, "a full inventory gave status " + full.status());
            helper.assertTrue(count(lens) == 2 && cobble(inventory) == 36 * 64, "a refused compose for a full inventory spent slips or touched the inventory");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4.0D)).isEmpty(), "a refused compose dropped something");
            // Positive control: one free slot, and the same reel composes.
            inventory.setItem(0, ItemStack.EMPTY);
            helper.assertTrue(Composition.compose(level, at, player, lens).status() == ComposeResult.SUCCESS && count(lens) == 0
                    && inventory.getItem(0).is(ModItems.CHRONICLE_LENS.get()), "with a free slot FIRE + PATH did not compose into a lens");
            inventory.clearContent();

            // Composing switched off: nothing spent.
            boolean enabled = CommonConfig.COMPOSITION_ENABLED.get();
            try {
                CommonConfig.COMPOSITION_ENABLED.set(false);
                SimpleContainer off = slips(ImprintTag.FIRE, ImprintTag.PATH);
                helper.assertTrue(Composition.compose(level, at, player, off).status() == ComposeResult.DISABLED && count(off) == 2 && empty(inventory),
                        "composing while disabled spent slips or gave something");
            } finally {
                CommonConfig.COMPOSITION_ENABLED.set(enabled);
            }
            LoadedChunkMemory.clear(level.getChunkAt(at));
            helper.succeed();
        }
    }

    /** The Compose button: wrong id, menu not open, player out of reach, reel gone, a menu not bound to a reel. */
    static void menuButton(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(1);
            BlockPos reelPos = at.offset(0, 0, 0);
            level.setBlock(reelPos, ModBlocks.COMPOSITION_REEL.get().defaultBlockState(), 3);
            CompositionReelBlockEntity reel = (CompositionReelBlockEntity) level.getBlockEntity(reelPos);
            helper.assertTrue(reel != null, "the reel has no block entity");
            ServerPlayer player = support.player("NegReelButton", at.offset(1, 0, 1));
            fill(reel, ImprintTag.FIRE, ImprintTag.PATH);
            CompositionMenu menu = new CompositionMenu(7, player.getInventory(), reel);
            try {
                // The menu was never opened (the player still has their own inventory screen).
                player.containerMenu = player.inventoryMenu;
                helper.assertTrue(!menu.clickMenuButton(player, ImprintConstants.COMPOSE_BUTTON_ID), "Compose worked from a menu that is not open");
                helper.assertTrue(count(reel) == 2 && empty(player.getInventory()), "a closed menu's Compose spent slips");

                // Open, but buttons that do not exist.
                player.containerMenu = menu;
                for (int id : new int[] {1, -1, 99, Integer.MAX_VALUE}) {
                    helper.assertTrue(!menu.clickMenuButton(player, id), "button " + id + " was accepted");
                }
                helper.assertTrue(count(reel) == 2, "an unknown button spent slips");

                // Open, but the player walked away (20 blocks).
                player.snapTo(at.getX() + 20.5D, at.getY(), at.getZ() + 0.5D);
                helper.assertTrue(!menu.stillValid(player), "the reel menu is still valid 20 blocks away");
                helper.assertTrue(!menu.clickMenuButton(player, ImprintConstants.COMPOSE_BUTTON_ID), "Compose worked from 20 blocks away");
                helper.assertTrue(count(reel) == 2 && empty(player.getInventory()), "an out-of-reach Compose spent slips");
                player.snapTo(at.getX() + 1.5D, at.getY(), at.getZ() + 1.5D);

                // A menu over a plain container, not a reel in the world.
                SimpleContainer loose = slips(ImprintTag.FIRE, ImprintTag.PATH);
                CompositionMenu unbound = new CompositionMenu(8, player.getInventory(), loose);
                player.containerMenu = unbound;
                helper.assertTrue(!unbound.clickMenuButton(player, ImprintConstants.COMPOSE_BUTTON_ID) && count(loose) == 2, "Compose worked on a menu with no reel");

                // Positive control: open, close by, the right button.
                player.containerMenu = menu;
                helper.assertTrue(menu.stillValid(player), "the reel menu is not valid beside the reel (the test set-up is wrong)");
                helper.assertTrue(menu.clickMenuButton(player, ImprintConstants.COMPOSE_BUTTON_ID) && menu.status() == ComposeResult.SUCCESS,
                        "Compose from an open menu beside the reel did not succeed (status " + menu.status() + ")");
                player.getInventory().clearContent();

                // The reel broken while the menu is still open: the menu is dead.
                fill(reel, ImprintTag.FIRE, ImprintTag.PATH);
                level.setBlock(reelPos, Blocks.AIR.defaultBlockState(), 2 | 16);
                helper.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player, ImprintConstants.COMPOSE_BUTTON_ID),
                        "Compose worked after the reel was removed");
                helper.assertTrue(empty(player.getInventory()), "a removed reel's Compose handed out a reward");
            } finally {
                player.containerMenu = player.inventoryMenu;
                player.getInventory().clearContent();
                level.setBlock(reelPos, Blocks.AIR.defaultBlockState(), 2 | 16);
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4.0D))) {
                    item.discard();
                }
                LoadedChunkMemory.clear(level.getChunkAt(at));
            }
            helper.succeed();
        }
    }

    /** A reel holding three slips keeps all three through a save and a chunk load; nothing pops out, nothing doubles. */
    static void saveLoad(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(2);
            level.setBlock(at, ModBlocks.COMPOSITION_REEL.get().defaultBlockState(), 3);
            CompositionReelBlockEntity reel = (CompositionReelBlockEntity) level.getBlockEntity(at);
            helper.assertTrue(reel != null, "the reel has no block entity");
            try {
                fill(reel, ImprintTag.REDSTONE, ImprintTag.PATH, ImprintTag.BUILD);
                helper.assertTrue(count(reel) == 3, "the reel did not take three slips");
                TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                reel.saveWithFullMetadata(output);
                CompoundTag saved = output.buildResult();
                // What a chunk load does: the data is read into the block entity, then onLoad runs once it is in the level.
                reel.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
                reel.onLoad();
                int dropped = 0;
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3.0D))) {
                    dropped += item.getItem().getCount();
                    item.discard();
                }
                helper.assertTrue(count(reel) == 3 && dropped == 0, "after a save and load the reel holds " + count(reel) + " slips and " + dropped + " fell out");
                helper.assertTrue(ImprintSlips.isSlip(reel.getItem(2)), "the third slip is gone from its slot");

                // An old two-slot save whose third entry was not a slip (the old layout): that stray item is put out, not lost.
                CompoundTag legacy = saved.copy();
                TagValueOutput strayOut = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                net.minecraft.core.NonNullList<ItemStack> old = net.minecraft.core.NonNullList.withSize(3, ItemStack.EMPTY);
                old.set(0, ImprintSlips.of(ImprintTag.FIRE));
                old.set(2, new ItemStack(Items.GOLD_INGOT, 2));
                net.minecraft.world.ContainerHelper.saveAllItems(strayOut, old);
                legacy.merge(strayOut.buildResult());
                reel.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), legacy));
                reel.onLoad();
                int gold = 0;
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3.0D))) {
                    if (item.getItem().is(Items.GOLD_INGOT)) {
                        gold += item.getItem().getCount();
                    }
                    item.discard();
                }
                int kept = reel.getItem(2).is(Items.GOLD_INGOT) ? reel.getItem(2).getCount() : 0;
                helper.assertTrue(gold + kept == 2 && count(reel) - kept == 1, "an old save's stray item: dropped " + gold + ", kept " + kept + ", slips " + (count(reel) - kept));
                helper.assertTrue(kept == 0, "a non-slip stayed in the reel's slip slot");
            } finally {
                reel.clearContent();
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | 16);
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3.0D))) {
                    item.discard();
                }
            }
            helper.succeed();
        }
    }

    private static List<ImprintTag> nonsense() {
        ImprintTag[] tags = ImprintTag.values();
        for (ImprintTag a : tags) {
            for (ImprintTag b : tags) {
                List<ImprintTag> pair = List.of(a, b);
                if (CompositionRecipes.match(pair).isEmpty()) {
                    return pair;
                }
            }
        }
        throw new IllegalStateException("every pair of tags is a formula");
    }

    private static SimpleContainer slips(ImprintTag... tags) {
        SimpleContainer container = new SimpleContainer(ImprintConstants.COMPOSITION_SLOTS);
        for (int i = 0; i < tags.length; i++) {
            container.setItem(i, ImprintSlips.of(tags[i]));
        }
        return container;
    }

    private static void fill(Container reel, ImprintTag... tags) {
        reel.clearContent();
        for (int i = 0; i < tags.length; i++) {
            reel.setItem(i, ImprintSlips.of(tags[i]));
        }
    }

    private static int count(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            total += container.getItem(i).getCount();
        }
        return total;
    }

    private static boolean empty(Inventory inventory) {
        return inventory.isEmpty();
    }

    private static int cobble(Inventory inventory) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.COBBLESTONE)) {
                total += inventory.getItem(i).getCount();
            }
        }
        return total;
    }

    private static int pressure(ServerLevel level, BlockPos pos) {
        var memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? 0 : memory.instability();
    }

}
