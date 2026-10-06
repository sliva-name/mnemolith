package com.mnemolith.client.recall;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.network.PastVisionPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * While the world replays one of your past moments, the screen edge brightens to a soft rose and fades back.
 * The server decided to send it; this only draws. Off with the client option {@code pastVision}.
 */
public final class PastVision {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "past_vision");
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/misc/pressure_vignette.png");
    private static final int ROSE = 0xFFD6EA;
    private static final int MAX_ALPHA = 150;
    private static final int EDGE = 18;

    private static int total;
    private static int age;

    private PastVision() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.HOTBAR, LAYER, PastVision::render);
    }

    public static void accept(PastVisionPayload payload) {
        total = Math.max(1, payload.ticks());
        age = 0;
    }

    public static boolean active() {
        return total > 0 && age < total;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (total > 0 && ++age >= total) {
            total = 0;
            age = 0;
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        total = 0;
        age = 0;
    }

    /** 0 to 1: up over the first second, down over the last second. */
    static float strength(float t, int length) {
        float in = Mth.clamp(t / EDGE, 0.0F, 1.0F);
        float out = Mth.clamp((length - t) / EDGE, 0.0F, 1.0F);
        return Math.min(in, out);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!active() || !ClientConfig.PAST_VISION.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null || minecraft.gui.hud.isHidden()) {
            return;
        }
        float t = age + delta.getGameTimeDeltaPartialTick(false);
        int alpha = Mth.clamp(Math.round(MAX_ALPHA * strength(t, total)), 0, 255);
        if (alpha <= 0) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height, alpha << 24 | ROSE);
    }
}
