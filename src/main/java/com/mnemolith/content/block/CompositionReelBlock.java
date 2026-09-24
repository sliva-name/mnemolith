package com.mnemolith.content.block;

import org.jspecify.annotations.Nullable;

import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.DiscoveryNotes;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CompositionReelBlock extends BaseEntityBlock {
    public static final MapCodec<CompositionReelBlock> CODEC = simpleCodec(CompositionReelBlock::new);

    public CompositionReelBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<CompositionReelBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompositionReelBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof CompositionReelBlockEntity reel) {
                ChunkMemory memory = LoadedChunkMemory.existing(serverLevel.getChunkAt(pos));
                if (memory != null && memory.observatory()) {
                    DiscoveryNotes.noteObservatory(serverPlayer);
                }
                serverPlayer.openMenu(reel);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
