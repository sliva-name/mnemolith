package com.mnemolith.client.gui;

import com.mnemolith.content.guide.GuideBook;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Illustrated field guide. One page at a time, dark ink on the bone page, art above a scrolling body. */
public class FieldGuideScreen extends Screen {
    private static final int DOT = 5;
    private static final int DOT_GAP = 3;

    private int page;
    private int scroll;
    private Button previous;
    private Button next;

    public FieldGuideScreen() {
        super(Component.translatable("item.mnemolith.field_guide"));
    }

    @Override
    protected void init() {
        Frame frame = this.frame();
        this.previous = Button.builder(Component.translatable("mnemolith.guide.prev"), button -> this.turn(-1))
                .bounds(frame.left + 8, frame.buttonY, 64, 18)
                .build();
        this.next = Button.builder(Component.translatable("mnemolith.guide.next"), button -> this.turn(1))
                .bounds(frame.left + 76, frame.buttonY, 64, 18)
                .build();
        this.addRenderableWidget(this.previous);
        this.addRenderableWidget(this.next);
        this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.done"), button -> this.onClose())
                .bounds(frame.left + frame.panelW - 72, frame.buttonY, 64, 18)
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) Math.signum(scrollY) * Math.max(this.font.lineHeight, 9);
        this.scroll = clampScroll(this.scroll - step);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.jumpFromDots(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Frame frame = this.frame();
        GuiArt.panel(graphics, frame.left, frame.top, frame.panelW, frame.panelH);
        int index = this.page;
        Identifier art = GuideBook.texture(index);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                art,
                frame.artX,
                frame.artY,
                0.0F,
                0.0F,
                frame.artW,
                frame.artH,
                GuideBook.ART_WIDTH,
                GuideBook.ART_HEIGHT,
                GuideBook.ART_WIDTH,
                GuideBook.ART_HEIGHT);
        GuiArt.label(
                graphics,
                this.font,
                Component.translatable(GuideBook.titleKey(index)),
                frame.left + 12,
                frame.top + 8,
                GuiArt.GUIDE_INK,
                GuiArt.GUIDE_SHADOW);
        Component pageLabel = Component.translatable("mnemolith.guide.page", index + 1, GuideBook.pageCount());
        int pageX = frame.left + frame.panelW - 12 - this.font.width(pageLabel);
        GuiArt.label(graphics, this.font, pageLabel, pageX, frame.top + 8, GuiArt.GUIDE_INK, GuiArt.GUIDE_SHADOW);
        Component body = Component.translatable(GuideBook.bodyKey(index));
        int textHeight = this.textHeight(body, frame.bodyW);
        int maxScroll = Math.max(0, textHeight - frame.bodyH);
        this.scroll = Math.min(this.scroll, maxScroll);
        graphics.enableScissor(frame.bodyX, frame.bodyY, frame.bodyX + frame.bodyW, frame.bodyY + frame.bodyH);
        GuiArt.paragraph(
                graphics,
                this.font,
                body,
                frame.bodyX,
                frame.bodyY - this.scroll,
                frame.bodyW - 6,
                GuiArt.GUIDE_INK,
                GuiArt.GUIDE_SHADOW);
        graphics.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(8, frame.bodyH * frame.bodyH / textHeight);
            int barY = frame.bodyY + (frame.bodyH - barH) * this.scroll / maxScroll;
            graphics.fill(frame.bodyX + frame.bodyW - 3, barY, frame.bodyX + frame.bodyW - 1, barY + barH, GuiArt.GUIDE_INK);
        }
        this.drawDots(graphics, frame);
    }

    private void drawDots(GuiGraphicsExtractor graphics, Frame frame) {
        int count = GuideBook.pageCount();
        for (int index = 0; index < count; index++) {
            int x = frame.dotsX + index * (DOT + DOT_GAP);
            int color = index == this.page ? GuiArt.GUIDE_INK : GuiArt.GUIDE_DOT;
            graphics.fill(x, frame.dotsY, x + DOT, frame.dotsY + DOT, color);
        }
    }

    private boolean jumpFromDots(double mouseX, double mouseY) {
        Frame frame = this.frame();
        int count = GuideBook.pageCount();
        int rowW = count * DOT + (count - 1) * DOT_GAP;
        if (mouseY < frame.dotsY - 3 || mouseY > frame.dotsY + DOT + 3) {
            return false;
        }
        if (mouseX < frame.dotsX || mouseX >= frame.dotsX + rowW) {
            return false;
        }
        int index = (int) ((mouseX - frame.dotsX) / (DOT + DOT_GAP));
        if (index < 0 || index >= count) {
            return false;
        }
        if (index != this.page) {
            this.page = index;
            this.scroll = 0;
            this.syncButtons();
        }
        return true;
    }

    private int textHeight(Component body, int width) {
        int lines = Math.max(1, this.font.split(body, Math.max(8, width - 6)).size());
        return lines * this.font.lineHeight + 1;
    }

    private int clampScroll(int value) {
        Component body = Component.translatable(GuideBook.bodyKey(this.page));
        Frame frame = this.frame();
        int maxScroll = Math.max(0, this.textHeight(body, frame.bodyW) - frame.bodyH);
        return Math.max(0, Math.min(maxScroll, value));
    }

    private void turn(int delta) {
        int next = this.page + delta;
        if (next < 0 || next >= GuideBook.pageCount()) {
            return;
        }
        this.page = next;
        this.scroll = 0;
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

    private Frame frame() {
        int panelW = Math.min(400, Math.max(160, this.width - 16));
        int panelH = Math.min(372, Math.max(180, this.height - 16));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;
        int artW = Math.min(GuideBook.ART_WIDTH, panelW - 24);
        int artH = artW * GuideBook.ART_HEIGHT / GuideBook.ART_WIDTH;
        int maxArtH = Math.max(40, (panelH - 96) * 2 / 5);
        if (artH > maxArtH) {
            artH = maxArtH;
            artW = Math.max(64, artH * GuideBook.ART_WIDTH / GuideBook.ART_HEIGHT);
        }
        int artX = left + (panelW - artW) / 2;
        int artY = top + 22;
        int bodyX = left + 12;
        int bodyY = artY + artH + 6;
        int bodyW = panelW - 24;
        int buttonY = top + panelH - 26;
        int dotsY = buttonY - 12;
        int bodyH = Math.max(this.font.lineHeight * 3, dotsY - 4 - bodyY);
        int count = GuideBook.pageCount();
        int rowW = count * DOT + (count - 1) * DOT_GAP;
        int dotsX = left + (panelW - rowW) / 2;
        return new Frame(left, top, panelW, panelH, artX, artY, artW, artH, bodyX, bodyY, bodyW, bodyH, dotsX, dotsY, buttonY);
    }

    private record Frame(
            int left,
            int top,
            int panelW,
            int panelH,
            int artX,
            int artY,
            int artW,
            int artH,
            int bodyX,
            int bodyY,
            int bodyW,
            int bodyH,
            int dotsX,
            int dotsY,
            int buttonY) {}
}
