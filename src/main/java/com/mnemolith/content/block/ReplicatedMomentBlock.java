package com.mnemolith.content.block;

import com.mojang.serialization.MapCodec;

import com.mnemolith.particle.MemoryFx;
import com.mnemolith.particle.ModParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a moment replicant leaves when it copies a block place: a glassy echo of the moment, not the block itself.
 * It has no loot table and no item, and it fades {@link #LIFETIME_TICKS} after it appears, so a caged replicant
 * cannot be farmed for copies of whatever the player placed.
 */
public class ReplicatedMomentBlock extends TransparentBlock {
    public static final MapCodec<ReplicatedMomentBlock> CODEC = simpleCodec(ReplicatedMomentBlock::new);
    /** Ten seconds. A scheduled tick is saved with the chunk, so an unloaded echo still fades. */
    public static final int LIFETIME_TICKS = 200;

    public ReplicatedMomentBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ReplicatedMomentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && oldState.getBlock() != this) {
            level.scheduleTick(pos, this, LIFETIME_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
        MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4);
    }
}
