package com.mnemolith.content.item;

import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.ModEffects;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A captured residual echo. Right-click your own echo to graft it (handled by the echo), use it on a block to set
 * the residue free there with its strength kept, or use it on yourself when the tag is fire or fall to take that
 * temper as a short effect. Not craftable: only the needle on a read residue, an archivist that archived one, or a
 * successful composition gives it.
 */
public class ResidualShardItem extends Item {
    public ResidualShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!Residues.isShard(stack)) {
            return InteractionResult.PASS;
        }
        ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
        if (cast == null) {
            return InteractionResult.PASS;
        }
        MobEffectInstance effect = effectFor(cast.tag());
        if (effect == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.addEffect(effect);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("mnemolith.residue.temper_taken",
                    Component.translatable(cast.tag().translationKey())), true);
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
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
            if (context.getPlayer() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("mnemolith.residue.release_failed"), true);
            }
            return InteractionResult.FAIL;
        }
        ServerPlayer releaser = context.getPlayer() instanceof ServerPlayer player ? player : null;
        // In a fractured chunk, a freed shard calls a recollection storm (its own message replaces the release line).
        boolean called = com.mnemolith.echo.storm.Storms.callByShard(level, residue, releaser);
        if (releaser != null && !called) {
            releaser.sendSystemMessage(Component.translatable("mnemolith.residue.released",
                    Component.translatable(residue.tag().translationKey()), residue.strength()), true);
        }
        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.SUCCESS_SERVER;
    }

    private static MobEffectInstance effectFor(ImprintTag tag) {
        return switch (tag) {
            case FIRE -> new MobEffectInstance(ModEffects.FIRE_TRAIL, ImprintConstants.FIRE_TRAIL_DURATION_TICKS, 0, false, true);
            case FALL -> new MobEffectInstance(ModEffects.LANDING_BURST, ImprintConstants.LANDING_BURST_DURATION_TICKS, 0, false, true);
            default -> null;
        };
    }
}
