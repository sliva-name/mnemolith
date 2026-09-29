package com.mnemolith.content.block;

import java.util.Optional;

import com.mnemolith.content.ModBlockEntities;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Stores the one imprint tag a selective mute stone still allows. */
public class SelectiveMuteStoneBlockEntity extends BlockEntity {
    private Optional<ImprintTag> allowed = Optional.empty();

    public SelectiveMuteStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SELECTIVE_MUTE_STONE.get(), pos, state);
    }

    public Optional<ImprintTag> allowed() {
        return this.allowed;
    }

    public void setAllowed(ImprintTag tag) {
        this.allowed = Optional.ofNullable(tag);
        this.setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.allowed.ifPresent(tag -> output.store("allowed", ImprintTag.CODEC, tag));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.allowed = input.read("allowed", ImprintTag.CODEC);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        ImprintTag tag = components.get(ModDataComponents.FILTER_TAG.get());
        if (tag != null) {
            this.allowed = Optional.of(tag);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        this.allowed.ifPresent(tag -> components.set(ModDataComponents.FILTER_TAG.get(), tag));
    }
}
