package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One loaded chunk in a lens snapshot. */
public record ChunkPressure(int chunkX, int chunkZ, int pressure, int band) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkPressure> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChunkPressure::chunkX,
            ByteBufCodecs.VAR_INT, ChunkPressure::chunkZ,
            ByteBufCodecs.VAR_INT, ChunkPressure::pressure,
            ByteBufCodecs.VAR_INT, ChunkPressure::band,
            ChunkPressure::new);
}
