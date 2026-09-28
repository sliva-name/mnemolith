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
    private final boolean reinforced;
    private final boolean twin;

    public ExtractionNeedleItem(Properties properties) {
        this(properties, false, false);
    }

    public ExtractionNeedleItem(Properties properties, boolean reinforced, boolean twin) {
        super(properties);
        this.reinforced = reinforced;
        this.twin = twin;
    }

    public boolean reinforced() {
        return this.reinforced;
    }

    public boolean twin() {
        return this.twin;
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
        int pulls = this.twin ? 2 : 1;
        int extracted = 0;
        Imprint last = null;
        for (int i = 0; i < pulls; i++) {
            com.mnemolith.content.InventorySpace.clearRefused();
            Optional<Imprint> result = level.getBlockEntity(context.getClickedPos()) instanceof com.mnemolith.content.block.ArchiveVaultBlockEntity vault
                    ? com.mnemolith.vault.ArchiveVaults.extract(level, context.getClickedPos(), vault, player)
                    : ImprintWriter.extract(level, context.getClickedPos(), player);
            if (result.isEmpty()) {
                if (extracted == 0 && !com.mnemolith.content.InventorySpace.consumeRefused()) {
                    player.sendSystemMessage(Component.translatable("mnemolith.message.extract_empty"));
                }
                break;
            }
            extracted++;
            last = result.get();
        }
        if (extracted == 0) {
            return InteractionResult.FAIL;
        }
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(context.getItemInHand(), cooldown);
        }
        if (!this.reinforced) {
            int cost = CommonConfig.EXTRACTION_DURABILITY_COST.get();
            if (cost > 0) {
                context.getItemInHand().hurtAndBreak(cost, level, player, item -> {});
            }
        }
        if (last != null) {
            if (extracted > 1) {
                player.sendSystemMessage(Component.translatable(
                        "mnemolith.message.extracted_multi",
                        extracted,
                        Component.translatable(last.tag().translationKey()),
                        last.intensity()));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "mnemolith.message.extracted",
                        Component.translatable(last.tag().translationKey()),
                        last.intensity()));
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
