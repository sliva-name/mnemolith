package com.mnemolith.network;

import java.util.List;
import java.util.function.IntFunction;

import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

/**
 * Nearby chunk pressure for one player. {@link Scope#FULL} is the lens reading (or an ambient read the
 * server allows). {@link Scope#BANDS} is the reduced read every player may receive for fracture feel.
 */
public record PressureSnapshotPayload(Scope scope, List<ChunkPressure> chunks) implements CustomPacketPayload {
    public static final Type<PressureSnapshotPayload> TYPE = PayloadIds.type("pressure_snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, PressureSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            Scope.STREAM_CODEC,
            PressureSnapshotPayload::scope,
            ByteBufCodecs.<RegistryFriendlyByteBuf, ChunkPressure>list(ImprintConstants.LENS_CHUNK_LIMIT).apply(ChunkPressure.STREAM_CODEC),
            PressureSnapshotPayload::chunks,
            PressureSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** What a snapshot may be used for on the client. */
    public enum Scope {
        /** Pressure, band, and chunk state for the lens radius. Drives the pill, shimmer, chime, and fracture feel. */
        FULL,
        /**
         * Only overloaded and fracture chunks within {@link PressureCollector#BAND_RADIUS}. Pressure is 0 and state is
         * {@code NORMAL}. Drives fracture feel only: no pill, no shimmer, no chime, no vein marks.
         */
        BANDS;

        private static final IntFunction<Scope> BY_ID = ByIdMap.continuous(Scope::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<io.netty.buffer.ByteBuf, Scope> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Scope::ordinal);
    }
}
