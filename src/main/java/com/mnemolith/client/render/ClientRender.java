package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;

import net.minecraft.client.Minecraft;

/** Client render hooks. Referenced only from {@code MnemolithClient}. */
public final class ClientRender {
    private ClientRender() {}

    public static void init() {
        Mnemolith.LOGGER.debug("Mnemolith client render hooks ready ({})", Minecraft.class.getSimpleName());
    }
}
