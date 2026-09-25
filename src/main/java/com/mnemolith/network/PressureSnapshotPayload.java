package com.mnemolith.network;

import java.util.List;

import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PressureSnapshotPayload(List<ChunkPressure> chunks) implements CustomPacketPayload {
    public static final Type<PressureSnapshotPayload> TYPE = PayloadIds.type("pressure_snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, PressureSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<RegistryFriendlyByteBuf, ChunkPressure>list(ImprintConstants.LENS_CHUNK_LIMIT).apply(ChunkPressure.STREAM_CODEC),
            PressureSnapshotPayload::chunks,
            PressureSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
