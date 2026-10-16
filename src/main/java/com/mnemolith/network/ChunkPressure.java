package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One loaded chunk in a lens snapshot. {@code state} is a {@code ChunkState} ordinal; {@code loudest} the
 * {@code ImprintTag} ordinal of the chunk's loudest imprint, or -1 for none or a band-only sample.
 */
public record ChunkPressure(int chunkX, int chunkZ, int pressure, int band, int state, int loudest) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkPressure> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChunkPressure::chunkX,
            ByteBufCodecs.VAR_INT, ChunkPressure::chunkZ,
            ByteBufCodecs.VAR_INT, ChunkPressure::pressure,
            ByteBufCodecs.VAR_INT, ChunkPressure::band,
            ByteBufCodecs.VAR_INT, ChunkPressure::state,
            ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), ChunkPressure::loudest,
            ChunkPressure::new);

    public ChunkPressure(int chunkX, int chunkZ, int pressure, int band, int state) {
        this(chunkX, chunkZ, pressure, band, state, -1);
    }
}
