package com.mnemolith.content.block;

import com.mnemolith.content.ModBlockEntities;
import com.mnemolith.vault.ArchiveVaults;
import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Archive vault. Right-click (empty hand) toggles drawing; sneak-right-click discharges everything into this chunk;
 * the extraction needle takes the loudest stored imprint as a slip. Broken, it keeps its imprints on the item; blown
 * up, it spills them. Rules in {@link ArchiveVaults}.
 */
public final class ArchiveVaultBlock extends BaseEntityBlock {
    public static final MapCodec<ArchiveVaultBlock> CODEC = simpleCodec(ArchiveVaultBlock::new);
    public static final BooleanProperty DRAWING = BooleanProperty.create("drawing");

    public ArchiveVaultBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DRAWING, false));
    }

    @Override
    public MapCodec<ArchiveVaultBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DRAWING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArchiveVaultBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.ARCHIVE_VAULT.get(), ArchiveVaultBlockEntity::serverTick);
    }

    /** Anything in hand (the needle, a block to place) goes to the item; only an empty hand works the vault itself. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hitResult) {
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ArchiveVaultBlockEntity vault) {
            if (serverPlayer.isShiftKeyDown()) {
                ArchiveVaults.discharge(server, pos, vault, serverPlayer);
            } else {
                ArchiveVaults.toggle(server, pos, state, vault, serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (explosion.getBlockInteraction() != Explosion.BlockInteraction.KEEP) {
            if (level.getBlockEntity(pos) instanceof ArchiveVaultBlockEntity vault && !vault.isEmpty()) {
                ArchiveVaults.spill(level, pos, vault, vault.count(), "explosion");
            }
        }
        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ArchiveVaults.refreshLoad(level, pos);
    }
}
