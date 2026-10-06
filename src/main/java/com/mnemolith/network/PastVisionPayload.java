package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The world is replaying one of this player's past moments: soften the screen edge for {@code ticks}.
 * Sent only to the owner of the scene. {@code kind} is the {@link com.mnemolith.recall.LifeMomentKind} ordinal.
 */
public record PastVisionPayload(int ticks, int kind) implements CustomPacketPayload {
    public static final Type<PastVisionPayload> TYPE = PayloadIds.type("past_vision");
    public static final StreamCodec<RegistryFriendlyByteBuf, PastVisionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PastVisionPayload::ticks,
            ByteBufCodecs.VAR_INT, PastVisionPayload::kind,
            PastVisionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
