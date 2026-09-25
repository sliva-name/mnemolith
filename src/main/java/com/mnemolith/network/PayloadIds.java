package com.mnemolith.network;

import com.mnemolith.Mnemolith;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Packet type ids. The path is the registered payload id and must not change. */
public final class PayloadIds {
    private PayloadIds() {}

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path));
    }
}
