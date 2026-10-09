package com.mnemolith.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server tells nearby players to draw one memory flicker in Memory Hollows. {@code scene} is a
 * {@link com.mnemolith.worldgen.hollows.HollowFlickers} scene; {@code tag} the {@link com.mnemolith.imprint.ImprintTag}
 * ordinal it came from, or -1 for a chunk with no imprint. Nothing in the world changes.
 */
public record HollowFlickerPayload(BlockPos pos, float yaw, int scene, int tag) implements CustomPacketPayload {
    public static final Type<HollowFlickerPayload> TYPE = PayloadIds.type("hollow_flicker");
    public static final StreamCodec<RegistryFriendlyByteBuf, HollowFlickerPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            HollowFlickerPayload::pos,
            ByteBufCodecs.VAR_INT.map(i -> i / 10.0F, f -> Math.round(f * 10.0F)),
            HollowFlickerPayload::yaw,
            ByteBufCodecs.VAR_INT,
            HollowFlickerPayload::scene,
            ByteBufCodecs.VAR_INT,
            HollowFlickerPayload::tag,
            HollowFlickerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
