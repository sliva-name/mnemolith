package com.mnemolith.armory;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;

/** A spear with the vanilla reach of a spear. The hit slows and cools a little instability, a hush in the chunk. */
public class HushSpearItem extends Item {
    public HushSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1));
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(target.blockPosition());
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory != null && memory.coolInstability(4) > 0) {
            MemoryPressure.recompute(chunk, memory);
        }
        level.playSound(null, target.blockPosition(), ModSounds.MUTE_PLACE.get(), SoundSource.PLAYERS, 0.5F, 1.5F);
    }
}
