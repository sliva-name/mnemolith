package com.mnemolith.client.gui;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.CompositionFormula;
import com.mnemolith.content.menu.CompositionMenu;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class CompositionScreen extends AbstractContainerScreen<CompositionMenu> {
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 230;

    public CompositionScreen(CompositionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.compose"), button -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ImprintConstants.COMPOSE_BUTTON_ID);
            }
        }).bounds(this.leftPos + 48, this.topPos + 52, 80, 18).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        GuiArt.panel(graphics, x, y, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            GuiArt.slot(graphics, slot.x - 1, slot.y - 1);
        }
        super.extractLabels(graphics, mouseX, mouseY);
        Component status = this.statusLine();
        int color = switch (this.menu.status()) {
            case ComposeResult.SUCCESS -> GuiArt.VERDIGRIS;
            case ComposeResult.FAIL, ComposeResult.DISABLED -> GuiArt.FAIL;
            default -> GuiArt.INK;
        };
        graphics.textWithWordWrap(this.font, status, 8, 74, this.imageWidth - 16, color);
        this.drawSilhouettes(graphics);
    }

    private Component statusLine() {
        return switch (this.menu.status()) {
            case ComposeResult.SUCCESS -> Component.translatable("mnemolith.gui.compose_success", this.formulaName(this.menu.formulaOrdinal()));
            case ComposeResult.FAIL -> Component.translatable("mnemolith.gui.compose_fail");
            case ComposeResult.EMPTY -> Component.translatable("mnemolith.gui.compose_empty");
            case ComposeResult.DISABLED -> Component.translatable("mnemolith.gui.compose_disabled");
            default -> Component.translatable("mnemolith.gui.compose_idle");
        };
    }

    private void drawSilhouettes(GuiGraphicsExtractor graphics) {
        int mask = this.menu.discoveredFormulas();
        boolean hints = CommonConfig.DISCOVERY_HINTS.get();
        int known = Integer.bitCount(mask & ((1 << Discovery.FORMULA_COUNT) - 1));
        if (!hints && known == 0) {
            graphics.text(this.font, Component.translatable("mnemolith.gui.compose_no_pattern"), 8, 100, GuiArt.INK, false);
            return;
        }
        int x = 8;
        for (CompositionFormula formula : CompositionFormula.values()) {
            boolean learned = (mask & (1 << formula.ordinal())) != 0;
            if (learned) {
                int iconX = x;
                for (ImprintTag tag : formula.tags()) {
                    GuiArt.tag(graphics, tag, iconX, 100);
                    iconX += 16;
                }
            } else if (hints) {
                graphics.fill(x, 100, x + 34, 114, 0xFF1C244A);
                graphics.text(this.font, Component.translatable("mnemolith.gui.compose_unknown"), x + 13, 103, GuiArt.BONE, false);
            }
            x += 42;
        }
    }

    private Component formulaName(int ordinal) {
        CompositionFormula[] formulas = CompositionFormula.values();
        if (ordinal < 0 || ordinal >= formulas.length) {
            return Component.translatable("mnemolith.gui.compose_unknown");
        }
        return Component.translatable(formulas[ordinal].translationKey());
    }
}
