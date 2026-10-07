package com.mnemolith.client.gui;

import java.util.List;

import com.mnemolith.network.ServerTuning;
import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.CompositionRecipe;
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
    /**
     * 240 = the smallest GUI height vanilla's auto scale guarantees, so the panel is never cut off
     * (1280x720, 1366x768 and 2560x1440 all end up with 240..256 GUI pixels). Chips: 4 rows starting at
     * y={@link #CHIP_TOP}, step {@link #CHIP_STEP}, last row ends at y=142, above the inventory title (imageHeight-94=146).
     */
    private static final int PANEL_HEIGHT = 240;
    private static final int CHIP_TOP = 80;
    private static final int CHIP_STEP = 16;
    /** Last pixel row chips may occupy; keep a gap above {@link #inventoryLabelY}. */
    private static final int CHIP_MAX_BOTTOM = 142;

    public CompositionScreen(CompositionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title.copy().withStyle(net.minecraft.ChatFormatting.BOLD))) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.compose"), button -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ImprintConstants.COMPOSE_BUTTON_ID);
            }
        }).bounds(this.leftPos + 48, this.topPos + 38, 80, 18).build());
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
        GuiArt.heading(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY);
        GuiArt.ink(graphics, this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiArt.PAPER_INK);
        Component status = this.statusLine();
        int color = switch (this.menu.status()) {
            case ComposeResult.SUCCESS -> GuiArt.PAPER_ACCENT;
            case ComposeResult.FAIL, ComposeResult.DISABLED, ComposeResult.FULL, ComposeResult.BLANK -> GuiArt.PAPER_WARN;
            default -> GuiArt.PAPER_INK;
        };
        GuiArt.inkParagraph(graphics, this.font, status, 8, 60, this.imageWidth - 16, color);
        this.drawSilhouettes(graphics);
    }

    private Component statusLine() {
        return switch (this.menu.status()) {
            case ComposeResult.SUCCESS -> Component.translatable("mnemolith.gui.compose_success", this.formulaName(this.menu.formulaOrdinal()));
            case ComposeResult.FAIL -> Component.translatable("mnemolith.gui.compose_fail");
            case ComposeResult.EMPTY -> Component.translatable("mnemolith.gui.compose_empty");
            case ComposeResult.DISABLED -> Component.translatable("mnemolith.gui.compose_disabled");
            case ComposeResult.FULL -> Component.translatable("mnemolith.message.inventory_full");
            case ComposeResult.BLANK -> Component.translatable("mnemolith.gui.compose_blank");
            default -> Component.translatable("mnemolith.gui.compose_idle");
        };
    }

    /** How long a newly learned formula's chip glows, in milliseconds. */
    private static final long GLOW_MILLIS = 2500L;
    /** Mask seen on the last frame; -1 until the first frame, so formulas known before opening do not glow. */
    private int seenMask = -1;
    private int glowIndex = -1;
    private long glowUntil;
    /** The "not read yet" chip, in screen space, for its tooltip (width 0 when it is not drawn). */
    private int unknownX;
    private int unknownY;
    private int unknownW;
    private int unknownCount;

    private void drawSilhouettes(GuiGraphicsExtractor graphics) {
        int mask = this.menu.discoveredFormulas();
        boolean hints = this.menu.discoveryHints();
        List<CompositionRecipe> recipes = ServerTuning.formulas();
        int count = Math.min(Discovery.FORMULA_COUNT, recipes.size());
        int all = count >= 31 ? -1 : (1 << count) - 1;
        this.noticeDiscovery(mask & all);
        int known = Integer.bitCount(mask & all);
        this.unknownW = 0;
        if (!hints && known == 0) {
            GuiArt.ink(graphics, this.font, Component.translatable("mnemolith.gui.compose_no_pattern"), 8, CHIP_TOP, GuiArt.PAPER_INK);
            return;
        }
        int x = 8;
        int row = CHIP_TOP;
        // Inventory title sits at imageHeight - 94 (=146 with PANEL_HEIGHT 240). Chips must stay above it.
        int chipFloor = Math.min(CHIP_MAX_BOTTOM, this.inventoryLabelY - 4);
        // Learned formulas first, then one chip that counts the rest (it used to be a wall of 15 "?").
        for (int i = 0; i < count; i++) {
            if ((mask & (1 << i)) == 0) {
                continue;
            }
            int width = Math.max(34, recipes.get(i).tags().size() * 16);
            if (x + width > this.imageWidth - 8) {
                x = 8;
                row += CHIP_STEP;
            }
            if (row + 14 > chipFloor) {
                return;
            }
            this.drawLearnedChip(graphics, x, row, i, recipes.get(i).tags());
            x += width + 8;
        }
        int unknown = count - known;
        if (hints && unknown > 0) {
            Component label = Component.translatable("mnemolith.gui.compose_unknown_more", unknown);
            int width = Math.max(34, this.font.width(label) + 12);
            if (x + width > this.imageWidth - 8) {
                x = 8;
                row += CHIP_STEP;
            }
            if (row + 14 <= chipFloor) {
                graphics.fill(x, row, x + width, row + 14, GuiArt.CHIP);
                GuiArt.label(graphics, this.font, label, x + (width - this.font.width(label)) / 2, row + 3, GuiArt.BONE);
                this.unknownX = x;
                this.unknownY = row;
                this.unknownW = width;
                this.unknownCount = unknown;
            }
        }
    }

    private void drawLearnedChip(GuiGraphicsExtractor graphics, int x, int row, int index, List<ImprintTag> tags) {
        long now = net.minecraft.util.Util.getMillis();
        if (index == this.glowIndex && now < this.glowUntil) {
            // A pulsing frame around the formula just learned.
            float pulse = 0.5F + 0.5F * net.minecraft.util.Mth.sin((float) (this.glowUntil - now) / 120.0F);
            int alpha = (int) (110 + 145 * pulse);
            int color = (alpha << 24) | (GuiArt.PAPER_ACCENT & 0xFFFFFF);
            int width = tags.size() * 16;
            graphics.fill(x - 2, row - 2, x + width + 1, row - 1, color);
            graphics.fill(x - 2, row + 15, x + width + 1, row + 16, color);
            graphics.fill(x - 2, row - 1, x - 1, row + 15, color);
            graphics.fill(x + width, row - 1, x + width + 1, row + 15, color);
        }
        int iconX = x;
        for (ImprintTag tag : tags) {
            GuiArt.tag(graphics, tag, iconX, row);
            iconX += 16;
        }
    }

    /** A formula learned while the screen is open glows for a moment, with a chime, so the discovery is not missed. */
    private void noticeDiscovery(int mask) {
        if (this.seenMask >= 0 && mask != this.seenMask) {
            int fresh = mask & ~this.seenMask;
            if (fresh != 0) {
                this.glowIndex = Integer.numberOfTrailingZeros(fresh);
                this.glowUntil = net.minecraft.util.Util.getMillis() + GLOW_MILLIS;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.5F, 0.6F));
            }
        }
        this.seenMask = mask;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        if (this.unknownW > 0 && x >= this.unknownX && x < this.unknownX + this.unknownW && y >= this.unknownY && y < this.unknownY + 14) {
            graphics.setTooltipForNextFrame(this.font, List.of(
                    Component.translatable("mnemolith.gui.compose_unknown_tip", this.unknownCount),
                    Component.translatable("mnemolith.gui.compose_unknown_how").withStyle(net.minecraft.ChatFormatting.GRAY)),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private Component formulaName(int ordinal) {
        return ServerTuning.formula(ordinal)
                .map(recipe -> Component.translatable(recipe.translationKey()))
                .orElseGet(() -> Component.translatable("mnemolith.gui.compose_unknown"));
    }
}
