package com.mnemolith.client.audio;

import com.mnemolith.Mnemolith;

import net.minecraft.client.Minecraft;

/** Client sound playback. Sound events themselves are registered from {@code com.mnemolith.audio}. */
public final class ClientAudio {
    private ClientAudio() {}

    public static void init() {
        Mnemolith.LOGGER.debug("Mnemolith client audio hooks ready ({})", Minecraft.class.getSimpleName());
    }
}
