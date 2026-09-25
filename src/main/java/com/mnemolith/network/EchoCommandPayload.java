package com.mnemolith.network;

import com.mnemolith.echo.job.EchoJob;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Stage 3: a lens order for the echo the player targets through the raised lens (stay, follow me, return to its work
 * point). The server re-checks the lens, the owner, the range and the aim, like a possession request.
 */
public record EchoCommandPayload(int entityId, EchoJob.Order order) implements CustomPacketPayload {
    public static final Type<EchoCommandPayload> TYPE = PayloadIds.type("echo_command");
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            EchoCommandPayload::entityId,
            ByteBufCodecs.VAR_INT.map(EchoJob.Order::byId, EchoJob.Order::ordinal),
            EchoCommandPayload::order,
            EchoCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
