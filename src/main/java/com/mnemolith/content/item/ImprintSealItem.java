package com.mnemolith.content.item;

import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.ImprintWriter;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Prints an imprint slip's cast into the clicked block position. Consumes the seal and one slip.
 * Respects selective / full mute rules via {@link ImprintWriter#write}.
 */
public class ImprintSealItem extends Item {
    public ImprintSealItem(Properties properties) {
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
        ItemStack seal = context.getItemInHand();
        ItemStack slip = findSlip(player, context.getHand());
        if (slip.isEmpty()) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.seal_need_slip"));
            return InteractionResult.FAIL;
        }
        ImprintCast cast = slip.get(ModDataComponents.IMPRINT_CAST.get());
        if (cast == null) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.seal_need_slip"));
            return InteractionResult.FAIL;
        }
        boolean wrote = ImprintWriter.write(level, context.getClickedPos(), java.util.List.of(cast.tag()), player.getUUID(), false);
        if (!wrote) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.seal_muted"));
            return InteractionResult.FAIL;
        }
        seal.consume(1, player);
        slip.consume(1, player);
        player.sendSystemMessage(Component.translatable(
                "mnemolith.message.sealed",
                Component.translatable(cast.tag().translationKey())));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static ItemStack findSlip(ServerPlayer player, InteractionHand sealHand) {
        InteractionHand other = sealHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
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
}
