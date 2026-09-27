package com.mnemolith.data;

import java.util.Optional;

import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Builds an imprint slip with a cast. Commands and mob loot share this.
 * A slip keeps only its tag: place and time stay on the chunk imprint, not on the item, so two slips of one tag stack.
 */
public final class ImprintSlips {
    private ImprintSlips() {}

    /** The place is not stored. Two slips of {@code tag} are the same item. */
    public static ItemStack of(ImprintTag tag, BlockPos pos) {
        java.util.Objects.requireNonNull(pos);
        return of(tag);
    }

    /** A slip of this imprint's tag. The imprint's place is not copied onto the item. */
    public static ItemStack of(Imprint imprint) {
        return of(imprint.tag());
    }

    public static ItemStack of(ImprintTag tag) {
        ItemStack stack = new ItemStack(ModItems.IMPRINT_SLIP.get());
        stack.set(ModDataComponents.IMPRINT_CAST.get(), canonical(tag));
        return stack;
    }

    /** Same for every slip of {@code tag}: origin at zero, no player, time zero. */
    public static ImprintCast canonical(ImprintTag tag) {
        BlockPos origin = BlockPos.ZERO;
        return new ImprintCast(tag, ImprintWriter.intensityFor(tag), origin, Optional.empty(), Imprint.contextHash(tag, origin, 0L), 0L);
    }

    /**
     * Rewrites a slip already in the inventory onto {@link #canonical} and folds it into an earlier stack of the same
     * tag. Called from the slip's inventory tick, so slips saved before they stacked join a pile on their own.
     */
    public static void settle(ServerPlayer player, ItemStack stack) {
        ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
        if (cast == null || stack.isEmpty()) {
            return;
        }
        ImprintCast canon = canonical(cast.tag());
        if (!cast.equals(canon)) {
            stack.set(ModDataComponents.IMPRINT_CAST.get(), canon);
        }
        Inventory inventory = player.getInventory();
        int self = -1;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot) == stack) {
                self = slot;
                break;
            }
        }
        if (self < 0) {
            return;
        }
        int limit = Math.max(1, Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()));
        for (int slot = 0; slot < self && !stack.isEmpty(); slot++) {
            ItemStack other = inventory.getItem(slot);
            if (other.isEmpty() || other.getItem() != stack.getItem()) {
                continue;
            }
            ImprintCast otherCast = other.get(ModDataComponents.IMPRINT_CAST.get());
            if (otherCast != null && !otherCast.equals(canonical(otherCast.tag()))) {
                other.set(ModDataComponents.IMPRINT_CAST.get(), canonical(otherCast.tag()));
            }
            if (!ItemStack.isSameItemSameComponents(other, stack)) {
                continue;
            }
            int room = limit - other.getCount();
            if (room <= 0) {
                continue;
            }
            int move = Math.min(room, stack.getCount());
            other.grow(move);
            stack.shrink(move);
        }
        if (stack.isEmpty()) {
            inventory.setItem(self, ItemStack.EMPTY);
        }
    }

    public static boolean isSlip(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModItems.IMPRINT_SLIP.get() && stack.get(ModDataComponents.IMPRINT_CAST.get()) != null;
    }

    public static boolean holdsTag(Player player, ImprintTag tag) {
        Container inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isSlip(stack)) {
                continue;
            }
            ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
            if (cast != null && cast.tag() == tag) {
                return true;
            }
        }
        return false;
    }

    public static int weight(ItemStack stack) {
        ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
        if (cast == null) {
            return -1;
        }
        return cast.tag().weight();
    }
}
