package com.mnemolith.content.item;

import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.echo.ResidueEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A captured residual echo. Right-click your own echo to graft it (handled by the echo), or use it on a block to set
 * the residue free there with its strength kept. Not craftable: only the needle on a read residue, or an archivist
 * that archived one, gives it.
 */
public class ResidualShardItem extends Item {
    public ResidualShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return Residues.isShard(context.getItemInHand()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!Residues.isShard(context.getItemInHand())) {
            return InteractionResult.PASS;
        }
        ResidueEntity residue = Residues.release(level, context.getItemInHand(), context.getClickedPos().relative(context.getClickedFace()));
        if (residue == null) {
            if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("mnemolith.residue.release_failed"), true);
            }
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("mnemolith.residue.released",
                    Component.translatable(residue.tag().translationKey()), residue.strength()), true);
        }
        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.SUCCESS_SERVER;
    }
}
