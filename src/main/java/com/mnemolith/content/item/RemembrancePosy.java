package com.mnemolith.content.item;

import java.util.List;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.entity.mob.Faded;
import com.mnemolith.imprint.ImprintWriter;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The remembrance posy: forget-me-nots tied with string, given to a faded. A faded is a figure its place half forgot;
 * someone remembering it is enough. A plain faded is laid to rest: it fades out peacefully, the memory it copied goes
 * back into the chunk instead of being forgotten, and it leaves a recollite shard. An archive keeper is only calmed:
 * it stops fighting, but still keeps its chest. Each use spends one posy (not in creative).
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class RemembrancePosy {
    public enum Result { NONE, RESTED, CALMED }

    private static long rested;

    private RemembrancePosy() {}

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Faded faded)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.REMEMBRANCE_POSY.get())) {
            return;
        }
        if (offer(level, player, faded, stack) != Result.NONE) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** {@code player} gives {@code stack} (a posy) to {@code faded}. Public for QA. */
    public static Result offer(ServerLevel level, ServerPlayer player, Faded faded, ItemStack stack) {
        if (!faded.isAlive() || faded.isRemoved() || !stack.is(ModItems.REMEMBRANCE_POSY.get())) {
            return Result.NONE;
        }
        Result result;
        if (faded.keeper()) {
            faded.calm();
            result = Result.CALMED;
        } else {
            if (faded.copied() != null) {
                ImprintWriter.write(level, faded.blockPosition(), List.of(faded.copied()), null, false);
            }
            ItemEntity shard = new ItemEntity(level, faded.getX(), faded.getY() + 0.5D, faded.getZ(), new ItemStack(ModItems.RECOLLITE_SHARD.get()));
            level.addFreshEntity(shard);
            faded.discard();
            rested++;
            result = Result.RESTED;
        }
        level.sendParticles(ParticleTypes.SOUL, faded.getX(), faded.getY() + 1.0D, faded.getZ(), 12, 0.3D, 0.6D, 0.3D, 0.01D);
        level.sendParticles(ParticleTypes.END_ROD, faded.getX(), faded.getY() + 1.2D, faded.getZ(), 6, 0.2D, 0.4D, 0.2D, 0.01D);
        level.playSound(null, faded.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 0.7F);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return result;
    }

    /** Faded laid to rest since the server started (QA). */
    public static long restedCount() {
        return rested;
    }
}
