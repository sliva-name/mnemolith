package com.mnemolith.content.item;

import com.mnemolith.recall.Intervene;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The Scar's fragment. On an echo it still scar-sets (that path is the echo's interact, not this one).
 * On a block it rewrites the loudest imprint in reach. No chat: the place itself is the answer.
 */
public class ScarFragmentItem extends Item {
    public ScarFragmentItem(Properties properties) {
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
        if (!Intervene.tryUse(player, level, context.getClickedPos())) {
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }
}
