package com.mnemolith.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Play-phase payloads. The client handler for the snapshot is registered from {@code MnemolithClient}.
 * Version 8: memory flickers in Memory Hollows.
 * Version 7: the past-self vision (the world remembers you).
 * Version 6: server tuning sync (possess range, mining radius, drum formulas) for dedicated servers.
 * Version 5: the lens origin line and trace footsteps. Version 4 added the owner-only recall ghost.
 * A stage-3 guide reuses that ghost; it does not add a packet.
 */
public final class ModNetwork {
    private ModNetwork() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::onRegister);
        modEventBus.addListener(ServerTuning::onConfigReload);
        NeoForge.EVENT_BUS.addListener(ServerTuning::onDatapackSync);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar("8")
                .playToServer(RequestPressurePayload.TYPE, RequestPressurePayload.STREAM_CODEC, PressureSync::handleRequest)
                .playToClient(PressureSnapshotPayload.TYPE, PressureSnapshotPayload.STREAM_CODEC)
                .playToClient(OpenCatalogPayload.TYPE, OpenCatalogPayload.STREAM_CODEC)
                .playToServer(EchoPossessPayload.TYPE, EchoPossessPayload.STREAM_CODEC, EchoNetwork::handlePossess)
                .playToServer(EchoUnpossessPayload.TYPE, EchoUnpossessPayload.STREAM_CODEC, EchoNetwork::handleUnpossess)
                .playToClient(EchoStatePayload.TYPE, EchoStatePayload.STREAM_CODEC)
                .playToServer(EchoJobPayload.TYPE, EchoJobPayload.STREAM_CODEC, EchoNetwork::handleJob)
                .playToClient(EchoGhostPayload.TYPE, EchoGhostPayload.STREAM_CODEC)
                .playToClient(RecallGhostPayload.TYPE, RecallGhostPayload.STREAM_CODEC)
                .playToClient(OriginReadPayload.TYPE, OriginReadPayload.STREAM_CODEC)
                .playToClient(TraceMarkPayload.TYPE, TraceMarkPayload.STREAM_CODEC)
                .playToServer(EchoCommandPayload.TYPE, EchoCommandPayload.STREAM_CODEC, EchoNetwork::handleCommand)
                .playToClient(ServerTuningPayload.TYPE, ServerTuningPayload.STREAM_CODEC)
                .playToClient(PastVisionPayload.TYPE, PastVisionPayload.STREAM_CODEC)
                .playToClient(HollowFlickerPayload.TYPE, HollowFlickerPayload.STREAM_CODEC);
    }
}
