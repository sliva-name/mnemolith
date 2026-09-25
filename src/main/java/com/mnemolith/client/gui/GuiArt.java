package com.mnemolith.client.gui;

import com.mnemolith.Mnemolith;
import com.mnemolith.common.MemoryPalette;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Archival panel, slot, and tag icons. Drawn with the GUI texture pipeline.
 * Catalog, lens, and reel glyphs stay bone or a light accent, with {@link #SHADOW}.
 * The field-guide page is the bone center of the same panel, so its title and body use {@link #GUIDE_INK}.
 */
public final class GuiArt {
    public static final Identifier PANEL = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/gui/panel.png");
    public static final Identifier SLOT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/gui/slot.png");
    public static final Identifier TAGS = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/gui/tags.png");

    /** Panel fill. Not a glyph color. */
    public static final int INK = MemoryPalette.opaque(MemoryPalette.INK);
    /** Light glyph on the dark panel. */
    public static final int BONE = MemoryPalette.opaque(MemoryPalette.BONE);
    /** Drop shadow. Darker than {@link #INK}, not {@code #000000}, and not equal to {@link #BONE}. */
    public static final int SHADOW = MemoryPalette.opaque(MemoryPalette.SHADOW);
    /** Light verdigris glyph. The pigment {@code #3E8E7E} is too close to the panel for small text. */
    public static final int VERDIGRIS = MemoryPalette.opaque(MemoryPalette.VERDIGRIS);
    /** Light ember glyph for a failed compose. */
    public static final int FAIL = MemoryPalette.opaque(MemoryPalette.FAIL);
    /** Chip behind an unread formula. Darker than the panel so bone text stays separated. */
    public static final int CHIP = MemoryPalette.opaque(MemoryPalette.CHIP);
    /**
     * Near-black ink for field-guide title and body.
     * The page fill is bone ({@link #BONE}), so a bone glyph disappears into it.
     */
    public static final int GUIDE_INK = MemoryPalette.opaque(MemoryPalette.GUIDE_INK);
    /**
     * Drop shadow under field-guide ink. Light bone, one pixel down-right.
     * Not equal to {@link #GUIDE_INK}, and not the dark panel shadow {@link #SHADOW}.
     */
    public static final int GUIDE_SHADOW = MemoryPalette.opaque(MemoryPalette.GUIDE_SHADOW);
    /** Inactive page dot on the bone page. Dark enough to read, lighter than {@link #GUIDE_INK}. */
    public static final int GUIDE_DOT = MemoryPalette.opaque(MemoryPalette.GUIDE_DOT);

    private static final int BORDER = 4;
    private static final int PANEL_SIZE = 32;

    private GuiArt() {}

    /**
     * Glyph, then a shadow one pixel down and right. The font shadow flag stays off:
     * that shadow is near-black and fights a custom light shadow on the field-guide page.
     * Catalog, lens, and reel keep {@link #SHADOW}.
     */
    public static void label(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        label(graphics, font, text, x, y, color, SHADOW);
    }

    public static void label(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, int shadow) {
        graphics.text(font, text, x + 1, y + 1, shadow, false);
        graphics.text(font, text, x, y, color, false);
    }

    public static void paragraph(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color) {
        paragraph(graphics, font, text, x, y, width, color, SHADOW);
    }

    public static void paragraph(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color, int shadow) {
        graphics.textWithWordWrap(font, text, x + 1, y + 1, width, shadow, false);
        graphics.textWithWordWrap(font, text, x, y, width, color, false);
    }

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
