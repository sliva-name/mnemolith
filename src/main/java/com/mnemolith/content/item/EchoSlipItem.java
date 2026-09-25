package com.mnemolith.content.item;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoRecorder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Blank echo slip. Use: starts recording yourself (one slip is consumed). Use again while recording: stop early.
 * The filled {@code echo_recording} is handed over when the time runs out.
 */
public class EchoSlipItem extends Item {
    public EchoSlipItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (EchoRecorder.isRecording(serverPlayer)) {
            EchoRecorder.finish(serverPlayer, "stopped");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!CommonConfig.ECHOES_ENABLED.get()) {
            serverPlayer.sendSystemMessage(Component.translatable("mnemolith.echo.activate_disabled"), true);
            return InteractionResult.FAIL;
        }
        if (EchoPossession.isPossessing(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.translatable("mnemolith.echo.activate_possessing"), true);
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (EchoRecorder.start(serverPlayer)) {
            stack.consume(1, player);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.FAIL;
    }
}
