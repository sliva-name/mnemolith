package com.mnemolith.content.block;

import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Decorative lamp whose light and band property follow chunk memory pressure (G3, ties P1). */
public class PressureLampBlock extends Block {
    public static final MapCodec<PressureLampBlock> CODEC = simpleCodec(PressureLampBlock::new);
    /** Ordinal of {@link PressureBand}. */
    public static final IntegerProperty BAND = IntegerProperty.create("band", 0, 3);
    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 14.0D, 12.0D);

    public PressureLampBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(BAND, 0));
    }

    @Override
    public MapCodec<PressureLampBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BAND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        PressureBand band = memory == null ? PressureBand.CALM : MemoryPressure.band(memory.cachedPressure());
        int ordinal = band.ordinal();
        if (state.getValue(BAND) != ordinal) {
            level.setBlock(pos, state.setValue(BAND, ordinal), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, 20);
    }

    public static int lightFor(BlockState state) {
        return switch (state.getValue(BAND)) {
            case 1 -> 8;
            case 2 -> 12;
            case 3 -> 15;
            default -> 4;
        };
    }
}
