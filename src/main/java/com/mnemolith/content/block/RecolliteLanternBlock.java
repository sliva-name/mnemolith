package com.mnemolith.content.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The recollite lantern as a framed block (hollowstone base and cap, corner posts, glowing core) rather than a
 * plain cube. The shape follows the model: solid base and cap with the post-and-core body between.
 */
public class RecolliteLanternBlock extends Block {
    public static final MapCodec<RecolliteLanternBlock> CODEC = simpleCodec(RecolliteLanternBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 2.0D, 14.0D),
            Block.box(2.5D, 2.0D, 2.5D, 13.5D, 12.0D, 13.5D),
            Block.box(1.5D, 12.0D, 1.5D, 14.5D, 16.0D, 14.5D));

    public RecolliteLanternBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<RecolliteLanternBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
