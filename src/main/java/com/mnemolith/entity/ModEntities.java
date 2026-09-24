package com.mnemolith.entity;

import com.mnemolith.Mnemolith;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity type registry. Empty until the three mobs and the Scar boss event exist. */
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(Mnemolith.MOD_ID);

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
