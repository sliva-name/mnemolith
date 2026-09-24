package com.mnemolith;

import com.mnemolith.client.audio.ClientAudio;
import com.mnemolith.client.gui.ClientScreens;
import com.mnemolith.client.network.ClientPayloads;
import com.mnemolith.client.particle.ClientParticles;
import com.mnemolith.client.render.ClientRender;
import com.mnemolith.client.render.ModEntityRenderers;
import com.mnemolith.client.render.PressureClient;
import com.mnemolith.config.ClientConfig;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Physical-client entrypoint. NeoForge does not load this class on a dedicated server.
 */
@Mod(value = Mnemolith.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Mnemolith.MOD_ID, value = Dist.CLIENT)
public final class MnemolithClient {
    public MnemolithClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(ClientScreens::registerMenus);
        modEventBus.addListener(ClientPayloads::register);
        modEventBus.addListener(ModEntityRenderers::registerLayers);
        modEventBus.addListener(ModEntityRenderers::registerRenderers);
        NeoForge.EVENT_BUS.addListener(PressureClient::onClientTick);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientRender.init();
            ClientParticles.init();
            ClientAudio.init();
            ClientScreens.init();
        });
        Mnemolith.LOGGER.info("Mnemolith client setup; imprintParticles={}", ClientConfig.IMPRINT_PARTICLES.get());
    }
}
