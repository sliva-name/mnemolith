package com.mnemolith.content.item;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.OpenCatalogPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opens the discovery catalog. The screen is created on the client from {@link OpenCatalogPayload}. */
public class CatalogFragmentItem extends Item {
    public CatalogFragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!CommonConfig.CATALOG_ENABLED.get()) {
            serverPlayer.sendOverlayMessage(Component.translatable("mnemolith.message.catalog_disabled"));
            return InteractionResult.FAIL;
        }
        PacketDistributor.sendToPlayer(serverPlayer, payloadFor(serverPlayer));
        return InteractionResult.SUCCESS;
    }

    /** The bits the catalog screen is allowed to draw. Undiscovered tags and formulas stay clear. */
    public static OpenCatalogPayload payloadFor(ServerPlayer player) {
        Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
        return new OpenCatalogPayload(discovery.tags(), discovery.formulas());
    }
}
