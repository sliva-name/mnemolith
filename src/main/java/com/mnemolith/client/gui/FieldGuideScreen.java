package com.mnemolith.client.gui;

import com.mnemolith.content.guide.GuideBook;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Illustrated field guide. One page at a time, bone text on the ink panel. */
public class FieldGuideScreen extends Screen {
    private static final int PANEL_WIDTH = 276;
    private static final int PANEL_HEIGHT = 248;

    private int page;
    private Button previous;
    private Button next;

    public FieldGuideScreen() {
        super(Component.translatable("item.mnemolith.field_guide"));
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        this.previous = Button.builder(Component.translatable("mnemolith.guide.prev"), button -> this.turn(-1))
                .bounds(left + 8, top + PANEL_HEIGHT - 26, 60, 18)
                .build();
        this.next = Button.builder(Component.translatable("mnemolith.guide.next"), button -> this.turn(1))
                .bounds(left + 72, top + PANEL_HEIGHT - 26, 60, 18)
                .build();
        this.addRenderableWidget(this.previous);
        this.addRenderableWidget(this.next);
        this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.done"), button -> this.onClose())
                .bounds(left + PANEL_WIDTH - 68, top + PANEL_HEIGHT - 26, 60, 18)
                .build());
        this.syncButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_LEFT) {
            this.turn(-1);
            return true;
        }
        if (event.key() == InputConstants.KEY_RIGHT) {
            this.turn(1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        GuiArt.panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        int index = this.page;
        Identifier art = GuideBook.texture(index);
        int artX = left + (PANEL_WIDTH - GuideBook.ART_WIDTH) / 2;
        int artY = top + 24;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                art,
                artX,
                artY,
                0.0F,
                0.0F,
                GuideBook.ART_WIDTH,
                GuideBook.ART_HEIGHT,
                GuideBook.ART_WIDTH,
                GuideBook.ART_HEIGHT);
        GuiArt.label(graphics, this.font, Component.translatable(GuideBook.titleKey(index)), left + 12, top + 8, GuiArt.BONE);
        GuiArt.paragraph(
                graphics,
                this.font,
                Component.translatable(GuideBook.bodyKey(index)),
                left + 12,
                artY + GuideBook.ART_HEIGHT + 6,
                PANEL_WIDTH - 24,
                GuiArt.BONE);
        GuiArt.label(
                graphics,
                this.font,
                Component.translatable("mnemolith.guide.page", index + 1, GuideBook.pageCount()),
                left + 140,
                top + PANEL_HEIGHT - 22,
                GuiArt.VERDIGRIS);
    }

    private void turn(int delta) {
        int next = this.page + delta;
        if (next < 0 || next >= GuideBook.pageCount()) {
            return;
        }
        this.page = next;
        this.syncButtons();
    }

    private void syncButtons() {
        if (this.previous != null) {
            this.previous.active = this.page > 0;
        }
        if (this.next != null) {
            this.next.active = this.page < GuideBook.pageCount() - 1;
        }
    }
}
