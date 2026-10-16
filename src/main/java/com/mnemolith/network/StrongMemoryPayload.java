package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server tells one player that the chunk they just stepped into remembers strongly (saturated or louder). The client
 * shows the strong-memory hint there if {@code strongMemoryHint} is on. Nothing in the world changes.
 */
public record StrongMemoryPayload(int chunkX, int chunkZ) implements CustomPacketPayload {
    public static final Type<StrongMemoryPayload> TYPE = PayloadIds.type("strong_memory");
    public static final StreamCodec<RegistryFriendlyByteBuf, StrongMemoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StrongMemoryPayload::chunkX,
            ByteBufCodecs.VAR_INT, StrongMemoryPayload::chunkZ,
            StrongMemoryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
