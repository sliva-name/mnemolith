package com.mnemolith.world;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.PlayerMemorialBlockEntity;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Places a chest-like memorial on player death when enabled (P4). Captures drops into the block entity
 * and stores a death imprint for the lens / extraction needle. Default off to avoid fighting tombstone mods.
 */
public final class PlayerMemorials {
    private PlayerMemorials() {}

    public static boolean tryPlace(ServerPlayer player, Collection<ItemEntity> drops) {
        if (!CommonConfig.MEMORIAL_ENABLED.get() || player instanceof FakePlayer) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return false;
        }
        BlockPos spot = findSpot(level, player.blockPosition());
        if (spot == null) {
            return false;
        }
        BlockState state = ModBlocks.PLAYER_MEMORIAL.get().defaultBlockState();
        if (!level.setBlock(spot, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (!(level.getBlockEntity(spot) instanceof PlayerMemorialBlockEntity memorial)) {
            return false;
        }
        Imprint death = new Imprint(
                ImprintTag.DEATH,
                3,
                spot.immutable(),
                Optional.of(player.getUUID()),
                Imprint.contextHash(ImprintTag.DEATH, spot, level.getGameTime()),
                level.getGameTime());
        memorial.bind(player.getUUID(), player.getGameProfile().name(), death);
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            if (memorial.offer(copy)) {
                entity.discard();
            } else if (!copy.isEmpty()) {
                // Overflow: leave the remainder as a world drop next to the memorial.
                entity.setItem(copy);
                entity.setPos(spot.getX() + 0.5D, spot.getY() + 1.0D, spot.getZ() + 0.5D);
            }
        }
        drops.removeIf(ItemEntity::isRemoved);
        Mnemolith.LOGGER.info("Mnemolith memorial for {} at {}", player.getGameProfile().name(), spot.toShortString());
        return true;
    }

    private static @org.jspecify.annotations.Nullable BlockPos findSpot(ServerLevel level, BlockPos death) {
        BlockPos base = death;
        if (!replaceable(level, base)) {
            for (Direction direction : Direction.values()) {
                BlockPos beside = death.relative(direction);
                if (replaceable(level, beside)) {
                    base = beside;
                    break;
                }
            }
        }
        if (!replaceable(level, base)) {
            base = death.above();
        }
        if (!replaceable(level, base)) {
            return null;
        }
        // Prefer standing on solid ground.
        if (!level.getBlockState(base.below()).isSolidRender()) {
            BlockPos down = base.below();
            if (replaceable(level, down) && level.getBlockState(down.below()).isSolidRender()) {
                return down;
            }
        }
        return base;
    }

    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || state.is(Blocks.WATER) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS);
    }
}
