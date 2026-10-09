package com.mnemolith.content.item;

import java.util.Optional;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        // instanceof: the recollite lens is a chronicle lens too.
        return entity.getMainHandItem().getItem() instanceof ChronicleLensItem
                || entity.getOffhandItem().getItem() instanceof ChronicleLensItem;
    }

    /** True while {@code entity} holds the lens raised (the thermal echo view). */
    public static boolean isFocusing(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getItem() instanceof ChronicleLensItem;
    }

    /** Filter tag on a held lens, if any. */
    public static Optional<ImprintTag> heldFilter(LivingEntity entity) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = entity.getItemInHand(hand);
            if (stack.getItem() instanceof ChronicleLensItem) {
                ImprintTag tag = stack.get(ModDataComponents.FILTER_TAG.get());
                if (tag != null) {
                    return Optional.of(tag);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            ItemStack lens = player.getItemInHand(hand);
            ItemStack slip = findSlip(player, hand);
            if (!slip.isEmpty()) {
                ImprintCast cast = slip.get(ModDataComponents.IMPRINT_CAST.get());
                if (cast != null) {
                    lens.set(ModDataComponents.FILTER_TAG.get(), cast.tag());
                    serverPlayer.sendSystemMessage(Component.translatable(
                            "mnemolith.message.lens_tuned",
                            Component.translatable(cast.tag().translationKey())));
                    server.playSound(null, player.blockPosition(), ModSounds.LENS_FOCUS.get(), SoundSource.PLAYERS, 0.7F, 1.4F);
                    return InteractionResult.SUCCESS;
                }
            }
            if (lens.has(ModDataComponents.FILTER_TAG.get())) {
                lens.remove(ModDataComponents.FILTER_TAG.get());
                serverPlayer.sendSystemMessage(Component.translatable("mnemolith.message.lens_cleared"));
                server.playSound(null, player.blockPosition(), ModSounds.LENS_FOCUS.get(), SoundSource.PLAYERS, 0.5F, 0.8F);
                return InteractionResult.SUCCESS;
            }
            MomentReplicant.blindNearby(server, player);
            server.playSound(null, player.blockPosition(), ModSounds.LENS_FOCUS.get(), SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    private static ItemStack findSlip(Player player, InteractionHand lensHand) {
        InteractionHand other = lensHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack off = player.getItemInHand(other);
        if (off.is(ModItems.IMPRINT_SLIP.get()) && off.has(ModDataComponents.IMPRINT_CAST.get())) {
            return off;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.IMPRINT_SLIP.get()) && stack.has(ModDataComponents.IMPRINT_CAST.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
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
