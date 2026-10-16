package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.common.MemoryGlyphs;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Memory feel, stage 4: while the remembering circlet is worn (it wards memory around you), the screen edge holds a
 * faint steady tint in the kept colour, fading in and out over about a second. Drawn below the strong-memory hint and the
 * pressure vignette. Toggle: {@code circletTint}.
 */
public final class CircletTint {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "circlet_tint");
    private static final float MAX_ALPHA = 48.0F;
    private static float amount;

    private CircletTint() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(StrongMemoryHint.LAYER, LAYER, CircletTint::render);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean on = ClientConfig.CIRCLET_TINT.get() && minecraft.player != null && MemoryGlyphs.circletTint(minecraft.player);
        amount += ((on ? 1.0F : 0.0F) - amount) * 0.1F;
        if (!on && amount < 0.003F) {
            amount = 0.0F;
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (amount < 0.01F || !ClientConfig.CIRCLET_TINT.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null || minecraft.gui.hud.isHidden()) {
            return;
        }
        int alpha = Mth.clamp(Math.round(amount * MAX_ALPHA), 0, 255);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, FractureFeel.VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height, (alpha << 24) | MemoryGlyphs.CIRCLET_TINT);
    }
}
