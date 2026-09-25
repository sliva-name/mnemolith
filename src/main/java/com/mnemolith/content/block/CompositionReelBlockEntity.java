package com.mnemolith.content.block;

import com.mnemolith.content.ModBlockEntities;
import com.mnemolith.content.menu.CompositionMenu;
import com.mnemolith.imprint.ImprintConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
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
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }
}
