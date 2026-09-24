package com.mnemolith;

import com.mnemolith.client.audio.ClientAudio;
import com.mnemolith.client.gui.ClientScreens;
import com.mnemolith.client.particle.ClientParticles;
import com.mnemolith.client.render.ClientRender;
import com.mnemolith.config.ClientConfig;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Physical-client entrypoint. NeoForge does not load this class on a dedicated server.
 */
@Mod(value = Mnemolith.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Mnemolith.MOD_ID, value = Dist.CLIENT)
public final class MnemolithClient {
    public MnemolithClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
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
