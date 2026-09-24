package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.ServerConfig;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Common game events. Fires on the integrated server and the dedicated server.
 * Imprint writers will subscribe here later, one event at a time, for the chunk that changed.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class ModGameEvents {
    private ModGameEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Mnemolith.LOGGER.info(
                "Mnemolith logical server starting; allowRecollectionStorms={} maxStormsPerDimension={}",
                ServerConfig.ALLOW_RECOLLECTION_STORMS.get(),
                ServerConfig.MAX_STORMS_PER_DIMENSION.get());
    }
}
