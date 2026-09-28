package com.mnemolith.client.gui;

import java.util.List;

import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.CompositionRecipe;
import com.mnemolith.content.composition.CompositionRecipes;
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
        GuiArt.label(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, GuiArt.BONE);
        GuiArt.label(graphics, this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiArt.BONE);
        Component status = this.statusLine();
        int color = switch (this.menu.status()) {
            case ComposeResult.SUCCESS -> GuiArt.VERDIGRIS;
            case ComposeResult.FAIL, ComposeResult.DISABLED, ComposeResult.FULL -> GuiArt.FAIL;
            default -> GuiArt.BONE;
        };
        GuiArt.paragraph(graphics, this.font, status, 8, 74, this.imageWidth - 16, color);
        this.drawSilhouettes(graphics);
    }

    private Component statusLine() {
        return switch (this.menu.status()) {
            case ComposeResult.SUCCESS -> Component.translatable("mnemolith.gui.compose_success", this.formulaName(this.menu.formulaOrdinal()));
            case ComposeResult.FAIL -> Component.translatable("mnemolith.gui.compose_fail");
            case ComposeResult.EMPTY -> Component.translatable("mnemolith.gui.compose_empty");
            case ComposeResult.DISABLED -> Component.translatable("mnemolith.gui.compose_disabled");
            case ComposeResult.FULL -> Component.translatable("mnemolith.message.inventory_full");
            default -> Component.translatable("mnemolith.gui.compose_idle");
        };
    }

    private void drawSilhouettes(GuiGraphicsExtractor graphics) {
        int mask = this.menu.discoveredFormulas();
        boolean hints = this.menu.discoveryHints();
        List<CompositionRecipe> recipes = CompositionRecipes.all();
        int known = Integer.bitCount(mask & ((1 << Math.min(Discovery.FORMULA_COUNT, recipes.size())) - 1));
        if (!hints && known == 0) {
            GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.compose_no_pattern"), 8, 100, GuiArt.BONE);
            return;
        }
        int x = 8;
        int row = 100;
        for (int i = 0; i < recipes.size() && i < Discovery.FORMULA_COUNT; i++) {
            if (x > this.imageWidth - 40) {
                x = 8;
                row += 18;
            }
            x = drawFormulaChip(graphics, mask, hints, x, row, i, recipes.get(i).tags());
        }
    }

    private int drawFormulaChip(GuiGraphicsExtractor graphics, int mask, boolean hints, int x, int row, int index, List<ImprintTag> tags) {
        boolean learned = (mask & (1 << index)) != 0;
        int width = Math.max(34, tags.size() * 16);
        if (learned) {
            int iconX = x;
            for (ImprintTag tag : tags) {
                GuiArt.tag(graphics, tag, iconX, row);
                iconX += 16;
            }
        } else if (hints) {
            graphics.fill(x, row, x + width, row + 14, GuiArt.CHIP);
            GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.compose_unknown"), x + 13, row + 3, GuiArt.BONE);
        }
        return x + width + 8;
    }

    private Component formulaName(int ordinal) {
        return CompositionRecipes.byIndex(ordinal)
                .map(recipe -> Component.translatable(recipe.translationKey()))
                .orElseGet(() -> Component.translatable("mnemolith.gui.compose_unknown"));
    }
}
