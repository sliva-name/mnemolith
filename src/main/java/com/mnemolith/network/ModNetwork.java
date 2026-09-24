package com.mnemolith.network;

import net.neoforged.bus.api.IEventBus;

/**
 * Payload registration point. Phase 2 registers no packets.
 * <p>
 * Later payloads should carry chunk imprint deltas and nearby pressure, registered on the mod event bus.
 */
public final class ModNetwork {
    private ModNetwork() {}

    public static void register(IEventBus modEventBus) {
        // No payloads in Phase 2. The bus argument is the attachment point for later registrars.
        if (modEventBus == null) {
            throw new IllegalArgumentException("modEventBus");
        }
    }
}
