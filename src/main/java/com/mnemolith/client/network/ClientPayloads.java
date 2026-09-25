package com.mnemolith.client.network;

import com.mnemolith.client.gui.CatalogScreen;
import com.mnemolith.client.render.PressureClient;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.network.OpenCatalogPayload;
import com.mnemolith.network.PressureSnapshotPayload;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client payload handlers. Registered from {@code MnemolithClient}. */
public final class ClientPayloads {
    private ClientPayloads() {}

    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(PressureSnapshotPayload.TYPE, (payload, context) -> context.enqueueWork(() -> PressureClient.accept(payload)));
        event.register(com.mnemolith.network.EchoStatePayload.TYPE, (payload, context) -> context.enqueueWork(() -> com.mnemolith.echo.EchoView.setPossessed(payload.possessed())));
        event.register(com.mnemolith.network.EchoGhostPayload.TYPE, (payload, context) -> context.enqueueWork(() -> com.mnemolith.client.echo.EchoJobClient.acceptGhost(payload)));
        event.register(OpenCatalogPayload.TYPE, (payload, context) -> context.enqueueWork(() -> {
            if (!CommonConfig.CATALOG_ENABLED.get()) {
                return;
            }
            Minecraft.getInstance().setScreenAndShow(new CatalogScreen(payload.tags(), payload.formulas()));
        }));
    }
}
