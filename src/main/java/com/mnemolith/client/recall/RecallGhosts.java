package com.mnemolith.client.recall;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.network.RecallGhostPayload;
import com.mnemolith.particle.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Owner-only ghost: a short translucent figure along a saved trail, plus footstep motes.
 * The server already decided to send the packet. Nothing here writes world state.
 */
public final class RecallGhosts {
    private static final int DURATION = 40;
    private static final int CAP = 3;
    private static final int PINK = 0xFFA8D6;
    private static final int WASHED = 0xC5D4F0;
    private static final List<Flash> FLASHES = new ArrayList<>();

    private RecallGhosts() {}

    public static void accept(RecallGhostPayload payload) {
        if (FLASHES.size() >= CAP) {
            FLASHES.remove(0);
        }
        FLASHES.add(new Flash(payload.pos(), payload.yaw(), payload.kind(), payload.distorted(), List.copyOf(payload.trail()), 0));
    }

    public static void onClientTick(ClientTickEvent.Pre event) {
        if (FLASHES.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        double density = ClientConfig.PARTICLE_DENSITY.get();
        for (int i = FLASHES.size() - 1; i >= 0; i--) {
            Flash flash = FLASHES.get(i);
            flash.age++;
            if (flash.age > DURATION) {
                FLASHES.remove(i);
                continue;
            }
            if (level != null && density > 0.0D) {
                Vec3 feet = at(flash, flash.age / (float) DURATION);
                level.addParticle(ModParticles.STRIDER_TRAIL.get(), feet.x, feet.y + 0.05D, feet.z, 0.0D, 0.01D, 0.0D);
            }
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FLASHES.clear();
    }

    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        if (FLASHES.isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        List<Flash> shown = List.copyOf(FLASHES);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads(), (pose, buffer) -> {
            for (Flash flash : shown) {
                float life = 1.0F - flash.age / (float) DURATION;
                int alpha = Mth.clamp((int) (0x90 * life), 0, 255);
                int rgb = flash.distorted ? WASHED : PINK;
                int color = (alpha << 24) | rgb;
                Vec3 feet = at(flash, flash.age / (float) DURATION);
                figure(pose, buffer, feet, flash.yaw, flash.kind == 0, color);
            }
        });
        poseStack.popPose();
    }

    private static Vec3 at(Flash flash, float t) {
        List<BlockPos> path = new ArrayList<>(flash.trail.size() + 1);
        path.addAll(flash.trail);
        path.add(flash.pos);
        if (path.size() == 1) {
            return center(path.get(0));
        }
        float scaled = Mth.clamp(t / 0.7F, 0.0F, 1.0F) * (path.size() - 1);
        int index = Math.min(path.size() - 2, (int) scaled);
        float part = scaled - index;
        Vec3 from = center(path.get(index));
        Vec3 to = center(path.get(index + 1));
        return from.lerp(to, part);
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    /** A small person-shaped volume facing {@code yaw}. An attack lifts one arm. */
    private static void figure(PoseStack.Pose pose, VertexConsumer buffer, Vec3 feet, float yaw, boolean attack, int color) {
        box(pose, buffer, feet, yaw, -0.18F, 0.0F, -0.08F, -0.04F, 0.72F, 0.08F, color);
        box(pose, buffer, feet, yaw, 0.04F, 0.0F, -0.08F, 0.18F, 0.72F, 0.08F, color);
        box(pose, buffer, feet, yaw, -0.22F, 0.72F, -0.12F, 0.22F, 1.32F, 0.12F, color);
        box(pose, buffer, feet, yaw, -0.18F, 1.32F, -0.18F, 0.18F, 1.72F, 0.18F, color);
        if (attack) {
            box(pose, buffer, feet, yaw, -0.42F, 0.95F, -0.42F, -0.24F, 1.08F, 0.05F, color);
        } else {
            box(pose, buffer, feet, yaw, -0.36F, 0.75F, -0.08F, -0.22F, 1.25F, 0.08F, color);
        }
        box(pose, buffer, feet, yaw, 0.22F, 0.75F, -0.08F, 0.36F, 1.25F, 0.08F, color);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, Vec3 feet, float yaw,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float[] xs = {x0, x1, x1, x0, x0, x1, x1, x0};
        float[] ys = {y0, y0, y0, y0, y1, y1, y1, y1};
        float[] zs = {z0, z0, z1, z1, z0, z0, z1, z1};
        float[] wx = new float[8];
        float[] wy = new float[8];
        float[] wz = new float[8];
        for (int i = 0; i < 8; i++) {
            float[] world = rotate(feet, yaw, xs[i], ys[i], zs[i]);
            wx[i] = world[0];
            wy[i] = world[1];
            wz[i] = world[2];
        }
        // 0-3 bottom, 4-7 top, matching the corner order above.
        quad(pose, buffer, wx, wy, wz, 4, 5, 6, 7, color);
        quad(pose, buffer, wx, wy, wz, 0, 3, 2, 1, shade(color, 0.72F));
        quad(pose, buffer, wx, wy, wz, 0, 4, 7, 3, shade(color, 0.88F));
        quad(pose, buffer, wx, wy, wz, 1, 2, 6, 5, shade(color, 0.88F));
        quad(pose, buffer, wx, wy, wz, 0, 1, 5, 4, shade(color, 0.8F));
        quad(pose, buffer, wx, wy, wz, 3, 7, 6, 2, shade(color, 0.8F));
    }

    private static float[] rotate(Vec3 feet, float yaw, float lx, float ly, float lz) {
        float rad = yaw * ((float) Math.PI / 180.0F);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        return new float[] {
                (float) feet.x + lx * cos - lz * sin,
                (float) feet.y + ly,
                (float) feet.z + lx * sin + lz * cos
        };
    }

    private static int shade(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float[] x, float[] y, float[] z, int a, int b, int c, int d, int color) {
        buffer.addVertex(pose, x[a], y[a], z[a]).setColor(color);
        buffer.addVertex(pose, x[b], y[b], z[b]).setColor(color);
        buffer.addVertex(pose, x[c], y[c], z[c]).setColor(color);
        buffer.addVertex(pose, x[d], y[d], z[d]).setColor(color);
    }

    private static final class Flash {
        private final BlockPos pos;
        private final float yaw;
        private final int kind;
        private final boolean distorted;
        private final List<BlockPos> trail;
        private int age;

        private Flash(BlockPos pos, float yaw, int kind, boolean distorted, List<BlockPos> trail, int age) {
            this.pos = pos;
            this.yaw = yaw;
            this.kind = kind;
            this.distorted = distorted;
            this.trail = trail;
            this.age = age;
        }
    }
}
