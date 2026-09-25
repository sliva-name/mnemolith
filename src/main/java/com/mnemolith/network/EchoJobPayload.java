package com.mnemolith.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks the server to change its own echo's job (from the echo screen, or when linking a chest / placing a blueprint).
 * The server checks ownership, distance and permissions before doing anything.
 */
public record EchoJobPayload(int entityId, Action action, BlockPos pos, int value) implements CustomPacketPayload {
    public enum Action {
        MODE_REPLAY,
        MODE_MINE,
        MODE_BUILD,
        STOP,
        RADIUS,
        LINK_CHEST,
        UNLINK_CHEST,
        PLACE_BLUEPRINT,
        CLEAR_BLUEPRINT;

        private static final Action[] VALUES = values();

        static Action byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : STOP;
        }
    }

    public static final Type<EchoJobPayload> TYPE = PayloadIds.type("echo_job");
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoJobPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            EchoJobPayload::entityId,
            ByteBufCodecs.VAR_INT.map(Action::byId, Action::ordinal),
            EchoJobPayload::action,
            BlockPos.STREAM_CODEC,
            EchoJobPayload::pos,
            ByteBufCodecs.VAR_INT,
            EchoJobPayload::value,
            EchoJobPayload::new);

    public static EchoJobPayload simple(int entityId, Action action) {
        return new EchoJobPayload(entityId, action, BlockPos.ZERO, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
