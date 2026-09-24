package com.mnemolith.content.guide;

import com.mnemolith.Mnemolith;

import net.minecraft.resources.Identifier;

/**
 * Static pages of the field guide. Text lives in the language files.
 * The screen that draws these pages is client-only.
 */
public final class GuideBook {
    public static final String ITEM_ID = "field_guide";
    public static final int ART_WIDTH = 128;
    public static final int ART_HEIGHT = 64;

    private static final String[] PAGES = {
            "loop",
            "sources",
            "bands",
            "mute",
            "crafts",
            "formulas",
            "fails",
            "strider",
            "archivist",
            "replicant",
            "world",
            "players"
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
