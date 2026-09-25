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
import net.minecraft.world.level.Level;

public class ChronicleLensItem extends Item {
    public ChronicleLensItem(Properties properties) {
        super(properties);
    }

    public static boolean isHeld(LivingEntity entity) {
        return entity.getMainHandItem().getItem() == ModItems.CHRONICLE_LENS.get()
                || entity.getOffhandItem().getItem() == ModItems.CHRONICLE_LENS.get();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            MomentReplicant.blindNearby(server, player);
            server.playSound(null, player.blockPosition(), ModSounds.LENS_FOCUS.get(), SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }
}
