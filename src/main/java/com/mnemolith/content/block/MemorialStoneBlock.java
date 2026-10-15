package com.mnemolith.content.block;

import com.mnemolith.echo.storm.ScarSites;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A memorial stone: a Scar's heart after three tendings with a remembrance posy. No item, no drops. While it stands the
 * chunk keeps its site as a memorial (a storm ward that seeds no residue); breaking it forgets the site.
 */
public class MemorialStoneBlock extends Block {
    public static final MapCodec<MemorialStoneBlock> CODEC = simpleCodec(MemorialStoneBlock::new);

    public MemorialStoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<MemorialStoneBlock> codec() {
        return CODEC;
    }

    @Override
    protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (stack.isEmpty()) {
            return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!stack.has(com.mnemolith.data.ModDataComponents.TRANSCRIPT.get())) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return MemorialNotes.inscribe(server, serverPlayer, pos, stack) ? net.minecraft.world.InteractionResult.SUCCESS_SERVER
                : net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        MemorialNotes.read(server, serverPlayer, pos);
        return net.minecraft.world.InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        ScarSites.heal(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.2D + random.nextDouble() * 0.6D, pos.getY() + 1.05D,
                    pos.getZ() + 0.2D + random.nextDouble() * 0.6D, 0.0D, 0.015D, 0.0D);
        }
    }
}
