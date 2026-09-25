package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client asks to possess the echo it targets through the raised lens. The server re-checks everything. */
public record EchoPossessPayload(int entityId) implements CustomPacketPayload {
    public static final Type<EchoPossessPayload> TYPE = PayloadIds.type("echo_possess");
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoPossessPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            EchoPossessPayload::entityId,
            EchoPossessPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
