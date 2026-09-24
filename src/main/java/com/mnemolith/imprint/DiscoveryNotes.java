package com.mnemolith.imprint;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Writes discovery bits and pushes them to that player only. */
public final class DiscoveryNotes {
    private DiscoveryNotes() {}

    public static void noteTag(ServerPlayer player, ImprintTag tag) {
        Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
        if (discovery.noteTag(tag)) {
            player.syncData(ModAttachments.DISCOVERY.get());
        }
    }

    public static void noteFormula(ServerPlayer player, int ordinal) {
        Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
        if (discovery.noteFormula(ordinal)) {
            player.syncData(ModAttachments.DISCOVERY.get());
        }
    }

    public static void noteMute(ServerPlayer player) {
        Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
        if (discovery.noteMute()) {
            player.syncData(ModAttachments.DISCOVERY.get());
            player.sendOverlayMessage(Component.translatable("mnemolith.message.mute_first"));
        }
    }

    public static void noteObservatory(ServerPlayer player) {
        Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
        if (discovery.noteObservatory()) {
            player.syncData(ModAttachments.DISCOVERY.get());
            player.sendOverlayMessage(Component.translatable("mnemolith.message.observatory_first"));
        }
    }
}
