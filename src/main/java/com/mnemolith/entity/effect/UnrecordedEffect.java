package com.mnemolith.entity.effect;

import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker. {@code ImprintEvents} clears mob targets that pick a player carrying this effect. */
public class UnrecordedEffect extends MobEffect {
    public UnrecordedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x3E8E7E);
    }

    public static int duration() {
        return ImprintConstants.UNRECORDED_DURATION_TICKS;
    }
}
