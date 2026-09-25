package com.mnemolith.client.gui;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModMenus;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client screens. Registered from {@code MnemolithClient}, so a dedicated server never loads this class. */
public final class ClientScreens {
    private ClientScreens() {}

    public static void init() {
        Mnemolith.LOGGER.debug("Mnemolith client screens ready ({})", Minecraft.class.getSimpleName());
    }

    public static void registerMenus(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COMPOSITION.get(), CompositionScreen::new);
        event.register(ModMenus.ECHO.get(), com.mnemolith.client.echo.EchoInventoryScreen::new);
    }
}
