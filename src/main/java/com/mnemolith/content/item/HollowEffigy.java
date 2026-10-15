package com.mnemolith.content.item;

import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.boss.Unremembered;
import com.mnemolith.worldgen.hollows.HollowLanterns;
import com.mnemolith.worldgen.hollows.Hollows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * The hollow effigy: used on a block in a Memory Hollows chunk that holds at least {@link #CALL_FORGOTTEN} forgotten
 * imprints, with no recollite lantern guarding it and no other Unremembered near, it gathers that forgetting into the
 * Unremembered. See {@code docs/design/unremembered.md}.
 */
public class HollowEffigy extends Item {
    public static final int CALL_FORGOTTEN = 3;
    public static final double OTHER_RANGE = 64.0D;

    public enum Result { CALLED, NOT_HOLLOW, TOO_FEW, KEPT, ANOTHER, FAILED }

    public HollowEffigy(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        Result result = call(level, player, context.getClickedPos().above(), context.getItemInHand());
        if (result != Result.CALLED) {
            player.sendOverlayMessage(Component.translatable("mnemolith.effigy." + result.name().toLowerCase(java.util.Locale.ROOT)));
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Tries to call the Unremembered standing at {@code at}. Spends {@code stack} on success (not in creative). Public for QA. */
    public static Result call(ServerLevel level, ServerPlayer player, BlockPos at, ItemStack stack) {
        if (!Hollows.is(level.getBiome(at))) {
            return Result.NOT_HOLLOW;
        }
        LevelChunk chunk = level.getChunkAt(at);
        if (Unremembered.countForgotten(chunk) < CALL_FORGOTTEN) {
            return Result.TOO_FEW;
        }
        if (HollowLanterns.guarded(level, at)) {
            return Result.KEPT;
        }
        if (!level.getEntitiesOfClass(Unremembered.class, new AABB(at).inflate(OTHER_RANGE)).isEmpty()) {
            return Result.ANOTHER;
        }
        Unremembered boss = ModEntities.UNREMEMBERED.get().create(level, EntitySpawnReason.TRIGGERED);
        if (boss == null) {
            return Result.FAILED;
        }
        Unremembered.clearForgotten(chunk);
        boss.snapTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
        boss.setTarget(player);
        level.addFreshEntity(boss);
        level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D, 40, 0.8D, 1.4D, 0.8D, 0.02D);
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.getX() + 0.5D, at.getY() + 0.2D, at.getZ() + 0.5D, 20, 1.2D, 0.1D, 1.2D, 0.01D);
        level.playSound(null, at, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.8F, 0.6F);
        boss.updateBar(level);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return Result.CALLED;
    }
}
