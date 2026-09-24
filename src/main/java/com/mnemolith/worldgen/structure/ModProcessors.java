package com.mnemolith.worldgen.structure;

import com.mojang.serialization.MapCodec;

import com.mnemolith.Mnemolith;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModProcessors {
    public static final DeferredRegister<MapCodec<? extends StructureProcessor>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Mnemolith.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends StructureProcessor>, MapCodec<ObservatoryProcessor>> OBSERVATORY = PROCESSORS.register(
            "observatory",
            () -> ObservatoryProcessor.CODEC);

    private ModProcessors() {}

    public static void register(IEventBus modEventBus) {
        PROCESSORS.register(modEventBus);
    }
}
