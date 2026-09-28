package com.mnemolith.content.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Simple chest-like memorial placed on player death when {@code memorial.enabled} is on. */
public class PlayerMemorialBlock extends BaseEntityBlock {
    public static final MapCodec<PlayerMemorialBlock> CODEC = simpleCodec(PlayerMemorialBlock::new);

    public PlayerMemorialBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<PlayerMemorialBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerMemorialBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof PlayerMemorialBlockEntity memorial) {
            serverPlayer.openMenu(memorial);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof PlayerMemorialBlockEntity memorial) {
            int filled = 0;
            for (int i = 0; i < memorial.getContainerSize(); i++) {
                if (!memorial.getItem(i).isEmpty()) {
                    filled++;
                }
            }
            return Math.min(15, (filled * 15) / Math.max(1, memorial.getContainerSize()));
        }
        return 0;
    }
}
