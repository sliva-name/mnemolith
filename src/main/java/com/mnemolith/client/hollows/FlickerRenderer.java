package com.mnemolith.client.hollows;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Draws memory flickers: a pale, translucent person-shaped figure that plays a few seconds of what the chunk
 * remembers (walk, work, fall, kneel, flare) and fades. Box geometry like the recall ghosts, no entity, no texture.
 * The server picks spot and scene; this class never touches the world.
 */
public final class FlickerRenderer {
    public static final int DURATION = 80;
    private static final int FADE = 15;
    private static final int CAP = 6;
    private static final int MAX_ALPHA = 0x88;
    private static final List<Flicker> FLICKERS = new ArrayList<>();

    private FlickerRenderer() {}

    public static void accept(HollowFlickerPayload payload) {
        if (!ClientConfig.HOLLOW_FLICKERS.get()) {
            return;
        }
        if (FLICKERS.size() >= CAP) {
            FLICKERS.remove(0);
        }
        int scene = Mth.clamp(payload.scene(), 0, HollowFlickers.SCENES - 1);
        FLICKERS.add(new Flicker(Vec3.atBottomCenterOf(payload.pos()), payload.yaw(), scene, color(payload.tag())));
    }

    public static int active() {
        return FLICKERS.size();
    }

    public static void onClientTick(ClientTickEvent.Pre event) {
        if (FLICKERS.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        double density = ClientConfig.PARTICLE_DENSITY.get();
        for (int i = FLICKERS.size() - 1; i >= 0; i--) {
            Flicker flicker = FLICKERS.get(i);
            flicker.age++;
            if (flicker.age > DURATION) {
                FLICKERS.remove(i);
                continue;
            }
            if (level != null && density > 0.0D && flicker.age % 6 == 0 && level.getRandom().nextDouble() < density) {
                Pose pose = pose(flicker, flicker.age);
                level.addParticle(ModParticles.STRIDER_TRAIL.get(), pose.feet.x, pose.feet.y + 0.05D, pose.feet.z, 0.0D, 0.01D, 0.0D);
                if (flicker.age == 6) {
                    level.addParticle(ModParticles.IMPRINT_SHIMMER.get(), pose.feet.x, pose.feet.y + 1.9D, pose.feet.z, 0.0D, 0.02D, 0.0D);
                }
            }
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FLICKERS.clear();
    }

    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        if (FLICKERS.isEmpty()) {
            return;
        }
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        List<Flicker> shown = List.copyOf(FLICKERS);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads(), (matrix, buffer) -> {
            for (Flicker flicker : shown) {
                float age = flicker.age + partial;
                int alpha = alpha(flicker, age);
                if (alpha <= 4) {
                    continue;
                }
                figure(matrix, buffer, pose(flicker, age), (alpha << 24) | flicker.rgb);
            }
        });
        poseStack.popPose();
    }

    private static int alpha(Flicker flicker, float age) {
        float envelope = Math.min(1.0F, Math.min(age / FADE, (DURATION - age) / FADE));
        float a = MAX_ALPHA * Mth.clamp(envelope, 0.0F, 1.0F);
        if (flicker.scene == HollowFlickers.FLARE) {
            // A flaring memory gutters like a flame.
            a *= 0.55F + 0.45F * Mth.abs(Mth.sin(age * 0.9F) * Mth.cos(age * 0.37F));
        }
        return (int) a;
    }

    /** Where the figure stands and how it holds itself at {@code age} ticks. */
    static Pose pose(Flicker flicker, float age) {
        float t = Mth.clamp(age / DURATION, 0.0F, 1.0F);
        float rad = flicker.yaw * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
        return switch (flicker.scene) {
            case HollowFlickers.WORK -> new Pose(flicker.origin, flicker.yaw, Mth.abs(Mth.sin(age * 0.35F)), 0.0F, 0.0F, 0.0F);
            case HollowFlickers.FALL -> {
                float walk = Math.min(t, 0.4F) / 0.4F;
                float tip = Mth.clamp((t - 0.4F) / 0.18F, 0.0F, 1.0F);
                yield new Pose(flicker.origin.add(forward.scale(walk * 1.2D)), flicker.yaw, 0.6F * tip, 0.0F, tip * 85.0F, 0.0F);
            }
            case HollowFlickers.KNEEL -> {
                float down = Mth.clamp(t / 0.3F, 0.0F, 1.0F);
                yield new Pose(flicker.origin, flicker.yaw, 0.0F, down, down * 12.0F, 0.0F);
            }
            case HollowFlickers.FLARE -> new Pose(flicker.origin, flicker.yaw, 1.0F, 0.0F, 0.0F, 1.0F);
            default -> {
                float step = Mth.sin(age * 0.4F);
                yield new Pose(flicker.origin.add(forward.scale(t * 3.0D)), flicker.yaw, 0.25F + 0.2F * step, 0.0F, 0.0F, 0.25F - 0.2F * step);
            }
        };
    }

    /** Pale base, tinted by the imprint the flicker came from. */
    static int color(int tag) {
        if (tag < 0) {
            return 0xDCE0F6;
        }
        return switch (ImprintTag.byOrdinal(tag)) {
            case FIRE, LIGHTNING -> 0xFFB892;
            case DEATH, BOSS, SCULK -> 0xC6B4F0;
            case FALL, EXPLOSION -> 0xA6E6D8;
            case SILENCE -> 0xC8D4E8;
            case BUILD, REDSTONE, TRADE -> 0xF0E2B8;
            default -> 0xDCE0F6;
        };
    }

    /**
     * The figure in local units (x right, y up, z forward), tipped forward by {@code tilt} degrees about the feet,
     * lowered by {@code crouch}, then turned to {@code yaw}. {@code rightArm}/{@code leftArm} lift from 0 (down) to
     * 1 (straight ahead).
     */
    private static void figure(PoseStack.Pose matrix, VertexConsumer buffer, Pose pose, int color) {
        float drop = pose.crouch * 0.45F;
        float legTop = 0.72F - drop;
        box(matrix, buffer, pose, -0.18F, 0.0F, -0.08F, -0.04F, legTop, 0.08F, 0.0F, color);
        box(matrix, buffer, pose, 0.04F, 0.0F, -0.08F, 0.18F, legTop, 0.08F, 0.0F, color);
        box(matrix, buffer, pose, -0.22F, legTop, -0.12F, 0.22F, legTop + 0.6F, 0.12F, 0.0F, color);
        box(matrix, buffer, pose, -0.18F, legTop + 0.6F, -0.18F, 0.18F, legTop + 1.0F, 0.18F, 0.0F, color);
        arm(matrix, buffer, pose, -0.36F, -0.22F, legTop, pose.rightArm, color);
        arm(matrix, buffer, pose, 0.22F, 0.36F, legTop, pose.leftArm, color);
    }

    private static void arm(PoseStack.Pose matrix, VertexConsumer buffer, Pose pose, float x0, float x1, float legTop, float lift, int color) {
        float shoulder = legTop + 0.55F;
        // The arm swings forward about the shoulder: a 0.5-long box rotated by lift * 90 degrees.
        box(matrix, buffer, pose, x0, shoulder - 0.5F, -0.08F, x1, shoulder, 0.08F, lift * 90.0F, color, shoulder);
    }

    private static void box(PoseStack.Pose matrix, VertexConsumer buffer, Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
            float swing, int color) {
        box(matrix, buffer, pose, x0, y0, z0, x1, y1, z1, swing, color, 0.0F);
    }

    private static void box(PoseStack.Pose matrix, VertexConsumer buffer, Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
            float swing, int color, float pivotY) {
        float[] xs = {x0, x1, x1, x0, x0, x1, x1, x0};
        float[] ys = {y0, y0, y0, y0, y1, y1, y1, y1};
        float[] zs = {z0, z0, z1, z1, z0, z0, z1, z1};
        float[] wx = new float[8];
        float[] wy = new float[8];
        float[] wz = new float[8];
        for (int i = 0; i < 8; i++) {
            float ly = ys[i];
            float lz = zs[i];
            if (swing != 0.0F) {
                // Rotate about the local x axis at the pivot height: positive swing brings the lower end forward (+z).
                float s = Mth.sin(swing * Mth.DEG_TO_RAD);
                float c = Mth.cos(swing * Mth.DEG_TO_RAD);
                float dy = ly - pivotY;
                ly = pivotY + dy * c + lz * s;
                lz = -dy * s + lz * c;
            }
            float[] world = place(pose, xs[i], ly, lz);
            wx[i] = world[0];
            wy[i] = world[1];
            wz[i] = world[2];
        }
        quad(matrix, buffer, wx, wy, wz, 4, 5, 6, 7, color);
        quad(matrix, buffer, wx, wy, wz, 0, 3, 2, 1, shade(color, 0.72F));
        quad(matrix, buffer, wx, wy, wz, 0, 4, 7, 3, shade(color, 0.88F));
        quad(matrix, buffer, wx, wy, wz, 1, 2, 6, 5, shade(color, 0.88F));
        quad(matrix, buffer, wx, wy, wz, 0, 1, 5, 4, shade(color, 0.8F));
        quad(matrix, buffer, wx, wy, wz, 3, 7, 6, 2, shade(color, 0.8F));
    }

    /** Local point to world: tilt forward about the feet, then yaw. Forward is +z, matching an entity's look at yaw 0. */
    private static float[] place(Pose pose, float lx, float ly, float lz) {
        if (pose.tilt != 0.0F) {
            float s = Mth.sin(pose.tilt * Mth.DEG_TO_RAD);
            float c = Mth.cos(pose.tilt * Mth.DEG_TO_RAD);
            float ny = ly * c - lz * s;
            float nz = ly * s + lz * c;
            ly = ny;
            lz = nz;
        }
        float rad = pose.yaw * Mth.DEG_TO_RAD;
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        return new float[] {
                (float) pose.feet.x + lx * cos - lz * sin,
                (float) pose.feet.y + ly,
                (float) pose.feet.z + lx * sin + lz * cos
        };
    }

    private static int shade(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void quad(PoseStack.Pose matrix, VertexConsumer buffer, float[] x, float[] y, float[] z, int a, int b, int c, int d, int color) {
        buffer.addVertex(matrix, x[a], y[a], z[a]).setColor(color);
        buffer.addVertex(matrix, x[b], y[b], z[b]).setColor(color);
        buffer.addVertex(matrix, x[c], y[c], z[c]).setColor(color);
        buffer.addVertex(matrix, x[d], y[d], z[d]).setColor(color);
    }

    record Pose(Vec3 feet, float yaw, float rightArm, float crouch, float tilt, float leftArm) {}

    static final class Flicker {
        final Vec3 origin;
        final float yaw;
        final int scene;
        final int rgb;
        int age;

        Flicker(Vec3 origin, float yaw, int scene, int rgb) {
            this.origin = origin;
            this.yaw = yaw;
            this.scene = scene;
            this.rgb = rgb;
        }
    }
}
