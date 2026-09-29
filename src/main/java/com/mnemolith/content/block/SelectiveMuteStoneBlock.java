package com.mnemolith.content.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import com.mnemolith.content.ModBlockEntities;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;

/**
 * Mute stone that still lets one imprint tag write. Residues and echo spawns stay blocked via {@code isMuted}.
 * Bind the allowed tag by placing an item that carries {@link ModDataComponents#FILTER_TAG}, or by using an
 * imprint slip on the placed stone.
 */
public class SelectiveMuteStoneBlock extends BaseEntityBlock {
    public static final MapCodec<SelectiveMuteStoneBlock> CODEC = simpleCodec(SelectiveMuteStoneBlock::new);

    public SelectiveMuteStoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SelectiveMuteStoneBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SelectiveMuteStoneBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        ServerLevel server = ServerPlacement.ifFresh(level, this, oldState);
        if (server == null) {
            return;
        }
        ImprintTag allowed = ImprintTag.SILENCE;
        if (server.getBlockEntity(pos) instanceof SelectiveMuteStoneBlockEntity be) {
            allowed = be.allowed().orElse(ImprintTag.SILENCE);
            if (be.allowed().isEmpty()) {
                be.setAllowed(allowed);
            }
        }
        LoadedChunkMemory.addSelectiveMute(server, pos, allowed);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        LoadedChunkMemory.removeSelectiveMute(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }


    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ImprintTag tag = stack.get(ModDataComponents.FILTER_TAG.get());
        if (tag == null) {
            return;
        }
        if (server.getBlockEntity(pos) instanceof SelectiveMuteStoneBlockEntity be) {
            be.setAllowed(tag);
        }
        LoadedChunkMemory.removeSelectiveMute(server, pos);
        LoadedChunkMemory.addSelectiveMute(server, pos, tag);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof SelectiveMuteStoneBlockEntity be)) {
            return InteractionResult.PASS;
        }
        var cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
        if (stack.getItem() != com.mnemolith.content.ModItems.IMPRINT_SLIP.get() || cast == null) {
            return InteractionResult.PASS;
        }
        be.setAllowed(cast.tag());
        LoadedChunkMemory.removeSelectiveMute(server, pos);
        LoadedChunkMemory.addSelectiveMute(server, pos, cast.tag());
        serverPlayer.sendSystemMessage(Component.translatable(
                "mnemolith.message.selective_mute_bound",
                Component.translatable(cast.tag().translationKey())));
        return InteractionResult.SUCCESS_SERVER;
    }
}
