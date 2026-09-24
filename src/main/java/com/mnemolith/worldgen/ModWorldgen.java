package com.mnemolith.worldgen;

import com.mnemolith.worldgen.structure.ModProcessors;
import com.mnemolith.worldgen.structure.ModStructureTypes;

import net.neoforged.bus.api.IEventBus;

/** Feature, structure type, and processor registers. Templates and placement are datapacks. */
public final class ModWorldgen {
    private ModWorldgen() {}

    public static void register(IEventBus modEventBus) {
        ModFeatures.register(modEventBus);
        ModStructureTypes.register(modEventBus);
        ModProcessors.register(modEventBus);
    }
}
