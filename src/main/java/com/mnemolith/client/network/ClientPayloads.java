package com.mnemolith.client.network;

import com.mnemolith.client.render.PressureClient;
import com.mnemolith.network.PressureSnapshotPayload;

import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client half of the pressure snapshot. Registered from {@code MnemolithClient}. */
public final class ClientPayloads {
    private ClientPayloads() {}

    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(PressureSnapshotPayload.TYPE, (payload, context) -> context.enqueueWork(() -> PressureClient.accept(payload)));
    }
}
