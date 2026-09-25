package com.mnemolith;

import com.mnemolith.client.audio.ClientAudio;
import com.mnemolith.client.gui.ClientScreens;
import com.mnemolith.client.gui.FieldGuideClient;
import com.mnemolith.client.gui.GuiArt;
import com.mnemolith.client.gui.LensOverlay;
import com.mnemolith.client.network.ClientPayloads;
import com.mnemolith.client.particle.ClientParticles;
import com.mnemolith.client.render.ClientRender;
import com.mnemolith.client.render.ModEntityRenderers;
import com.mnemolith.client.render.PressureClient;
import com.mnemolith.client.config.ClientConfig;

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
        com.mnemolith.entity.echo.EchoEntity.clientFactory = com.mnemolith.client.echo.ClientEcho::new;
        com.mnemolith.entity.echo.EchoShell.clientFactory = com.mnemolith.client.echo.ClientEchoShell::new;
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(ClientScreens::registerMenus);
        modEventBus.addListener(LensOverlay::register);
        modEventBus.addListener(ClientPayloads::register);
        modEventBus.addListener(ClientParticles::register);
        modEventBus.addListener(ModEntityRenderers::registerLayers);
        modEventBus.addListener(ModEntityRenderers::registerRenderers);
        NeoForge.EVENT_BUS.addListener(PressureClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(LensOverlay::onSystemMessage);
        NeoForge.EVENT_BUS.addListener(FieldGuideClient::onRightClick);
        modEventBus.addListener(com.mnemolith.client.echo.ThermalClient::registerKeys);
        modEventBus.addListener(com.mnemolith.client.echo.EchoHud::register);
        NeoForge.EVENT_BUS.addListener(com.mnemolith.client.echo.ThermalClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.mnemolith.client.echo.ThermalClient::onAfterWeather);
        NeoForge.EVENT_BUS.addListener(com.mnemolith.client.echo.ThermalClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.mnemolith.client.echo.EchoJobClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.mnemolith.client.echo.EchoJobClient::onInteraction);
        NeoForge.EVENT_BUS.addListener(com.mnemolith.client.echo.EchoJobClient::onSubmitGeometry);
        NeoForge.EVENT_BUS.addListener(com.mnemolith.client.echo.EchoJobClient::onLoggingOut);
        modEventBus.addListener(com.mnemolith.client.echo.EchoJobClient::registerHud);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, false, net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre.class, com.mnemolith.client.echo.EchoRenderer::onRenderPlayerPre);
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
        Mnemolith.LOGGER.info(
                "Mnemolith gui contrast glyph={} shadow={} panel={} accent={} fail={}",
                Integer.toHexString(GuiArt.BONE),
                Integer.toHexString(GuiArt.SHADOW),
                Integer.toHexString(GuiArt.INK),
                Integer.toHexString(GuiArt.VERDIGRIS),
                Integer.toHexString(GuiArt.FAIL));
    }
}
