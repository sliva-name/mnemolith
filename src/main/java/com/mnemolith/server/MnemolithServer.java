package com.mnemolith.server;

import com.mnemolith.Mnemolith;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;

/**
 * Dedicated-server entrypoint. This class is not loaded on the physical client.
 */
@Mod(value = Mnemolith.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class MnemolithServer {
    public MnemolithServer(IEventBus modEventBus) {
        modEventBus.addListener(MnemolithServer::onDedicatedServerSetup);
    }

    private static void onDedicatedServerSetup(FMLDedicatedServerSetupEvent event) {
        Mnemolith.LOGGER.info("Mnemolith dedicated server setup");
    }
}
