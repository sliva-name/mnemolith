package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One loaded chunk in a lens snapshot. {@code state} is a {@code ChunkState} ordinal. */
public record ChunkPressure(int chunkX, int chunkZ, int pressure, int band, int state) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkPressure> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChunkPressure::chunkX,
            ByteBufCodecs.VAR_INT, ChunkPressure::chunkZ,
            ByteBufCodecs.VAR_INT, ChunkPressure::pressure,
            ByteBufCodecs.VAR_INT, ChunkPressure::band,
            ByteBufCodecs.VAR_INT, ChunkPressure::state,
            ChunkPressure::new);
}
