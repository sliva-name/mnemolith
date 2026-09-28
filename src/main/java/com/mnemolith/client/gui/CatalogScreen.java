package com.mnemolith.client.gui;

import java.util.List;

import com.mnemolith.content.composition.CompositionRecipe;
import com.mnemolith.content.composition.CompositionRecipes;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.recall.AnchorKind;
import com.mnemolith.recall.FoundMemory;
import com.mnemolith.recall.GestureKind;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Discovery list. Grows when the player extracts, composes, or reads a memory with the lens. */
public class CatalogScreen extends Screen {
    private static final int PANEL_WIDTH = 248;
    private static final int PANEL_HEIGHT = 236;
    private static final Component[] TAG_LABELS = labels(ImprintTag.values());
    private static final Component[] FORMULA_LABELS = formulaLabels();
    private static final Component[] GESTURE_LABELS = gestureLabels();
    private static final Component[] ANCHOR_LABELS = anchorLabels();
    private static final Component WARPED = Component.translatable("mnemolith.memory.warped");
    private static final Component IMPRINT = Component.translatable("mnemolith.memory.imprint");

    private int tags;
    private int formulas;
    private int gestures;
    private int anchors;
    private boolean distorted;
    private boolean imprint;
    private final boolean hints;
    private int laidLines = -1;

    public CatalogScreen(int tags, int formulas, boolean hints) {
        super(Component.translatable("mnemolith.gui.catalog"));
        this.tags = tags;
        this.formulas = formulas;
        this.hints = hints;
    }

    @Override
    protected void init() {
        this.refresh();
        this.layout();
    }

    private void layout() {
        this.clearWidgets();
        this.laidLines = this.memoryLines();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - this.panelHeight()) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.done"), button -> this.onClose())
                .bounds(left + PANEL_WIDTH - 72, top + this.panelHeight() - 26, 60, 18)
                .build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int height = this.panelHeight();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - height) / 2;
        GuiArt.panel(graphics, left, top, PANEL_WIDTH, height);
        GuiArt.label(graphics, this.font, this.title, left + 10, top + 8, GuiArt.BONE);

        boolean memories = this.memoryLines() > 0;
        if (this.tags == 0 && this.formulas == 0 && !memories) {
            GuiArt.paragraph(graphics, this.font, Component.translatable("mnemolith.gui.catalog_empty"), left + 10, top + 28, PANEL_WIDTH - 20, GuiArt.BONE);
            return;
        }

        if (this.tags != 0 || this.formulas != 0) {
            GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.catalog_tags"), left + 10, top + 24, GuiArt.BONE);
        }
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
            GuiArt.label(graphics, this.font, TAG_LABELS[tag.ordinal()], x + 18, y + 4, GuiArt.BONE);
            row = line;
            shown++;
        }
        int formulaY = top + (shown == 0 && this.tags == 0 ? 24 : 42 + (shown == 0 ? 0 : (row + 1) * 16));
        int written = 0;
        if (this.formulas != 0 || shown != 0 || this.tags != 0) {
            GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.catalog_formulas"), left + 10, formulaY, GuiArt.BONE);
            List<CompositionRecipe> recipes = CompositionRecipes.all();
            for (int i = 0; i < recipes.size() && i < Discovery.FORMULA_COUNT; i++) {
                if ((this.formulas & (1 << i)) == 0) {
                    continue;
                }
                CompositionRecipe formula = recipes.get(i);
                int y = formulaY + 14 + written * 16;
                if (y > top + height - 48) {
                    break;
                }
                int x = left + 10;
                for (ImprintTag tag : formula.tags()) {
                    GuiArt.tag(graphics, tag, x, y);
                    x += 16;
                }
                Component label = i < FORMULA_LABELS.length ? FORMULA_LABELS[i] : Component.translatable(formula.translationKey());
                GuiArt.label(graphics, this.font, label, x + 4, y + 4, GuiArt.BONE);
                written++;
            }
            if (written == 0 && (this.tags != 0 || this.formulas != 0)) {
                GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.catalog_no_formula"), left + 10, formulaY + 14, GuiArt.BONE);
            }
        }
        if (memories) {
            int memoryY = this.tags == 0 && this.formulas == 0
                    ? top + 28
                    : formulaY + 14 + Math.max(written, 1) * 16 + 8;
            GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.catalog_memory"), left + 10, memoryY, GuiArt.BONE);
            int index = 0;
            for (GestureKind kind : GestureKind.values()) {
                if ((this.gestures & (1 << kind.ordinal())) == 0) {
                    continue;
                }
                this.memoryLine(graphics, left, memoryY, index, GESTURE_LABELS[kind.ordinal()]);
                index++;
            }
            for (AnchorKind kind : AnchorKind.values()) {
                if ((this.anchors & (1 << kind.ordinal())) == 0) {
                    continue;
                }
                this.memoryLine(graphics, left, memoryY, index, ANCHOR_LABELS[kind.ordinal()]);
                index++;
            }
            if (this.distorted) {
                this.memoryLine(graphics, left, memoryY, index, WARPED);
                index++;
            }
            if (this.imprint) {
                this.memoryLine(graphics, left, memoryY, index, IMPRINT);
            }
        }
        if (this.hints) {
            int unread = Discovery.FORMULA_COUNT - Integer.bitCount(this.formulas & ((1 << Discovery.FORMULA_COUNT) - 1));
            if (unread > 0) {
                GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.catalog_remaining", unread), left + 10, top + height - 40, GuiArt.VERDIGRIS);
            }
        }
    }

    private void memoryLine(GuiGraphicsExtractor graphics, int left, int memoryY, int index, Component label) {
        int column = index % 2;
        int line = index / 2;
        int x = left + 10 + column * 116;
        int y = memoryY + 14 + line * 12;
        GuiArt.label(graphics, this.font, label, x, y, GuiArt.BONE);
    }

    @Override
    public void tick() {
        super.tick();
        int before = this.memoryLines();
        this.refresh();
        if (this.memoryLines() != before || this.laidLines != this.memoryLines()) {
            this.layout();
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
        if (player != null && player.hasData(ModAttachments.FOUND_MEMORY.get())) {
            FoundMemory found = player.getData(ModAttachments.FOUND_MEMORY.get());
            this.gestures = found.gestures();
            this.anchors = found.anchors();
            this.distorted = found.distorted();
            this.imprint = found.imprint();
        }
    }

    private int memoryLines() {
        return Integer.bitCount(this.gestures) + Integer.bitCount(this.anchors) + (this.distorted ? 1 : 0) + (this.imprint ? 1 : 0);
    }

    private int panelHeight() {
        int lines = this.memoryLines();
        if (lines == 0) {
            return PANEL_HEIGHT;
        }
        int rows = (lines + 1) / 2;
        return PANEL_HEIGHT + 20 + rows * 12;
    }

    private static Component[] labels(ImprintTag[] tags) {
        Component[] out = new Component[tags.length];
        for (ImprintTag tag : tags) {
            out[tag.ordinal()] = Component.translatable(tag.translationKey());
        }
        return out;
    }

    private static Component[] formulaLabels() {
        List<CompositionRecipe> recipes = CompositionRecipes.all();
        Component[] out = new Component[Math.max(recipes.size(), 1)];
        for (int i = 0; i < recipes.size(); i++) {
            out[i] = Component.translatable(recipes.get(i).translationKey());
        }
        return out;
    }

    private static Component[] gestureLabels() {
        GestureKind[] kinds = GestureKind.values();
        Component[] out = new Component[kinds.length];
        for (GestureKind kind : kinds) {
            out[kind.ordinal()] = Component.translatable("mnemolith.memory.gesture." + kind.getSerializedName());
        }
        return out;
    }

    private static Component[] anchorLabels() {
        AnchorKind[] kinds = AnchorKind.values();
        Component[] out = new Component[kinds.length];
        for (AnchorKind kind : kinds) {
            out[kind.ordinal()] = Component.translatable("mnemolith.memory.anchor." + kind.getSerializedName());
        }
        return out;
    }
}
