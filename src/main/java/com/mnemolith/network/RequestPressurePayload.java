package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks for nearby chunk pressure. {@code ambient} is what the client hopes to do
 * without a lens: saturated shimmer, or fracture feel. The server does not treat that bit as
 * permission: a full snapshot is sent when the player holds a chronicle lens, or when
 * {@code gameplay.allowAmbientPressure} is on. Otherwise the answer is the band-only snapshot
 * ({@link PressureSnapshotPayload.Scope#BANDS}) that fracture feel needs. Vein marks still require the lens.
 * The server only answers. It does not write memory.
 */
public record RequestPressurePayload(boolean ambient) implements CustomPacketPayload {
    public static final Type<RequestPressurePayload> TYPE = PayloadIds.type("request_pressure");
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPressurePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            RequestPressurePayload::ambient,
            RequestPressurePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
