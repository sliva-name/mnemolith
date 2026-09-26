package com.mnemolith.armory;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.imprint.ImprintWriter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Slow iron maul. The swing is loud: it knocks and leaves instability in the chunk. */
public class GraveMaulItem extends Item {
    public GraveMaulItem(Properties properties) {
        super(properties);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Vec3 look = attacker.getLookAngle().normalize();
        target.push(look.x * 0.8D, 0.25D, look.z * 0.8D);
        target.hurtMarked = true;
        if (attacker.level() instanceof ServerLevel level) {
            ImprintWriter.spike(level, target.blockPosition(), 2);
            level.playSound(null, target.blockPosition(), ModSounds.PRESSURE_WARN.get(), SoundSource.PLAYERS, 0.45F, 0.7F);
        }
    }
}
