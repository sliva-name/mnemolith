package com.mnemolith.content.item;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.entity.mob.MomentReplicant;

import com.mnemolith.content.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class ChronicleLensItem extends Item {
    /** Holding use (without sneaking) keeps the lens raised: the client shows the thermal echo view meanwhile. */
    public static final int FOCUS_DURATION = 72000;

    public ChronicleLensItem(Properties properties) {
        super(properties);
    }

    public static boolean isHeld(LivingEntity entity) {
        return entity.getMainHandItem().getItem() == ModItems.CHRONICLE_LENS.get()
                || entity.getOffhandItem().getItem() == ModItems.CHRONICLE_LENS.get();
    }

    /** True while {@code entity} holds the lens raised (the thermal echo view). */
    public static boolean isFocusing(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getItem() == ModItems.CHRONICLE_LENS.get();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel server) {
            MomentReplicant.blindNearby(server, player);
            server.playSound(null, player.blockPosition(), ModSounds.LENS_FOCUS.get(), SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return FOCUS_DURATION;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.SPYGLASS;
    }
}
