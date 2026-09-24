package com.mnemolith.client.gui;

import com.mnemolith.Mnemolith;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Archival panel, slot, and tag icons. Drawn with the GUI texture pipeline. */
public final class GuiArt {
    public static final Identifier PANEL = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/gui/panel.png");
    public static final Identifier SLOT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/gui/slot.png");
    public static final Identifier TAGS = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/gui/tags.png");

    public static final int INK = 0xFF1C244A;
    public static final int BONE = 0xFFE6DCC8;
    public static final int VERDIGRIS = 0xFF1F6B5C;
    public static final int FAIL = 0xFF8C2F2F;

    private static final int BORDER = 4;
    private static final int PANEL_SIZE = 32;

    private GuiArt() {}

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int border = BORDER;
        int size = PANEL_SIZE;
        int middle = size - border * 2;
        blit(graphics, x, y, border, border, 0, 0, border, border);
        blit(graphics, x + width - border, y, border, border, size - border, 0, border, border);
        blit(graphics, x, y + height - border, border, border, 0, size - border, border, border);
        blit(graphics, x + width - border, y + height - border, border, border, size - border, size - border, border, border);
        blit(graphics, x + border, y, width - border * 2, border, border, 0, middle, border);
        blit(graphics, x + border, y + height - border, width - border * 2, border, border, size - border, middle, border);
        blit(graphics, x, y + border, border, height - border * 2, 0, border, border, middle);
        blit(graphics, x + width - border, y + border, border, height - border * 2, size - border, border, border, middle);
        blit(graphics, x + border, y + border, width - border * 2, height - border * 2, border, border, middle, middle);
    }

    public static void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 0.0F, 0.0F, 18, 18, 18, 18);
    }

    public static void tag(GuiGraphicsExtractor graphics, ImprintTag imprintTag, int x, int y) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TAGS, x, y, imprintTag.ordinal() * 16.0F, 0.0F, 16, 16, 144, 16);
    }

    private static void blit(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int u, int v, int srcWidth, int srcHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, x, y, u, v, width, height, srcWidth, srcHeight, PANEL_SIZE, PANEL_SIZE);
    }
}
