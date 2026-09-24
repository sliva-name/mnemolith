package com.mnemolith.worldgen;

import com.mnemolith.Mnemolith;
import com.mnemolith.worldgen.feature.ArchivalVeinFeature;
import com.mnemolith.worldgen.feature.MutePocketFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Mnemolith.MOD_ID);

    public static final DeferredHolder<Feature<?>, ArchivalVeinFeature> ARCHIVAL_VEIN = FEATURES.register("archival_vein", ArchivalVeinFeature::new);
    public static final DeferredHolder<Feature<?>, MutePocketFeature> MUTE_POCKET = FEATURES.register("mute_pocket", MutePocketFeature::new);

    private ModFeatures() {}

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
