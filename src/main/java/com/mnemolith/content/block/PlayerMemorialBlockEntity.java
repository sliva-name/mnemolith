package com.mnemolith.content.block;

import java.util.Optional;
import java.util.UUID;

import com.mnemolith.content.ModBlockEntities;
import com.mnemolith.imprint.Imprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Chest-like memorial: keeps a dead player's items and an optional death imprint (lens-readable / needle-extractable).
 */
public class PlayerMemorialBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOTS = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private Optional<UUID> owner = Optional.empty();
    private String ownerName = "";
    private Optional<Imprint> deathImprint = Optional.empty();

    public PlayerMemorialBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLAYER_MEMORIAL.get(), pos, state);
    }

    public Optional<UUID> owner() {
        return this.owner;
    }

    public String ownerName() {
        return this.ownerName;
    }

    public Optional<Imprint> deathImprint() {
        return this.deathImprint;
    }

    public void bind(UUID owner, String name, Imprint death) {
        this.owner = Optional.of(owner);
        this.ownerName = name == null ? "" : name;
        this.deathImprint = Optional.ofNullable(death);
        this.setChanged();
    }

    /** Takes the death imprint for the extraction needle. */
    public Optional<Imprint> takeDeathImprint() {
        Optional<Imprint> taken = this.deathImprint;
        if (taken.isPresent()) {
            this.deathImprint = Optional.empty();
            this.setChanged();
        }
        return taken;
    }

    public boolean offer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i).isEmpty()) {
                this.items.set(i, stack.split(stack.getCount()));
                this.setChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    protected Component getDefaultName() {
        if (!this.ownerName.isEmpty()) {
            return Component.translatable("container.mnemolith.player_memorial.named", this.ownerName);
        }
        return Component.translatable("container.mnemolith.player_memorial");
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
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
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        this.owner.ifPresent(id -> output.store("owner", UUIDUtil.CODEC, id));
        if (!this.ownerName.isEmpty()) {
            output.putString("owner_name", this.ownerName);
        }
        this.deathImprint.ifPresent(imprint -> output.store("death_imprint", Imprint.CODEC, imprint));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.owner = input.read("owner", UUIDUtil.CODEC);
        this.ownerName = input.getStringOr("owner_name", "");
        this.deathImprint = input.read("death_imprint", Imprint.CODEC);
    }
}
