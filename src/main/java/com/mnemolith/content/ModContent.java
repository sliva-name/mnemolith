package com.mnemolith.content;

import net.neoforged.bus.api.IEventBus;

/** Attaches content registries to the mod event bus. */
public final class ModContent {
    private ModContent() {}

    public static void register(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
    }
}
