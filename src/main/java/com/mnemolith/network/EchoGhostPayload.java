package com.mnemolith.network;

import java.util.Optional;

import com.mnemolith.echo.EchoLesson;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Rotation;

/**
 * Server tells the owner where their echo's blueprint stands so the client can keep drawing the pink ghost while it builds.
 * An empty blueprint clears the ghost of that echo.
 */
public record EchoGhostPayload(int entityId, BlockPos anchor, Rotation rotation, Optional<EchoLesson.Blueprint> blueprint) implements CustomPacketPayload {
    public static final Type<EchoGhostPayload> TYPE = PayloadIds.type("echo_ghost");
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoGhostPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            EchoGhostPayload::entityId,
            BlockPos.STREAM_CODEC,
            EchoGhostPayload::anchor,
            ByteBufCodecs.VAR_INT.map(i -> Rotation.values()[Math.floorMod(i, 4)], Rotation::ordinal),
            EchoGhostPayload::rotation,
            ByteBufCodecs.optional(EchoLesson.Blueprint.STREAM_CODEC),
            EchoGhostPayload::blueprint,
            EchoGhostPayload::new);

    public static EchoGhostPayload clear(int entityId) {
        return new EchoGhostPayload(entityId, BlockPos.ZERO, Rotation.NONE, Optional.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
