package com.mnemolith.armory;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Iron-weight blade. A hit in a chunk that still holds an imprint strikes again, then the blade rests. */
public class RecallBladeItem extends Item {
    public RecallBladeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        if (attacker instanceof Player player && player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(target.blockPosition()));
        if (memory == null || memory.imprintCount() == 0) {
            return;
        }
        if (attacker instanceof Player player) {
            player.getCooldowns().addCooldown(stack, 30);
        }
        target.hurt(attacker.damageSources().mobAttack(attacker), 2.0F);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        level.playSound(null, target.blockPosition(), ModSounds.EXTRACT.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
    }
}
