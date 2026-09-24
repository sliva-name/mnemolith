package com.mnemolith.network;

import com.mnemolith.Mnemolith;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client asks for nearby chunk pressure while a chronicle lens is held. */
public record RequestPressurePayload() implements CustomPacketPayload {
    public static final Type<RequestPressurePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "request_pressure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPressurePayload> STREAM_CODEC = StreamCodec.unit(new RequestPressurePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
