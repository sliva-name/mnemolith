package com.mnemolith.client.render;

import org.joml.Vector3f;

import com.mnemolith.entity.PleadingChair;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Bob, then lean the whole chair toward the nearest player. The mesh faces the open side down local -Z. */
public class PleadingChairRenderer extends EntityRenderer<PleadingChair, PleadingChairRenderer.State> {
    private final Vector3f point = new Vector3f();
    private final Vector3f normal = new Vector3f();

    public PleadingChairRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PleadingChair entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yRot = entity.getYRot(partialTick);
        state.plead = 0.0F;
        state.forward = 0.0F;
        state.side = 0.0F;
        Player nearest = null;
        double best = 64.0D;
        for (Player player : entity.level().players()) {
            double distance = player.distanceToSqr(entity);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest == null) {
            return;
        }
        double dx = nearest.getX() - entity.getX();
        double dz = nearest.getZ() - entity.getZ();
        float yaw = state.yRot * Mth.DEG_TO_RAD;
        float forward = (float) (dx * -Mth.sin(yaw) + dz * Mth.cos(yaw));
        float side = (float) (dx * Mth.cos(yaw) + dz * Mth.sin(yaw));
        float horizontal = Mth.sqrt(forward * forward + side * side);
        state.plead = Mth.clamp(1.0F - Mth.sqrt((float) best) / 8.0F, 0.0F, 1.0F);
        if (horizontal > 1.0E-3F) {
            state.forward = forward / horizontal;
            state.side = side / horizontal;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        ChairMesh mesh = ChairMesh.get();
        if (mesh.vertices == 0) {
            super.submit(state, poseStack, collector, camera);
            return;
        }
        float bob = Mth.sin(state.ageInTicks * 0.16F) * 0.028F;
        float nod = Mth.sin(state.ageInTicks * 0.33F) * 0.05F * state.plead;
        float lean = 0.22F * state.plead;
        poseStack.pushPose();
        poseStack.translate(0.0F, bob, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        poseStack.mulPose(Axis.XP.rotation(-lean * state.forward - nod));
        poseStack.mulPose(Axis.ZP.rotation(-lean * state.side));
        int light = state.lightCoords;
        int white = ARGB.white(1.0F);
        // Entity pipelines draw quads: indices (0, 1, 2) and (2, 3, 0). Repeating the third
        // corner makes the second triangle degenerate, so each source triangle stays a triangle.
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(ChairMesh.TEXTURE), (pose, buffer) -> {
            for (int triangle = 0; triangle < mesh.vertices; triangle += 3) {
                for (int corner = 0; corner < 4; corner++) {
                    int i = triangle + (corner == 3 ? 2 : corner);
                    int p = i * 3;
                    this.point.set(mesh.position[p], mesh.position[p + 1], mesh.position[p + 2]);
                    pose.pose().transformPosition(this.point);
                    this.normal.set(mesh.normal[p], mesh.normal[p + 1], mesh.normal[p + 2]);
                    pose.transformNormal(this.normal, this.normal);
                    buffer.addVertex(
                            this.point.x, this.point.y, this.point.z,
                            white,
                            mesh.uv[i * 2], mesh.uv[i * 2 + 1],
                            OverlayTexture.NO_OVERLAY,
                            light,
                            this.normal.x, this.normal.y, this.normal.z);
                }
            }
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    public static class State extends EntityRenderState {
        public float yRot;
        public float plead;
        public float forward;
        public float side;
    }
}
