package com.mnemolith.network;

import java.util.List;

import com.mnemolith.recall.Gesture;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server tells one player to draw a short ghost at an old gesture. Nobody else receives it.
 * {@code kind} is {@link com.mnemolith.recall.GestureKind} ordinal. {@code distorted} already
 * moved {@code pos}, {@code yaw} and {@code trail} on the server.
 */
public record RecallGhostPayload(BlockPos pos, float yaw, float pitch, int kind, boolean distorted, List<BlockPos> trail) implements CustomPacketPayload {
    public static final Type<RecallGhostPayload> TYPE = PayloadIds.type("recall_ghost");
    public static final StreamCodec<RegistryFriendlyByteBuf, RecallGhostPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            RecallGhostPayload::pos,
            ByteBufCodecs.VAR_INT.map(i -> i / 100.0F, f -> Math.round(f * 100.0F)),
            RecallGhostPayload::yaw,
            ByteBufCodecs.VAR_INT.map(i -> i / 100.0F, f -> Math.round(f * 100.0F)),
            RecallGhostPayload::pitch,
            ByteBufCodecs.VAR_INT,
            RecallGhostPayload::kind,
            ByteBufCodecs.BOOL,
            RecallGhostPayload::distorted,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(Gesture.TRAIL_CAP)),
            RecallGhostPayload::trail,
            RecallGhostPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
