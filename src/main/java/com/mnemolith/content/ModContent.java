package com.mnemolith.content;

import net.neoforged.bus.api.IEventBus;

/**
 * Attaches the content registries to the mod event bus. Phase 2 registers no blocks, items, or tabs.
 */
public final class ModContent {
    private ModContent() {}

    public static void register(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
    }
}
