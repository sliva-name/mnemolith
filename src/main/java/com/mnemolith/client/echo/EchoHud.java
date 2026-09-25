package com.mnemolith.client.echo;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.client.gui.GuiArt;
import com.mnemolith.echo.EchoView;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Small hints: "LMB — possess" and the lens orders (Z/R/B) on a targeted echo, and "V — return" while possessing. */
public final class EchoHud {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "echo_hints");
    private static final int PINK = 0xFFFFC6E6;
    private static final int CHIP = 0xB0200A18;

    private EchoHud() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, EchoHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!ClientConfig.ECHO_HINTS.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        Font font = minecraft.font;
        int centerX = graphics.guiWidth() / 2;
        if (EchoView.possessed()) {
            Component line = Component.translatable("mnemolith.hud.unpossess_hint", ThermalClient.UNPOSSESS.getTranslatedKeyMessage());
            chip(graphics, font, line, centerX, 8);
            return;
        }
        if (!EchoView.thermal()) {
            return;
        }
        // Below the figure and above the pressure pill (guiHeight - 68, lifted while an action-bar message shows):
        // three chip rows end 4 px above the pill.
        int hintY = Math.max(graphics.guiHeight() / 2 + 16, graphics.guiHeight() - 112 - com.mnemolith.client.gui.LensOverlay.messageLift());
        int target = EchoView.targetId();
        Entity entity = target < 0 ? null : minecraft.level.getEntity(target);
        if (entity == null) {
            chip(graphics, font, Component.translatable("mnemolith.hud.thermal"), centerX, hintY);
            return;
        }
        int distance = (int) Math.round(Math.sqrt(entity.distanceToSqr(minecraft.player)));
        Component line = Component.translatable("mnemolith.hud.possess_hint", minecraft.options.keyAttack.getTranslatedKeyMessage());
        Component detail = Component.translatable("mnemolith.hud.target", entity.getDisplayName(), distance);
        Component orders = Component.translatable("mnemolith.hud.command_hint", ThermalClient.CMD_STAY.getTranslatedKeyMessage(),
                ThermalClient.CMD_FOLLOW.getTranslatedKeyMessage(), ThermalClient.CMD_RETURN.getTranslatedKeyMessage());
        chip(graphics, font, line, centerX, hintY);
        chip(graphics, font, orders, centerX, hintY + 14);
        chip(graphics, font, detail, centerX, hintY + 28);
    }

    private static void chip(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y) {
        int width = font.width(text);
        int x = centerX - width / 2;
        graphics.fill(x - 4, y - 2, x + width + 4, y + 10, CHIP);
        GuiArt.label(graphics, font, text, x, y, PINK);
    }
}
