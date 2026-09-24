package com.mnemolith.content.menu;

import com.mnemolith.content.ModItems;
import com.mnemolith.content.ModMenus;
import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.Composition;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.imprint.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CompositionMenu extends AbstractContainerMenu {
    public static final int DATA_STATUS = 0;
    public static final int DATA_FORMULA = 1;
    public static final int DATA_DISCOVERED = 2;

    private final Container container;
    private final BlockPos blockPos;
    private final boolean boundToBlock;
    private final SimpleContainerData data = new SimpleContainerData(3);

    public CompositionMenu(int containerId, Inventory inventory, Container container) {
        super(ModMenus.COMPOSITION.get(), containerId);
        this.container = container;
        if (container instanceof BlockEntity entity) {
            this.blockPos = entity.getBlockPos();
            this.boundToBlock = true;
        } else {
            this.blockPos = BlockPos.ZERO;
            this.boundToBlock = false;
        }
        checkContainerSize(container, ImprintConstants.COMPOSITION_SLOTS);
        for (int slot = 0; slot < ImprintConstants.COMPOSITION_SLOTS; slot++) {
            this.addSlot(new SlipSlot(container, slot, 62 + slot * 18, 30));
        }
        this.addStandardInventorySlots(inventory, 8, 148);
        this.addDataSlots(this.data);
        this.data.set(DATA_FORMULA, -1);
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            this.publishDiscovery(serverPlayer);
        }
    }

    public int status() {
        return this.data.get(DATA_STATUS);
    }

    public int formulaOrdinal() {
        return this.data.get(DATA_FORMULA);
    }

    public int discoveredFormulas() {
        return this.data.get(DATA_DISCOVERED);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != ImprintConstants.COMPOSE_BUTTON_ID || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(serverPlayer.level() instanceof ServerLevel level) || !this.boundToBlock) {
            return false;
        }
        ComposeResult result = Composition.compose(level, this.blockPos, serverPlayer, this.container);
        this.data.set(DATA_STATUS, result.status());
        this.data.set(DATA_FORMULA, result.formulaOrdinal());
        this.publishDiscovery(serverPlayer);
        return true;
    }

    private void publishDiscovery(ServerPlayer player) {
        Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
        this.data.set(DATA_DISCOVERED, discovery.formulas());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack carried = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        carried = stack.copy();
        int slipEnd = ImprintConstants.COMPOSITION_SLOTS;
        int inventoryEnd = slipEnd + 36;
        if (index < slipEnd) {
            if (!this.moveItemStackTo(stack, slipEnd, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, slipEnd, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return carried;
    }

    private static final class SlipSlot extends Slot {
        private SlipSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() == ModItems.IMPRINT_SLIP.get();
        }
    }
}
