package com.mnemolith.content.block;

import com.mojang.serialization.MapCodec;

import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Vein block. Its positions add a capped pressure bleed and extend a lens read. */
public class ArchivalStratumBlock extends Block {
    public static final MapCodec<ArchivalStratumBlock> CODEC = simpleCodec(ArchivalStratumBlock::new);

    public ArchivalStratumBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ArchivalStratumBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide() || oldState.getBlock() == this || !(level instanceof ServerLevel server)) {
            return;
        }
        LevelChunk chunk = server.getChunkAt(pos);
        if (LoadedChunkMemory.noteStratum(chunk, pos)) {
            MemoryPressure.recompute(chunk, LoadedChunkMemory.getOrCreate(chunk));
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (LoadedChunkMemory.forgetStratum(chunk, pos)) {
            ChunkMemory memory = LoadedChunkMemory.existing(chunk);
            if (memory != null) {
                MemoryPressure.recompute(chunk, memory);
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
