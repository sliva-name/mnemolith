package com.mnemolith.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Play-phase payloads. The client handler for the snapshot is registered from {@code MnemolithClient}.
 */
public final class ModNetwork {
    private ModNetwork() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(RequestPressurePayload.TYPE, RequestPressurePayload.STREAM_CODEC, PressureSync::handleRequest)
                .playToClient(PressureSnapshotPayload.TYPE, PressureSnapshotPayload.STREAM_CODEC)
                .playToClient(OpenCatalogPayload.TYPE, OpenCatalogPayload.STREAM_CODEC);
    }
}
