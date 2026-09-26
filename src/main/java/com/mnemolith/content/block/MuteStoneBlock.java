package com.mnemolith.content.block;

import com.mojang.serialization.MapCodec;

import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Suppresses imprint writes in its chunk, and in a config radius of loaded chunks. */
public class MuteStoneBlock extends Block {
    public static final MapCodec<MuteStoneBlock> CODEC = simpleCodec(MuteStoneBlock::new);

    public MuteStoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<MuteStoneBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        ServerLevel server = ServerPlacement.ifFresh(level, this, oldState);
        if (server == null) {
            return;
        }
        // A pushed stone keeps muting at its new spot, but only a real placement writes silence. Otherwise a piston
        // loop would be an endless silence farm.
        if (!ServerPlacement.byPiston(oldState, movedByPiston)) {
            ImprintWriter.tryWrite(server, pos, ImprintTag.SILENCE, null, false);
        }
        LoadedChunkMemory.addMuteStone(server, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        LoadedChunkMemory.removeMuteStone(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
