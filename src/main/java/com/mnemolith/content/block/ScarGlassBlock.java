package com.mnemolith.content.block;

import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scar glass: memory that set like glass where a recollection storm merged into the Scar. It only grows at a Scar
 * site (never crafted). Placed anywhere, it is a storm ward: no recollection storm can gather within one chunk of it,
 * while imprints are written as usual (unlike a mute stone). Registered on the chunk memory like the resonator.
 */
public class ScarGlassBlock extends TransparentBlock {
    public static final MapCodec<ScarGlassBlock> CODEC = simpleCodec(ScarGlassBlock::new);

    public ScarGlassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ScarGlassBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        ServerLevel server = ServerPlacement.ifFresh(level, this, oldState);
        if (server != null) {
            LoadedChunkMemory.addWard(server, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        LoadedChunkMemory.removeWard(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
