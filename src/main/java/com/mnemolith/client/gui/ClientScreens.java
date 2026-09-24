package com.mnemolith.client.gui;

import com.mnemolith.Mnemolith;

import net.minecraft.client.Minecraft;

/** Client screens. The NeoForge config screen is registered from {@code MnemolithClient}. */
public final class ClientScreens {
    private ClientScreens() {}

    public static void init() {
        Mnemolith.LOGGER.debug("Mnemolith client screens ready ({})", Minecraft.class.getSimpleName());
    }
}
