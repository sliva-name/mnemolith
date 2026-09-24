package com.mnemolith.content.block;

import com.mojang.serialization.MapCodec;

import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A placed trap. Archivists that walk into its range stop and forget the slip they wanted. */
public class ResonatorTrapBlock extends Block {
    public static final MapCodec<ResonatorTrapBlock> CODEC = simpleCodec(ResonatorTrapBlock::new);

    public ResonatorTrapBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ResonatorTrapBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide() || oldState.getBlock() == this || !(level instanceof ServerLevel server)) {
            return;
        }
        LoadedChunkMemory.addResonator(server, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        LoadedChunkMemory.removeResonator(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
