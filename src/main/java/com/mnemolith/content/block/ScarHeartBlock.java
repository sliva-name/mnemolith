package com.mnemolith.content.block;

import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.storm.ScarSites;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Scar's heart: the knot of memory left at the centre of a Scar site. It has no item and drops nothing. While it
 * stands the site keeps seeding its old residue and keeps storms away; breaking it heals the scar (the chunk forgets
 * the site). Only placed by a Scar forming with mobGriefing on.
 */
public class ScarHeartBlock extends Block {
    public static final MapCodec<ScarHeartBlock> CODEC = simpleCodec(ScarHeartBlock::new);
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 15.0D, 14.0D);

    public ScarHeartBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ScarHeartBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        ScarSites.heal(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }
        Temper[] tempers = Temper.values();
        Temper temper = tempers[random.nextInt(tempers.length)];
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 0.6D + random.nextDouble() * 1.4D;
        level.addParticle(EchoGrafts.particle(temper), pos.getX() + 0.5D + Math.cos(angle) * radius, pos.getY() + 0.2D + random.nextDouble() * 1.6D,
                pos.getZ() + 0.5D + Math.sin(angle) * radius, -Math.cos(angle) * 0.03D, 0.02D, -Math.sin(angle) * 0.03D);
    }
}
