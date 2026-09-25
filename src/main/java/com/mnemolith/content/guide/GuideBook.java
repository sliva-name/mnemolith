package com.mnemolith.content.guide;

import com.mnemolith.Mnemolith;

import net.minecraft.resources.Identifier;

/**
 * Static pages of the field guide. Text lives in the language files.
 * The screen that draws these pages is client-only.
 * Dedicated QA expects {@link #pageCount()} to stay 19 (stage 3 added the echo page).
 */
public final class GuideBook {
    public static final String ITEM_ID = "field_guide";
    public static final int ART_WIDTH = 256;
    public static final int ART_HEIGHT = 128;

    private static final String[] PAGES = {
            "welcome",
            "hour",
            "loop",
            "sources",
            "bands",
            "lens",
            "needle",
            "reel",
            "formulas",
            "fails",
            "mute",
            "catalog",
            "strider",
            "archivist",
            "replicant",
            "echoes",
            "world",
            "players",
            "reference"
    };

    private GuideBook() {}

    public static int pageCount() {
        return PAGES.length;
    }

    public static String pageId(int index) {
        return PAGES[index];
    }

    public static String titleKey(int index) {
        return "mnemolith.guide." + PAGES[index] + ".title";
    }

    public static String bodyKey(int index) {
        return "mnemolith.guide." + PAGES[index] + ".body";
    }

    public static Identifier texture(int index) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/gui/guide/" + PAGES[index] + ".png");
    }
}
