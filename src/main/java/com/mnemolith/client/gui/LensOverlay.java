package com.mnemolith.client.gui;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.render.PressureClient;
import com.mnemolith.config.ClientConfig;
import com.mnemolith.network.ChunkPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.ChunkState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Compact pressure pill. Drawn only while the chronicle lens is held. */
public final class LensOverlay {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "lens_pressure");

    private LensOverlay() {}

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
        Component line = line(here);
        Component detail = player.isShiftKeyDown() ? detail(here) : null;
        Font font = minecraft.font;
        int textWidth = font.width(line);
        if (detail != null) {
            textWidth = Math.max(textWidth, font.width(detail));
        }
        int boxWidth = textWidth + 16;
        int boxHeight = detail == null ? 16 : 26;
        int x = (minecraft.getWindow().getGuiScaledWidth() - boxWidth) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 68;
        int alpha = (int) Math.round(ClientConfig.OVERLAY_OPACITY.get() * 255.0D);
        alpha = Math.max(48, Math.min(255, alpha));
        graphics.fill(x, y, x + boxWidth, y + boxHeight, (alpha << 24) | 0x1C244A);
        graphics.fill(x, y, x + 2, y + boxHeight, 0xFF3E8E7E);
        graphics.text(font, line, x + 8, y + 4, GuiArt.BONE, false);
        if (detail != null) {
            graphics.text(font, detail, x + 8, y + 14, GuiArt.BONE, false);
        }
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
