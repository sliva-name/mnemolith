package com.mnemolith.entity.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker consumed by the next significant landing. */
public class LandingBurstEffect extends MobEffect {
    public LandingBurstEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x3D4A8A);
    }
}
