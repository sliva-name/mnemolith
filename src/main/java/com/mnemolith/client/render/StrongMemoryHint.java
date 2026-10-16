package com.mnemolith.client.render;

import java.util.HashSet;
import java.util.Set;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.common.MemoryPalette;
import com.mnemolith.network.StrongMemoryPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Memory feel, stage 3: the strong-memory hint. The server says when this player steps into a saturated-or-louder
 * chunk; while the player stays in it the screen edge breathes a faint indigo tint (below the pressure vignette), and
 * the first time per chunk this session the action bar says "This place remembers." Toggle: {@code strongMemoryHint}.
 */
public final class StrongMemoryHint {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "strong_memory_hint");
    private static final Set<Long> SEEN = new HashSet<>();
    private static long chunk = Long.MIN_VALUE;
    private static float amount;
    private static final float MAX_ALPHA = 70.0F;

    private StrongMemoryHint() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(FractureFeel.LAYER, LAYER, StrongMemoryHint::render);
    }

    public static void accept(StrongMemoryPayload payload) {
        long key = key(payload.chunkX(), payload.chunkZ());
        chunk = key;
        if (!ClientConfig.STRONG_MEMORY_HINT.get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && SEEN.size() < 4096 && SEEN.add(key)) {
            player.sendOverlayMessage(Component.translatable("mnemolith.memory.strong_hint"));
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean inside = player != null && ClientConfig.STRONG_MEMORY_HINT.get()
                && key(player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4) == chunk;
        amount += ((inside ? 1.0F : 0.0F) - amount) * 0.08F;
        if (amount < 0.003F && !inside) {
            amount = 0.0F;
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SEEN.clear();
        chunk = Long.MIN_VALUE;
        amount = 0.0F;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (amount < 0.01F || !ClientConfig.STRONG_MEMORY_HINT.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null || minecraft.gui.hud.isHidden()) {
            return;
        }
        float time = minecraft.player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        // a slow breath, about 5 s per cycle
        float breath = 0.55F + 0.45F * (0.5F + 0.5F * Mth.sin(time * 0.063F));
        int alpha = Mth.clamp(Math.round(amount * breath * MAX_ALPHA), 0, 255);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, FractureFeel.VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height, (alpha << 24) | MemoryPalette.INDIGO);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
