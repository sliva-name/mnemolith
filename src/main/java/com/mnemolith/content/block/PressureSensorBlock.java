package com.mnemolith.content.block;

import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/** Reads the chunk's memory pressure and pushes a redstone signal. Analog output follows the raw score. */
public class PressureSensorBlock extends Block {
    public static final MapCodec<PressureSensorBlock> CODEC = simpleCodec(PressureSensorBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public PressureSensorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    public MapCodec<PressureSensorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int power = computePower(level, pos);
        if (state.getValue(POWER) != power) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, 10);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(POWER);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? state.getSignal(level, pos, direction) : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        if (memory == null) {
            return 0;
        }
        int pressure = memory.cachedPressure();
        int cap = Math.max(1, com.mnemolith.config.CommonConfig.FRACTURE_THRESHOLD.get());
        return Math.min(15, (pressure * 15) / cap);
    }

    private static int computePower(Level level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        if (memory == null) {
            return 0;
        }
        PressureBand band = MemoryPressure.band(memory.cachedPressure());
        return switch (band) {
            case CALM -> 0;
            case SATURATED -> 5;
            case OVERLOADED -> 10;
            case FRACTURE -> 15;
        };
    }
}
