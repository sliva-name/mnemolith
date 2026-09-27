package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The raised lens has a name for the place the player is studying, or {@code present} is false and the line goes away.
 * {@code source} is {@link com.mnemolith.recall.Origin}. The client builds one short line. No chat.
 */
public record OriginReadPayload(boolean present, int source, int kind, int age, boolean distorted, boolean personal) implements CustomPacketPayload {
    public static final Type<OriginReadPayload> TYPE = PayloadIds.type("origin_read");
    public static final StreamCodec<RegistryFriendlyByteBuf, OriginReadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OriginReadPayload::present,
            ByteBufCodecs.VAR_INT, OriginReadPayload::source,
            ByteBufCodecs.VAR_INT, OriginReadPayload::kind,
            ByteBufCodecs.VAR_INT, OriginReadPayload::age,
            ByteBufCodecs.BOOL, OriginReadPayload::distorted,
            ByteBufCodecs.BOOL, OriginReadPayload::personal,
            OriginReadPayload::new);

    public static OriginReadPayload clear() {
        return new OriginReadPayload(false, 0, 0, 0, false, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
