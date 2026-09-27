package com.mnemolith.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Footsteps at a trace, for the player who owns it, and only because they came back with the lens.
 * {@code ticks} is how long the client keeps them. {@code pos} is already shifted when the memory is distorted.
 */
public record TraceMarkPayload(BlockPos pos, int ticks, boolean distorted) implements CustomPacketPayload {
    public static final Type<TraceMarkPayload> TYPE = PayloadIds.type("trace_mark");
    public static final StreamCodec<RegistryFriendlyByteBuf, TraceMarkPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TraceMarkPayload::pos,
            ByteBufCodecs.VAR_INT, TraceMarkPayload::ticks,
            ByteBufCodecs.BOOL, TraceMarkPayload::distorted,
            TraceMarkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
