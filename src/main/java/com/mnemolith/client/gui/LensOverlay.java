package com.mnemolith.client.gui;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.render.PressureClient;
import com.mnemolith.common.MemoryPalette;
import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.network.ChunkPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.ChunkState;

import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Compact pressure pill. Drawn only while the chronicle lens is held. */
public final class LensOverlay {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "lens_pressure");
    private static Component cachedLine = Component.empty();
    private static Component cachedDetail = Component.empty();
    private static int cachedKey = Integer.MIN_VALUE;

    /** Vanilla shows an action-bar message for 60 ticks at guiHeight - 68, right where the pill sits. */
    private static final long OVERLAY_MESSAGE_MILLIS = 3200L;
    /** How far the pill (and the echo hint above it) moves up while an action-bar message is shown. */
    public static final int MESSAGE_LIFT = 22;
    private static long overlayMessageUntil;

    private LensOverlay() {}

    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (event.isOverlay()) {
            overlayMessageUntil = Util.getMillis() + OVERLAY_MESSAGE_MILLIS;
        }
    }

    /** Extra upward offset while an action-bar message would otherwise sit on top of the pill. */
    public static int messageLift() {
        return Util.getMillis() < overlayMessageUntil ? MESSAGE_LIFT : 0;
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, LensOverlay::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!ClientConfig.LENS_OVERLAY.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || !PressureClient.holdsLens(player)) {
            return;
        }
        ChunkPressure here = PressureClient.origin(player);
        boolean sneak = player.isShiftKeyDown();
        int key = cacheKey(here, sneak, ClientConfig.SHOW_NUMERIC_PRESSURE.get());
        if (key != cachedKey) {
            cachedKey = key;
            cachedLine = line(here);
            cachedDetail = sneak ? detail(here) : null;
        }
        Component line = cachedLine;
        Component detail = sneak ? cachedDetail : null;
        Font font = minecraft.font;
        int textWidth = font.width(line);
        if (detail != null) {
            textWidth = Math.max(textWidth, font.width(detail));
        }
        int boxWidth = textWidth + 16;
        int boxHeight = detail == null ? 16 : 26;
        int x = (minecraft.getWindow().getGuiScaledWidth() - boxWidth) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 68 - messageLift();
        int alpha = (int) Math.round(ClientConfig.OVERLAY_OPACITY.get() * 255.0D);
        alpha = Math.max(48, Math.min(255, alpha));
        graphics.fill(x, y, x + boxWidth, y + boxHeight, (alpha << 24) | (GuiArt.INK & 0xFFFFFF));
        graphics.fill(x, y, x + 2, y + boxHeight, MemoryPalette.opaque(MemoryPalette.PIGMENT));
        GuiArt.label(graphics, font, line, x + 8, y + 4, GuiArt.BONE);
        if (detail != null) {
            GuiArt.label(graphics, font, detail, x + 8, y + 14, GuiArt.BONE);
        }
    }

    private static int cacheKey(ChunkPressure here, boolean sneak, boolean numeric) {
        int pressure = here == null ? -1 : here.pressure();
        int band = here == null ? -1 : here.band();
        int state = here == null ? -1 : here.state();
        return (pressure * 17) ^ (band * 31) ^ (state * 13) ^ (sneak ? 1 : 0) ^ (numeric ? 2 : 0);
    }

    private static Component line(ChunkPressure here) {
        if (here == null) {
            return Component.translatable("mnemolith.gui.lens_waiting");
        }
        Component band = Component.translatable(PressureBand.byOrdinal(here.band()).translationKey());
        if (!ClientConfig.SHOW_NUMERIC_PRESSURE.get()) {
            return band;
        }
        return Component.translatable("mnemolith.gui.lens_pressure", band, here.pressure());
    }

    private static Component detail(ChunkPressure here) {
        ChunkState state = ChunkState.NORMAL;
        if (here != null && here.state() >= 0 && here.state() < ChunkState.values().length) {
            state = ChunkState.values()[here.state()];
        }
        String name = switch (state) {
            case MUTED -> "muted";
            case ARCHIVAL -> "archival";
            case FRACTURED -> "fractured";
            case NORMAL -> "normal";
        };
        return Component.translatable("mnemolith.state." + name);
    }
}
