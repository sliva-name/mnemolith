package com.mnemolith.content.block;

import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.storm.ArchiveShrines;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Archive shrine (B4): found in hush chapels and flooded / ashen archives. Right-click challenges the Archive Guardian
 * once — deterministic spawn, arena ring, unique reward. Never uses the recollection-storm dice roll.
 */
public class ArchiveShrineBlock extends Block {
    public static final MapCodec<ArchiveShrineBlock> CODEC = simpleCodec(ArchiveShrineBlock::new);
    public static final BooleanProperty CHALLENGED = BooleanProperty.create("challenged");
    public static final BooleanProperty CLAIMED = BooleanProperty.create("claimed");
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 15.0D, 14.0D);

    public ArchiveShrineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CHALLENGED, false).setValue(CLAIMED, false));
    }

    @Override
    public MapCodec<ArchiveShrineBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHALLENGED, CLAIMED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            ArchiveShrines.challenge(server, pos, state, serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(state.getValue(CLAIMED) ? 6 : 3) != 0) {
            return;
        }
        Temper temper = state.getValue(CLAIMED) ? Temper.HUSHED : Temper.DEEP;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 0.5D + random.nextDouble() * 1.2D;
        level.addParticle(EchoGrafts.particle(temper),
                pos.getX() + 0.5D + Math.cos(angle) * radius,
                pos.getY() + 0.3D + random.nextDouble() * 1.4D,
                pos.getZ() + 0.5D + Math.sin(angle) * radius,
                -Math.cos(angle) * 0.02D, 0.02D, -Math.sin(angle) * 0.02D);
    }
}
