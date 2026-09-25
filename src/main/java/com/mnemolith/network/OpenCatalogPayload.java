package com.mnemolith.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Asks the owning client to open the catalog. The screen class stays on the client. */
public record OpenCatalogPayload(int tags, int formulas) implements CustomPacketPayload {
    public static final Type<OpenCatalogPayload> TYPE = PayloadIds.type("open_catalog");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCatalogPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenCatalogPayload::tags,
            ByteBufCodecs.VAR_INT, OpenCatalogPayload::formulas,
            OpenCatalogPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
