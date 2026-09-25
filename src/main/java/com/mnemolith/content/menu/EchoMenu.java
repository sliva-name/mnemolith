package com.mnemolith.content.menu;

import org.jspecify.annotations.Nullable;

import com.mnemolith.content.ModMenus;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * The echo's own inventory, laid out like a player's: 36 main slots, four armor slots, one offhand slot.
 * Menu slot {@code i} is echo slot {@code i} for {@code i < 41}; the viewer's inventory follows.
 * Items move through vanilla slot logic, so a stack is always in exactly one place.
 */
public class EchoMenu extends AbstractContainerMenu {
    public static final int ECHO_SLOTS = EchoInventory.SIZE;
    public static final int MAIN_X = 30;
    public static final int MAIN_Y = 18;
    public static final int HOTBAR_Y = 76;
    public static final int GEAR_X = 8;
    public static final int OFFHAND_Y = 96;
    public static final int PLAYER_Y = 118;
    public static final int WIDTH = 200;
    public static final int HEIGHT = 200;

    private final Container container;
    private final @Nullable EchoEntity echo;
    /** The echo's lesson as sent in the open packet (client), or read from the echo (server). */
    private com.mnemolith.echo.EchoLesson lesson = com.mnemolith.echo.EchoLesson.NONE;

    public EchoMenu(int containerId, Inventory inventory, Container container, @Nullable EchoEntity echo) {
        super(ModMenus.ECHO.get(), containerId);
        checkContainerSize(container, ECHO_SLOTS);
        this.container = container;
        this.echo = echo;
        container.startOpen(inventory.player);
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            int x = MAIN_X + (i % 9) * 18;
            int y = i < 9 ? HOTBAR_Y : MAIN_Y + (i / 9 - 1) * 18;
            this.addSlot(new Slot(container, i, x, y));
        }
        EquipmentSlot[] armor = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        for (int i = 0; i < 4; i++) {
            // Head at the top, feet at the bottom, like the player screen.
            this.addSlot(new ArmorSlot(container, Inventory.INVENTORY_SIZE + i, GEAR_X, MAIN_Y + (3 - i) * 18, armor[i]));
        }
        this.addSlot(new Slot(container, EchoInventory.OFFHAND, GEAR_X, OFFHAND_Y));
        this.addStandardInventorySlots(inventory, MAIN_X, PLAYER_Y);
    }

    /** Client constructor: the entity id comes from the open packet. */
    public static EchoMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        EchoEntity echo = inventory.player.level().getEntity(id) instanceof EchoEntity found ? found : null;
        EchoMenu menu = new EchoMenu(containerId, inventory, new SimpleContainer(ECHO_SLOTS), echo);
        if (buffer.isReadable()) {
            menu.lesson = com.mnemolith.echo.EchoLesson.STREAM_CODEC.decode(buffer);
        }
        return menu;
    }

    public com.mnemolith.echo.EchoLesson lesson() {
        return this.echo != null && !this.echo.level().isClientSide() ? this.echo.job().lesson() : this.lesson;
    }

    public @Nullable EchoEntity echo() {
        return this.echo;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        int playerStart = ECHO_SLOTS;
        int playerEnd = playerStart + Inventory.INVENTORY_SIZE;
        if (index < ECHO_SLOTS) {
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            EquipmentSlot target = armorSlotOf(stack);
            boolean placed = false;
            if (target != null && target.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                int armorIndex = Inventory.INVENTORY_SIZE + target.getIndex();
                placed = this.moveItemStackTo(stack, armorIndex, armorIndex + 1, false);
            }
            if (!placed && !this.moveItemStackTo(stack, 0, Inventory.INVENTORY_SIZE, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return moved;
    }

    static @Nullable EquipmentSlot armorSlotOf(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null ? null : equippable.slot();
    }

    private static final class ArmorSlot extends Slot {
        private final EquipmentSlot equipment;

        private ArmorSlot(Container container, int slot, int x, int y, EquipmentSlot equipment) {
            super(container, slot, x, y);
            this.equipment = equipment;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return armorSlotOf(stack) == this.equipment;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
