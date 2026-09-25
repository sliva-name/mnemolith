package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server tells one player whether they are currently inside an echo (drives the HUD hint and the return key). */
public record EchoStatePayload(boolean possessed) implements CustomPacketPayload {
    public static final Type<EchoStatePayload> TYPE = PayloadIds.type("echo_state");
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            EchoStatePayload::possessed,
            EchoStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
