package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.common.MemoryPalette;
import com.mnemolith.echo.EchoView;
import com.mnemolith.network.ChunkPressure;
import com.mnemolith.pressure.PressureBand;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Client fracture feel. Intensity comes only from the latest pressure snapshot, which the server
 * already built for the lens. This class does not read chunk memory and does not decide a band.
 * <p>
 * The chunk under the player is full strength. One chunk away (Chebyshev) is a little over half,
 * two chunks away is a hint, and anything farther is only what the lens-sized snapshot still
 * includes. Overloaded darkens and shakes. Fracture does that harder and can run one fullscreen
 * fringe pass, the same post-chain path as the lens thermal view.
 */
public final class FractureFeel {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "pressure_vignette");
    public static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/misc/pressure_vignette.png");
    public static final Identifier FRINGE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "fracture");

    /** Smoothed 0..1 weight. Origin fracture is 1, origin overload is {@link #OVERLOADED}. */
    private static float amount;
    /** Smoothed 0..1, 1 when the loudest nearby sample is the fracture band. */
    private static float fractureMix;
    private static boolean chainMissingLogged;

    private static final float OVERLOADED = 0.48F;
    private static final float APPROACH = 0.35F;
    /** Fringe stays off for overload and for a fracture that is only next door. */
    private static final float FRINGE_AMOUNT = 0.72F;

    private FractureFeel() {}

    /** True when any fracture-feel toggle still needs a server snapshot. */
    public static boolean wantsSnapshot() {
        return ClientConfig.PRESSURE_VIGNETTE.get()
                || ClientConfig.STORM_SCREEN_SHAKE.get()
                || ClientConfig.FRACTURE_FRINGE.get();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.HOTBAR, LAYER, FractureFeel::renderVignette);
    }

    public static void reset() {
        amount = 0.0F;
        fractureMix = 0.0F;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            reset();
            return;
        }
        Sample sample = sample(player);
        amount = approach(amount, sample.amount());
        fractureMix = approach(fractureMix, sample.fracture());
    }

    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientConfig.STORM_SCREEN_SHAKE.get() || amount < 0.02F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null || minecraft.isPaused()) {
            return;
        }
        float time = minecraft.player.tickCount + (float) event.getPartialTick();
        float roll = (Mth.sin(time * 0.47F) * 0.65F + Mth.sin(time * 1.15F) * 0.35F) * 2.2F * amount;
        float pitch = Mth.sin(time * 0.83F + 1.3F) * 0.7F * amount;
        event.setRoll(event.getRoll() + roll);
        event.setPitch(event.getPitch() + pitch);
    }

    public static void onAfterWeather(RenderLevelStageEvent.AfterWeather event) {
        if (!ClientConfig.FRACTURE_FRINGE.get() || amount < FRINGE_AMOUNT || fractureMix < 0.65F) {
            return;
        }
        if (EchoView.thermal()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }
        PostChain chain = minecraft.getShaderManager().getPostChain(FRINGE, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            if (!chainMissingLogged) {
                chainMissingLogged = true;
                Mnemolith.LOGGER.warn("Mnemolith fracture fringe {} failed to load; vignette and camera shake still run", FRINGE);
            }
            return;
        }
        chain.process(minecraft.gameRenderer.mainRenderTarget(), GraphicsResourceAllocator.UNPOOLED);
    }

    private static void renderVignette(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!ClientConfig.PRESSURE_VIGNETTE.get() || amount < 0.02F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null || minecraft.gui.hud.isHidden()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float pulse = 1.0F;
        if (fractureMix > 0.5F) {
            float time = minecraft.player.tickCount + partial;
            pulse = 0.86F + 0.14F * (0.5F + 0.5F * Mth.sin(time * 0.17F));
        }
        int alpha = Mth.clamp(Math.round(amount * pulse * 220.0F), 0, 220);
        int color = (alpha << 24) | mixRgb(MemoryPalette.INK, 0x7A2A32, fractureMix);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height, color);
    }

    /** Loudest overloaded or fracture sample in the current snapshot, with distance falloff. */
    private static Sample sample(LocalPlayer player) {
        int originX = player.blockPosition().getX() >> 4;
        int originZ = player.blockPosition().getZ() >> 4;
        float best = 0.0F;
        float fracture = 0.0F;
        for (ChunkPressure chunk : PressureClient.snapshot()) {
            int chebyshev = Math.max(Math.abs(chunk.chunkX() - originX), Math.abs(chunk.chunkZ() - originZ));
            if (chebyshev > 3) {
                continue;
            }
            PressureBand band = PressureBand.byOrdinal(chunk.band());
            float local = switch (band) {
                case FRACTURE -> 1.0F;
                case OVERLOADED -> OVERLOADED;
                default -> 0.0F;
            };
            if (local <= 0.0F) {
                continue;
            }
            float falloff = switch (chebyshev) {
                case 0 -> 1.0F;
                case 1 -> 0.55F;
                case 2 -> 0.28F;
                default -> 0.12F;
            };
            local *= falloff;
            if (local > best) {
                best = local;
                fracture = band == PressureBand.FRACTURE ? 1.0F : 0.0F;
            }
        }
        return new Sample(best, fracture);
    }

    private static float approach(float current, float target) {
        float next = current + (target - current) * APPROACH;
        if (Math.abs(target - next) < 0.004F) {
            return target;
        }
        return next;
    }

    private static int mixRgb(int from, int to, float t) {
        float mix = Mth.clamp(t, 0.0F, 1.0F);
        int red = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * mix);
        int green = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * mix);
        int blue = Math.round((from & 255) + ((to & 255) - (from & 255)) * mix);
        return (red << 16) | (green << 8) | blue;
    }

    private record Sample(float amount, float fracture) {}
}
