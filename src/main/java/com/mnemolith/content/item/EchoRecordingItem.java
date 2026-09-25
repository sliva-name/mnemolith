package com.mnemolith.content.item;

import com.mnemolith.echo.EchoLife;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A filled echo recording. Use: an echo appears where the recording began and replays it. Use on your echo: re-teach it. */
public class EchoRecordingItem extends Item {
    public EchoRecordingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        return EchoLife.activate(serverPlayer, player.getItemInHand(hand)) == EchoLife.SpawnResult.SPAWNED
                ? InteractionResult.SUCCESS_SERVER
                : InteractionResult.FAIL;
    }
}
