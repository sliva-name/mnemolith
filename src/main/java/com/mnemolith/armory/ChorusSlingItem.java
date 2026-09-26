package com.mnemolith.armory;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.armory.MemoryBolt;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Draws like a bow and throws a memory bolt. The bolt marks the nearest owned echo to walk to the hit. */
public class ChorusSlingItem extends Item {
    public ChorusSlingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (findBolt(player).isEmpty()) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) {
            return false;
        }
        int used = this.getUseDuration(stack, entity) - remainingTime;
        if (used < 8) {
            return false;
        }
        ItemStack bolt = findBolt(player);
        if (bolt.isEmpty()) {
            return false;
        }
        float power = Math.min(1.0F, used / 20.0F);
        MemoryBolt shot = new MemoryBolt(server, player, bolt.copyWithCount(1));
        shot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 1.6F, 1.0F);
        server.addFreshEntity(shot);
        if (!player.getAbilities().instabuild) {
            bolt.shrink(1);
            stack.hurtAndBreak(1, server, player, item -> {});
        }
        server.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8F, 1.3F);
        return true;
    }

    /** Nearest living echo of this player, used when the bolt lands. */
    public static EchoEntity nearestEcho(ServerLevel level, ServerPlayer player) {
        EchoEntity best = null;
        double bestDistance = 48.0D * 48.0D;
        for (EchoRegistry.Entry entry : EchoRegistry.get(level.getServer()).entries(player.getUUID())) {
            if (level.getEntity(entry.echo()) instanceof EchoEntity echo && echo.isAlive() && echo.distanceToSqr(player) < bestDistance) {
                best = echo;
                bestDistance = echo.distanceToSqr(player);
            }
        }
        return best;
    }

    private static ItemStack findBolt(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ArmoryItems.MEMORY_BOLT.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
