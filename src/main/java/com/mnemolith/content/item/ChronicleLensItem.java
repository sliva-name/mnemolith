package com.mnemolith.content.item;

import com.mnemolith.entity.mob.MomentReplicant;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class ChronicleLensItem extends Item {
    public ChronicleLensItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            MomentReplicant.blindNearby(server, player);
        }
        return InteractionResult.SUCCESS;
    }
}
