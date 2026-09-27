package com.mnemolith.client.recall;

import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.OriginReadPayload;
import com.mnemolith.recall.AnchorKind;
import com.mnemolith.recall.GestureKind;
import com.mnemolith.recall.Origin;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The one line the raised lens may show. The server already decided the place was this player's to read.
 */
public final class OriginView {
    private static boolean present;
    private static Component line = Component.empty();
    private static int revision;

    private OriginView() {}

    public static void accept(OriginReadPayload payload) {
        revision++;
        if (!payload.present()) {
            present = false;
            line = Component.empty();
            return;
        }
        present = true;
        Component what = what(payload.source(), payload.kind());
        Component who = Component.translatable(payload.personal() ? "mnemolith.lens.someone" : "mnemolith.lens.place");
        int age = payload.age();
        if (age < 0 || age > 3) {
            age = 0;
        }
        Component when = Component.translatable("mnemolith.lens.age." + age);
        if (payload.distorted()) {
            line = Component.translatable("mnemolith.lens.origin_warped", what, who, when, Component.translatable("mnemolith.lens.warped"));
        } else {
            line = Component.translatable("mnemolith.lens.origin", what, who, when);
        }
    }

    public static Component line() {
        return present ? line : null;
    }

    public static int revision() {
        return revision;
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        present = false;
        line = Component.empty();
        revision++;
    }

    private static Component what(int source, int kind) {
        if (source == Origin.GESTURE && kind >= 0 && kind < GestureKind.values().length) {
            return Component.translatable("mnemolith.memory.gesture." + GestureKind.values()[kind].getSerializedName());
        }
        if (source == Origin.ANCHOR && kind >= 0 && kind < AnchorKind.values().length) {
            return Component.translatable("mnemolith.memory.anchor." + AnchorKind.values()[kind].getSerializedName());
        }
        if (source == Origin.IMPRINT) {
            return Component.translatable(ImprintTag.byOrdinal(kind).translationKey());
        }
        return Component.empty();
    }
}
