package com.mnemolith.client.echo;

import org.lwjgl.glfw.GLFW;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.echo.EchoView;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.network.EchoCommandPayload;
import com.mnemolith.network.EchoPossessPayload;
import com.mnemolith.network.EchoUnpossessPayload;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The lens "thermal" view. While the lens is raised (use held, not sneaking) the world is re-tinted dark pink by a
 * post chain, every echo gets a filled light-pink silhouette that shows through walls ({@link EchoRenderer}), and the
 * closest own echo inside a small aim-assist cone becomes the target (near-white pulsing fill plus an outline).
 * Attack on a target asks the server to possess it.
 * <p>
 * The post chain runs at {@link RenderLevelStageEvent.AfterWeather}: after the world and the weather, but before the
 * always-on-top pass (silhouettes and status labels), the held item and the entity outline composite, so all of those
 * keep their own colors instead of being tinted.
 */
public final class ThermalClient {
    public static final Identifier EFFECT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "thermal");
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "echo"));
    public static final KeyMapping UNPOSSESS = new KeyMapping("key.mnemolith.unpossess", GLFW.GLFW_KEY_V, CATEGORY);
    /** Stage 3 lens orders: only act while the lens is raised and aimed at an own echo. */
    public static final KeyMapping CMD_STAY = new KeyMapping("key.mnemolith.cmd_stay", GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping CMD_FOLLOW = new KeyMapping("key.mnemolith.cmd_follow", GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping CMD_RETURN = new KeyMapping("key.mnemolith.cmd_return", GLFW.GLFW_KEY_B, CATEGORY);

    private static boolean chainMissingLogged;

    private ThermalClient() {}

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(UNPOSSESS);
        event.register(CMD_STAY);
        event.register(CMD_FOLLOW);
        event.register(CMD_RETURN);
    }

    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            EchoView.setThermal(false);
            return;
        }
        boolean active = ChronicleLensItem.isFocusing(player) && !EchoView.possessed() && minecraft.gui.screen() == null;
        EchoView.setThermal(active);
        if (active) {
            EchoEntity target = pick(minecraft, player);
            EchoView.setTarget(target == null ? -1 : target.getId());
            if (target != null) {
                boolean clicked = false;
                while (minecraft.options.keyAttack.consumeClick()) {
                    clicked = true;
                }
                if (clicked) {
                    ClientPacketDistributor.sendToServer(new EchoPossessPayload(target.getId()));
                }
            }
        }
        EchoJob.Order order = EchoJob.Order.NONE;
        order = consume(CMD_STAY, EchoJob.Order.STAY, order);
        order = consume(CMD_FOLLOW, EchoJob.Order.FOLLOW, order);
        order = consume(CMD_RETURN, EchoJob.Order.RETURN, order);
        if (active && order != EchoJob.Order.NONE && EchoView.targetId() >= 0) {
            ClientPacketDistributor.sendToServer(new EchoCommandPayload(EchoView.targetId(), order));
        }
        while (UNPOSSESS.consumeClick()) {
            if (EchoView.possessed()) {
                ClientPacketDistributor.sendToServer(EchoUnpossessPayload.INSTANCE);
            }
        }
    }

    /** Drains a key's clicks (so presses without the lens never queue up) and keeps the last pressed order. */
    private static EchoJob.Order consume(KeyMapping key, EchoJob.Order order, EchoJob.Order current) {
        EchoJob.Order result = current;
        while (key.consumeClick()) {
            result = order;
        }
        return result;
    }

    /** Own echo with the smallest angle to the crosshair, inside the aim-assist cone (widened for close echoes). */
    public static EchoEntity pick(Minecraft minecraft, LocalPlayer player) {
        double range = CommonConfig.ECHO_POSSESS_RANGE.get();
        double assist = ClientConfig.ECHO_AIM_ASSIST.get();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        EchoEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (EchoEntity echo : minecraft.level.getEntitiesOfClass(EchoEntity.class, player.getBoundingBox().inflate(range))) {
            if (!echo.isAlive() || !echo.isOwnedBy(player)) {
                continue;
            }
            Vec3 toEcho = echo.getBoundingBox().getCenter().subtract(eye);
            double distance = toEcho.length();
            if (distance > range || distance < 0.01D) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, toEcho.scale(1.0D / distance).dot(look)))));
            double cone = assist + Math.toDegrees(Math.atan(0.9D / distance));
            if (angle <= cone && angle < bestAngle) {
                bestAngle = angle;
                best = echo;
            }
        }
        return best;
    }

    public static void onAfterWeather(RenderLevelStageEvent.AfterWeather event) {
        if (!EchoView.thermal() || !ClientConfig.THERMAL_VIEW.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        PostChain chain = minecraft.getShaderManager().getPostChain(EFFECT, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            if (!chainMissingLogged) {
                chainMissingLogged = true;
                Mnemolith.LOGGER.warn("Mnemolith thermal post chain {} failed to load; the lens view falls back to outlines only", EFFECT);
            }
            return;
        }
        chain.process(minecraft.gameRenderer.mainRenderTarget(), GraphicsResourceAllocator.UNPOOLED);
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EchoView.setThermal(false);
        EchoView.setPossessed(false);
    }
}
