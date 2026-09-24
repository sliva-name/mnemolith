package com.mnemolith.worldgen.structure;

import com.mnemolith.Mnemolith;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructureTypes {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Mnemolith.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<ObservatoryStructure>> OBSERVATORY = STRUCTURE_TYPES.register(
            "chronicle_observatory",
            () -> () -> ObservatoryStructure.CODEC);

    private ModStructureTypes() {}

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
    }
}
