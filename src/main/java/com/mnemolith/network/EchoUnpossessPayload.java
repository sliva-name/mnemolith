package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client pressed the return key. */
public record EchoUnpossessPayload() implements CustomPacketPayload {
    public static final EchoUnpossessPayload INSTANCE = new EchoUnpossessPayload();
    public static final Type<EchoUnpossessPayload> TYPE = PayloadIds.type("echo_unpossess");
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoUnpossessPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
