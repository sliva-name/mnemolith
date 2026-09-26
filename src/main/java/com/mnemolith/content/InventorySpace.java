package com.mnemolith.content;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Room in the player's own storage for an item that is handed over, not picked up.
 * A full inventory refuses the whole action; nothing is inserted and nothing is dropped.
 */
public final class InventorySpace {
    private static final ThreadLocal<Boolean> REFUSED = ThreadLocal.withInitial(() -> false);

    private InventorySpace() {}

    public static void clearRefused() {
        REFUSED.set(false);
    }

    /** True when the last {@link #refuse} in this call has not been read yet. */
    public static boolean consumeRefused() {
        boolean refused = REFUSED.get();
        REFUSED.set(false);
        return refused;
    }

    public static void refuse(ServerPlayer player) {
        REFUSED.set(true);
        player.sendSystemMessage(Component.translatable("mnemolith.message.inventory_full"), true);
    }

    /** The main storage can take every item in {@code stack}. Armor and the offhand are not storage. */
    public static boolean fits(Inventory inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        int remaining = stack.getCount();
        int limit = Math.max(1, Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()));
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && remaining > 0; slot++) {
            ItemStack there = inventory.getItem(slot);
            if (there.isEmpty()) {
                remaining -= limit;
            } else if (ItemStack.isSameItemSameComponents(there, stack)) {
                remaining -= Math.max(0, limit - there.getCount());
            }
        }
        return remaining <= 0;
    }

    /**
     * The result still fits after {@code held} is used up. Survival use of the last item in the main hand frees
     * that slot. Creative play and the offhand do not.
     */
    public static boolean fitsAfterUse(ServerPlayer player, InteractionHand hand, ItemStack held, ItemStack result) {
        if (!player.getAbilities().instabuild && hand == InteractionHand.MAIN_HAND && held.getCount() <= 1) {
            int limit = Math.max(1, Math.min(result.getMaxStackSize(), player.getInventory().getMaxStackSize()));
            if (result.getCount() <= limit) {
                return true;
            }
        }
        return fits(player.getInventory(), result);
    }

    /** Inserts {@code stack} only when it fits entirely. Otherwise tells the player and leaves it untouched. */
    public static boolean give(ServerPlayer player, ItemStack stack) {
        if (!fits(player.getInventory(), stack)) {
            refuse(player);
            return false;
        }
        return player.getInventory().add(stack);
    }
}
