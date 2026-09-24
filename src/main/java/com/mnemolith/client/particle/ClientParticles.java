package com.mnemolith.client.particle;

import com.mnemolith.Mnemolith;

import net.minecraft.client.Minecraft;

/** Client particle hooks for imprint motes. No particles are registered in Phase 2. */
public final class ClientParticles {
    private ClientParticles() {}

    public static void init() {
        Mnemolith.LOGGER.debug("Mnemolith client particle hooks ready ({})", Minecraft.class.getSimpleName());
    }
}
