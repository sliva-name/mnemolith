package com.mnemolith.entity.echo;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Player-shaped view over an echo's items: 0-35 main (0-8 hotbar), 36 feet, 37 legs, 38 chest, 39 head, 40 offhand.
 * The same indices as {@link Inventory}, so a possession swap is slot-for-slot.
 */
public final class EchoInventory implements Container {
    public static final int SIZE = 41;
    public static final int MAIN = Inventory.INVENTORY_SIZE;
    public static final int OFFHAND = Inventory.SLOT_OFFHAND;
    private final EchoEntity echo;

    public EchoInventory(EchoEntity echo) {
        this.echo = echo;
    }

    public static EquipmentSlot equipmentSlot(int index) {
        return switch (index) {
            case 36 -> EquipmentSlot.FEET;
            case 37 -> EquipmentSlot.LEGS;
            case 38 -> EquipmentSlot.CHEST;
            case 39 -> EquipmentSlot.HEAD;
            case 40 -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (!this.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot >= 0 && slot < MAIN) {
            return this.echo.mainItems().get(slot);
        }
        EquipmentSlot equipment = equipmentSlot(slot);
        return equipment == null ? ItemStack.EMPTY : this.echo.getItemBySlot(equipment);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack stack = this.getItem(slot);
        if (stack.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack split = stack.split(count);
        if (stack.isEmpty()) {
            this.setItem(slot, ItemStack.EMPTY);
        }
        this.setChanged();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = this.getItem(slot);
        this.setItem(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < MAIN) {
            this.echo.mainItems().set(slot, stack);
            return;
        }
        EquipmentSlot equipment = equipmentSlot(slot);
        if (equipment != null) {
            this.echo.setItemSlot(equipment, stack);
        }
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return this.echo.isAlive() && !this.echo.isRemoved() && this.echo.isOwnedBy(player) && player.distanceToSqr(this.echo) <= 64.0D;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < SIZE; i++) {
            this.setItem(i, ItemStack.EMPTY);
        }
    }

    /** Puts {@code stack} into matching stacks, then empty main slots. Whatever does not fit stays in {@code stack}. */
    public void insert(ItemStack stack) {
        for (int i = 0; i < MAIN && !stack.isEmpty(); i++) {
            ItemStack existing = this.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int room = Math.min(existing.getMaxStackSize(), this.getMaxStackSize()) - existing.getCount();
                if (room > 0) {
                    int moved = Math.min(room, stack.getCount());
                    existing.grow(moved);
                    stack.shrink(moved);
                }
            }
        }
        for (int i = 0; i < MAIN && !stack.isEmpty(); i++) {
            if (this.getItem(i).isEmpty()) {
                this.setItem(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
            }
        }
    }

    /** Main slot holding {@code item}, hotbar first. -1 when none. */
    public int find(net.minecraft.world.item.Item item) {
        for (int i = 0; i < MAIN; i++) {
            if (this.getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    public int totalCount() {
        int total = 0;
        for (int i = 0; i < SIZE; i++) {
            total += this.getItem(i).getCount();
        }
        return total;
    }
}
