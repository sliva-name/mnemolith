package com.mnemolith.content.block;

import com.mnemolith.content.composition.Composition;

import com.mnemolith.content.ModBlockEntities;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.menu.CompositionMenu;
import com.mnemolith.imprint.ImprintConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CompositionReelBlockEntity extends BaseContainerBlockEntity {
    private static final Component NAME = Component.translatable("container.mnemolith.composition_reel");

    private NonNullList<ItemStack> items = NonNullList.withSize(ImprintConstants.COMPOSITION_SLOTS, ItemStack.EMPTY);
    /** A slip left in the old third slot. Dropped once the chunk loads, so a saved reel does not delete it. */
    private ItemStack legacyExtra = ItemStack.EMPTY;

    public CompositionReelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPOSITION_REEL.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return NAME;
    }

    @Override
    public int getContainerSize() {
        return ImprintConstants.COMPOSITION_SLOTS;
    }

    /** Hoppers and droppers use this. The menu slot check does not apply to them. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return Composition.composable(stack);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CompositionMenu(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        NonNullList<ItemStack> loaded = NonNullList.withSize(3, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, loaded);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < this.items.size(); slot++) {
            this.items.set(slot, loaded.get(slot));
        }
        // The old layout kept a non-slip in the third slot; only that is put out on load. A slip there stays a slip.
        ItemStack third = this.items.get(2);
        if (!third.isEmpty() && !Composition.composable(third)) {
            this.legacyExtra = third;
            this.items.set(2, ItemStack.EMPTY);
        } else {
            this.legacyExtra = ItemStack.EMPTY;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.legacyExtra.isEmpty() || this.level == null || this.level.isClientSide()) {
            return;
        }
        ItemStack extra = this.legacyExtra;
        this.legacyExtra = ItemStack.EMPTY;
        Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D, extra);
        this.setChanged();
    }
}
