package com.mnemolith.client.echo;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.client.gui.GuiArt;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.menu.EchoMenu;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.network.EchoJobPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The echo's own inventory, in the archival panel style, with the job panel on the right: mode buttons
 * (Повтор / Добыча / Стройка / Ферма / Стоп), radius, the linked chest, the blueprint, and the live status.
 * Stage 3 made the job panel taller than the inventory panel (a third lesson line and the farming button).
 * Everything shown comes from the echo's synced data; every button only sends a request the server checks.
 */
public class EchoInventoryScreen extends AbstractContainerScreen<EchoMenu> {
    private static final int PANEL_X = EchoMenu.WIDTH + 4;
    private static final int PANEL_W = 146;
    private static final int PAD = 6;
    private static final int BUTTON_W = 65;
    private static final int PINK = 0xFFFFC6E6;
    private static final int WARM = 0xFFFFB089;
    private static final int DIM = 0xFFA89FB8;
    /** Height of the job panel (the inventory panel keeps {@link EchoMenu#HEIGHT}). */
    private static final int JOB_H = 232;

    private @Nullable Button replay;
    private @Nullable Button mine;
    private @Nullable Button build;
    private @Nullable Button farm;
    private @Nullable Button stop;
    private @Nullable Button radiusDown;
    private @Nullable Button radiusUp;
    private @Nullable Button link;
    private @Nullable Button unlink;
    private @Nullable Button place;

    public EchoInventoryScreen(EchoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_X + PANEL_W, JOB_H);
        this.inventoryLabelX = EchoMenu.MAIN_X;
        this.inventoryLabelY = EchoMenu.PLAYER_Y - 11;
    }

    private @Nullable EchoEntity echo() {
        EchoEntity echo = this.menu.echo();
        return echo != null && echo.isAlive() ? echo : null;
    }

    private EchoLesson lesson() {
        return this.menu.lesson();
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = EchoMenu.MAIN_X;
        int x = this.leftPos + PANEL_X + PAD;
        int y = this.topPos;
        int right = x + BUTTON_W + 4;
        this.replay = this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.echo.mode.replay"), b -> this.send(EchoJobPayload.Action.MODE_REPLAY))
                .bounds(x, y + 51, BUTTON_W, 16).tooltip(Tooltip.create(Component.translatable("mnemolith.gui.echo.mode.replay.tip"))).build());
        this.mine = this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.echo.mode.mine"), b -> this.send(EchoJobPayload.Action.MODE_MINE))
                .bounds(right, y + 51, BUTTON_W, 16).build());
        this.build = this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.echo.mode.build"), b -> this.onBuild())
                .bounds(x, y + 69, BUTTON_W, 16).build());
        this.farm = this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.echo.mode.farm"), b -> this.send(EchoJobPayload.Action.MODE_FARM))
                .bounds(right, y + 69, BUTTON_W, 16).build());
        this.stop = this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.echo.mode.stop"), b -> this.send(EchoJobPayload.Action.STOP))
                .bounds(x, y + 87, BUTTON_W * 2 + 4, 16).tooltip(Tooltip.create(Component.translatable("mnemolith.gui.echo.mode.stop.tip"))).build());
        this.radiusDown = this.addRenderableWidget(Button.builder(Component.literal("−"), b -> this.changeRadius(-1))
                .bounds(x + 96, y + 108, 18, 16).build());
        this.radiusUp = this.addRenderableWidget(Button.builder(Component.literal("+"), b -> this.changeRadius(1))
                .bounds(x + 116, y + 108, 18, 16).build());
        this.link = this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.echo.link"), b -> this.onLink())
                .bounds(x, y + 139, BUTTON_W, 16).tooltip(Tooltip.create(Component.translatable("mnemolith.gui.echo.link.tip"))).build());
        this.unlink = this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.echo.unlink"), b -> this.send(EchoJobPayload.Action.UNLINK_CHEST))
                .bounds(right, y + 139, BUTTON_W, 16).build());
        this.place = this.addRenderableWidget(Button.builder(Component.translatable("mnemolith.gui.echo.place"), b -> this.onPlace())
                .bounds(x, y + 171, BUTTON_W * 2 + 4, 16).tooltip(Tooltip.create(Component.translatable("mnemolith.gui.echo.place.tip"))).build());
        this.refreshButtons();
    }

    private void send(EchoJobPayload.Action action) {
        EchoEntity echo = this.echo();
        if (echo != null) {
            ClientPacketDistributor.sendToServer(EchoJobPayload.simple(echo.getId(), action));
        }
    }

    private void changeRadius(int direction) {
        EchoEntity echo = this.echo();
        if (echo == null) {
            return;
        }
        int step = this.minecraft.hasShiftDown() ? 8 : 2;
        int max = CommonConfig.ECHO_MINE_MAX_RADIUS.get();
        int value = Math.max(2, Math.min(max, echo.jobRadius() + direction * step));
        ClientPacketDistributor.sendToServer(new EchoJobPayload(echo.getId(), EchoJobPayload.Action.RADIUS, BlockPos.ZERO, value));
    }

    private void onBuild() {
        EchoEntity echo = this.echo();
        if (echo == null) {
            return;
        }
        if (echo.jobAnchor().isPresent()) {
            this.send(EchoJobPayload.Action.MODE_BUILD);
        } else {
            this.onPlace();
        }
    }

    private void onPlace() {
        EchoEntity echo = this.echo();
        Optional<EchoLesson.Blueprint> blueprint = this.lesson().blueprint();
        if (echo != null && blueprint.isPresent()) {
            EchoJobClient.beginPlacement(echo.getId(), blueprint.get());
            this.onClose();
        }
    }

    private void onLink() {
        EchoEntity echo = this.echo();
        if (echo != null) {
            EchoJobClient.beginLink(echo.getId());
            this.onClose();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshButtons();
    }

    private void refreshButtons() {
        EchoEntity echo = this.echo();
        boolean alive = echo != null;
        EchoLesson lesson = this.lesson();
        boolean mining = alive && (echo.lessonFlags() & EchoEntity.LESSON_MINING) != 0;
        boolean building = alive && (echo.lessonFlags() & EchoEntity.LESSON_BUILDING) != 0 && lesson.teachesBuilding();
        boolean farming = alive && (echo.lessonFlags() & EchoEntity.LESSON_FARMING) != 0;
        set(this.replay, alive);
        set(this.mine, mining);
        set(this.build, building);
        set(this.farm, farming);
        set(this.stop, alive);
        set(this.radiusDown, alive);
        set(this.radiusUp, alive);
        set(this.link, alive);
        set(this.unlink, alive && echo.jobChest().isPresent());
        set(this.place, building);
        if (this.mine != null) {
            this.mine.setTooltip(Tooltip.create(Component.translatable(mining ? "mnemolith.gui.echo.mode.mine.tip" : "mnemolith.gui.echo.no_mining")));
        }
        if (this.farm != null) {
            this.farm.setTooltip(Tooltip.create(Component.translatable(farming ? "mnemolith.gui.echo.mode.farm.tip" : "mnemolith.gui.echo.no_farming")));
        }
        if (this.build != null) {
            this.build.setTooltip(Tooltip.create(Component.translatable(building ? "mnemolith.gui.echo.mode.build.tip" : "mnemolith.gui.echo.no_building")));
        }
    }

    private static void set(@Nullable Button button, boolean active) {
        if (button != null) {
            button.active = active;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiArt.panel(graphics, this.leftPos, this.topPos, EchoMenu.WIDTH, this.imageHeight);
        GuiArt.panel(graphics, this.leftPos + PANEL_X, this.topPos, PANEL_W, this.imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            GuiArt.slot(graphics, slot.x - 1, slot.y - 1);
        }
        GuiArt.label(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, GuiArt.BONE);
        GuiArt.label(graphics, this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiArt.BONE);
        this.extractJobPanel(graphics);
    }

    /** Coordinates here are relative to the screen's top-left corner. */
    private void extractJobPanel(GuiGraphicsExtractor graphics) {
        int x = PANEL_X + PAD;
        int width = PANEL_W - PAD * 2;
        EchoEntity echo = this.echo();
        GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.echo.job"), x, 7, GuiArt.BONE);
        List<Component> lesson = new java.util.ArrayList<>(this.lesson().describeShort());
        boolean farming = echo != null && (echo.lessonFlags() & EchoEntity.LESSON_FARMING) != 0;
        if (farming) {
            if (!this.lesson().teachesMining() && !this.lesson().teachesBuilding()) {
                lesson.clear();
            }
            lesson.add(Component.translatable("mnemolith.gui.echo.lesson.farming", echo.farmCrops()));
        }
        for (int i = 0; i < Math.min(3, lesson.size()); i++) {
            this.fitted(graphics, lesson.get(i), x, 19 + i * 10, width, PINK);
        }
        if (echo == null) {
            return;
        }
        // The active mode is underlined in pink.
        Button active = switch (echo.jobMode()) {
            case REPLAY -> this.replay;
            case MINE -> this.mine;
            case BUILD -> this.build;
            case FARM -> this.farm;
            default -> null;
        };
        if (active != null) {
            int bx = active.getX() - this.leftPos;
            int by = active.getY() - this.topPos + active.getHeight();
            graphics.fill(bx + 2, by, bx + active.getWidth() - 2, by + 1, PINK);
        }
        GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.echo.radius", echo.jobRadius()), x, 112, GuiArt.BONE);
        Component chest = echo.jobChest().map(EchoInventoryScreen::pos).orElse(Component.translatable("mnemolith.gui.echo.none"));
        this.fitted(graphics, Component.translatable("mnemolith.gui.echo.chest").append(": ").append(chest), x, 128, width,
                echo.jobChest().isPresent() ? GuiArt.BONE : DIM);
        Component anchor = echo.jobAnchor().map(EchoInventoryScreen::pos).orElse(Component.translatable("mnemolith.gui.echo.blueprint_none"));
        this.fitted(graphics, Component.translatable("mnemolith.gui.echo.blueprint").append(": ").append(anchor), x, 160, width,
                echo.jobAnchor().isPresent() ? GuiArt.BONE : DIM);
        GuiArt.label(graphics, this.font, Component.translatable("mnemolith.gui.echo.status"), x, 193, GuiArt.BONE);
        List<FormattedCharSequence> lines = this.font.split(echo.jobStatus(), width);
        int color = echo.jobStopped() ? WARM : PINK;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(this.font, lines.get(i), x + 1, 205 + i * 10, GuiArt.SHADOW, false);
            graphics.text(this.font, lines.get(i), x, 204 + i * 10, color, false);
        }
    }

    private void fitted(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        if (this.font.width(text) <= width) {
            GuiArt.label(graphics, this.font, text, x, y, color);
            return;
        }
        String plain = this.font.plainSubstrByWidth(text.getString(), width - this.font.width("…")) + "…";
        GuiArt.label(graphics, this.font, Component.literal(plain), x, y, color);
    }

    private static Component pos(BlockPos pos) {
        return Component.translatable("mnemolith.gui.echo.pos", pos.getX(), pos.getY(), pos.getZ());
    }
}
