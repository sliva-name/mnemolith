package com.mnemolith.content.guide;

import com.mnemolith.Mnemolith;

import net.minecraft.resources.Identifier;

/**
 * Static pages of the field guide. Text lives in the language files.
 * The screen that draws these pages is client-only.
 * Dedicated QA expects {@link #pageCount()} to be {@link #PAGE_COUNT} (stage 3 added the echo page; memory grafts
 * added the recording and graft pages; residual echoes added the residue page; the recollection storm added the storm and Scar pages;
 * the echo relay and archive vault added the relay and vault pages).
 */
public final class GuideBook {
    public static final String ITEM_ID = "field_guide";
    public static final int ART_WIDTH = 256;
    public static final int ART_HEIGHT = 128;
    /** Pages QA expects. */
    public static final int PAGE_COUNT = 26;

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
            "recording",
            "echoes",
            "grafts",
            "residues",
            "storms",
            "scar",
            "relay",
            "vault",
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
