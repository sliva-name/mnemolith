package com.mnemolith.data;

import com.mnemolith.Mnemolith;

import net.minecraft.core.HolderLookup;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Wires the existing {@code data} run config (Z3). Providers are additive: hand-written JSON under
 * {@code src/main/resources/data} stays authoritative; generated output lands in {@code src/generated/resources}.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class MnemolithDataGen {
    private MnemolithDataGen() {}

    @SubscribeEvent
    public static void gather(GatherDataEvent.Client event) {
        event.createProvider((output, lookup) -> new BlockTagsProvider(output, lookup, Mnemolith.MOD_ID) {
            @Override
            protected void addTags(HolderLookup.Provider provider) {
                // Hand-written tags remain under src/main/resources. This provider exists so the
                // datagen pipeline is live for future recipe/tag/advancement migrations.
            }
        });
        Mnemolith.LOGGER.info("Mnemolith datagen providers registered (Z3 hook)");
    }
}
