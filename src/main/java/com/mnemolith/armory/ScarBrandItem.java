package com.mnemolith.armory;

import java.util.List;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Spends a loud chunk. In overload or fracture it burns a short line and cools instability.
 * In a calm chunk it refuses.
 */
public class ScarBrandItem extends Item {
    private static final double REACH = 6.0D;

    public ScarBrandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!Armory.overloaded(player)) {
            serverPlayer.sendSystemMessage(Component.translatable("mnemolith.armory.brand_quiet"), true);
            return InteractionResult.FAIL;
        }
        Vec3 look = player.getLookAngle().normalize();
        AABB box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0D);
        List<LivingEntity> hit = server.getEntitiesOfClass(LivingEntity.class, box, living -> living != player && living.isAlive());
        int struck = 0;
        for (LivingEntity living : hit) {
            Vec3 toward = living.getEyePosition().subtract(player.getEyePosition());
            if (toward.lengthSqr() > REACH * REACH) {
                continue;
            }
            if (toward.normalize().dot(look) < 0.55D) {
                continue;
            }
            living.hurt(server.damageSources().playerAttack(serverPlayer), 7.0F);
            living.setRemainingFireTicks(40);
            struck++;
        }
        LevelChunk chunk = server.getChunkAt(player.blockPosition());
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory != null && memory.coolInstability(6) > 0) {
            MemoryPressure.recompute(chunk, memory);
        }
        stack.hurtAndBreak(1, server, serverPlayer, item -> {});
        player.getCooldowns().addCooldown(stack, 40);
        server.playSound(null, player.blockPosition(), ModSounds.COMPOSE_FAIL.get(), SoundSource.PLAYERS, 0.7F, 0.6F);
        serverPlayer.sendSystemMessage(Component.translatable("mnemolith.armory.brand_spent", struck), true);
        return InteractionResult.SUCCESS_SERVER;
    }
}
