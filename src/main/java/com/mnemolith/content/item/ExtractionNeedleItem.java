package com.mnemolith.content.item;

import java.util.Optional;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintWriter;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class ExtractionNeedleItem extends Item {
    public ExtractionNeedleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide() || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        int cooldown = CommonConfig.EXTRACTION_COOLDOWN_TICKS.get();
        if (cooldown > 0 && player.getCooldowns().isOnCooldown(context.getItemInHand())) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.extract_cooldown"));
            return InteractionResult.FAIL;
        }
        Optional<Imprint> extracted = level.getBlockEntity(context.getClickedPos()) instanceof com.mnemolith.content.block.ArchiveVaultBlockEntity vault
                ? com.mnemolith.vault.ArchiveVaults.extract(level, context.getClickedPos(), vault, player)
                : ImprintWriter.extract(level, context.getClickedPos(), player);
        if (extracted.isEmpty()) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.extract_empty"));
            return InteractionResult.FAIL;
        }
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(context.getItemInHand(), cooldown);
        }
        int cost = CommonConfig.EXTRACTION_DURABILITY_COST.get();
        if (cost > 0) {
            context.getItemInHand().hurtAndBreak(cost, level, player, item -> {});
        }
        player.sendSystemMessage(Component.translatable(
                "mnemolith.message.extracted",
                Component.translatable(extracted.get().tag().translationKey()),
                extracted.get().intensity()));
        return InteractionResult.SUCCESS_SERVER;
    }
}
