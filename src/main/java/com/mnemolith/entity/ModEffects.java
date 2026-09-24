package com.mnemolith.entity;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.effect.FireTrailEffect;
import com.mnemolith.entity.effect.LandingBurstEffect;
import com.mnemolith.entity.effect.UnrecordedEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Mnemolith.MOD_ID);

    public static final DeferredHolder<MobEffect, UnrecordedEffect> UNRECORDED = EFFECTS.register("unrecorded", UnrecordedEffect::new);
    public static final DeferredHolder<MobEffect, FireTrailEffect> FIRE_TRAIL = EFFECTS.register("fire_trail", FireTrailEffect::new);
    public static final DeferredHolder<MobEffect, LandingBurstEffect> LANDING_BURST = EFFECTS.register("landing_burst", LandingBurstEffect::new);

    private ModEffects() {}

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
