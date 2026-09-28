package com.mnemolith.content.block;

import org.jspecify.annotations.Nullable;

import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoRoles;
import com.mnemolith.entity.echo.EchoEntity;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pedestal where idle echoes sleep without a live body (O3). Use to house the nearest idle owned echo,
 * or to wake / role-assign a stored one.
 */
public class EchoHomeBlock extends BaseEntityBlock {
    public static final MapCodec<EchoHomeBlock> CODEC = simpleCodec(EchoHomeBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 2.0D, 15.0D),
            Block.box(3.0D, 2.0D, 3.0D, 13.0D, 10.0D, 13.0D),
            Block.box(2.0D, 10.0D, 2.0D, 14.0D, 12.0D, 14.0D));

    public EchoHomeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<EchoHomeBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EchoHomeBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }



    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof EchoHomeBlockEntity home)) {
            return InteractionResult.SUCCESS;
        }
        var taught = EchoRoles.roleFor(stack);
        if (taught != null && home.size() > 0) {
            home.assignRole(0, taught);
            serverPlayer.sendSystemMessage(Component.translatable("mnemolith.echo.home.role",
                    Component.translatable("mnemolith.echo.role." + taught.name().toLowerCase(java.util.Locale.ROOT))), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof EchoHomeBlockEntity home)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            // House nearest idle owned echo within 4 blocks.
            EchoEntity nearest = null;
            double best = 16.0D;
            for (EchoEntity echo : serverLevel.getEntitiesOfClass(EchoEntity.class, new AABB(pos).inflate(4.0D))) {
                if (!echo.isOwnedBy(serverPlayer) || echo.isReplaying() || !echo.isAlive()) {
                    continue;
                }
                double d = echo.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                if (d < best) {
                    best = d;
                    nearest = echo;
                }
            }
            if (nearest == null) {
                serverPlayer.sendSystemMessage(Component.translatable("mnemolith.echo.home.none_near"), true);
                return InteractionResult.SUCCESS_SERVER;
            }
            return EchoLife.house(serverPlayer, nearest, home) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
        }
        if (home.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable("mnemolith.echo.home.empty"), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        return EchoLife.wake(serverPlayer, home, pos.above()) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
    }
}
