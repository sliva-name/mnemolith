package com.mnemolith.data;

import com.mnemolith.Mnemolith;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. Imprint slips carry {@link ImprintCast}. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Mnemolith.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ImprintCast>> IMPRINT_CAST = DATA_COMPONENTS.registerComponentType(
            "imprint_cast",
            builder -> builder.persistent(ImprintCast.CODEC).networkSynchronized(ImprintCast.STREAM_CODEC));

    private ModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
