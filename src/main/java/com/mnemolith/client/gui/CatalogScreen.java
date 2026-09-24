package com.mnemolith.client.gui;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.composition.CompositionFormula;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ModAttachments;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Discovery list. Grows when the player extracts or composes. */
public class CatalogScreen extends Screen {
    private static final int PANEL_WIDTH = 248;
    private static final int PANEL_HEIGHT = 236;

    private int tags;
    private int formulas;

    public CatalogScreen(int tags, int formulas) {
        super(Component.translatable("mnemolith.gui.catalog"));
        this.tags = tags;
        this.formulas = formulas;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.done"), button -> this.onClose())
                .bounds(left + PANEL_WIDTH - 72, top + PANEL_HEIGHT - 26, 60, 18)
                .build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        this.refresh();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        GuiArt.panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.text(this.font, this.title, left + 10, top + 8, GuiArt.INK, false);

        if (this.tags == 0 && this.formulas == 0) {
            graphics.textWithWordWrap(this.font, Component.translatable("mnemolith.gui.catalog_empty"), left + 10, top + 28, PANEL_WIDTH - 20, GuiArt.INK);
            return;
        }

        graphics.text(this.font, Component.translatable("mnemolith.gui.catalog_tags"), left + 10, top + 24, GuiArt.INK, false);
        int row = 0;
        int shown = 0;
        for (ImprintTag tag : ImprintTag.values()) {
            if ((this.tags & (1 << tag.ordinal())) == 0) {
                continue;
            }
            int column = shown % 2;
            int line = shown / 2;
            int x = left + 10 + column * 116;
            int y = top + 36 + line * 16;
            GuiArt.tag(graphics, tag, x, y);
            graphics.text(this.font, Component.translatable(tag.translationKey()), x + 18, y + 4, GuiArt.INK, false);
            row = line;
            shown++;
        }
        int formulaY = top + 42 + (shown == 0 ? 0 : (row + 1) * 16);
        graphics.text(this.font, Component.translatable("mnemolith.gui.catalog_formulas"), left + 10, formulaY, GuiArt.INK, false);
        int written = 0;
        for (CompositionFormula formula : CompositionFormula.values()) {
            if ((this.formulas & (1 << formula.ordinal())) == 0) {
                continue;
            }
            int y = formulaY + 14 + written * 16;
            if (y > top + PANEL_HEIGHT - 48) {
                break;
            }
            int x = left + 10;
            for (ImprintTag tag : formula.tags()) {
                GuiArt.tag(graphics, tag, x, y);
                x += 16;
            }
            graphics.text(this.font, Component.translatable(formula.translationKey()), x + 4, y + 4, GuiArt.INK, false);
            written++;
        }
        if (written == 0) {
            graphics.text(this.font, Component.translatable("mnemolith.gui.catalog_no_formula"), left + 10, formulaY + 14, GuiArt.INK, false);
        }
        if (CommonConfig.DISCOVERY_HINTS.get()) {
            int unread = Discovery.FORMULA_COUNT - Integer.bitCount(this.formulas & ((1 << Discovery.FORMULA_COUNT) - 1));
            if (unread > 0) {
                graphics.text(this.font, Component.translatable("mnemolith.gui.catalog_remaining", unread), left + 10, top + PANEL_HEIGHT - 40, GuiArt.VERDIGRIS, false);
            }
        }
    }

    private void refresh() {
        if (this.minecraft == null) {
            return;
        }
        LocalPlayer player = this.minecraft.player;
        if (player != null && player.hasData(ModAttachments.DISCOVERY.get())) {
            Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
            this.tags = discovery.tags();
            this.formulas = discovery.formulas();
        }
    }
}
