package com.mnemolith.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * A tall masked pillar on jointed legs, with a spinning ring, a halo, hanging strips, and a tablet in one hand.
 * The mask is a hollow plate, the same idea as the archivist's hood. Texture is 64×64.
 */
public class KinWitnessModel extends EntityModel<KinWitnessModel.State> {
    private final ModelPart body;
    private final ModelPart mask;
    private final ModelPart halo;
    private final ModelPart ring;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart stripLeft;
    private final ModelPart stripRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;
    private final ModelPart kneeLeft;
    private final ModelPart kneeRight;

    public KinWitnessModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.mask = this.body.getChild("mask");
        this.halo = this.mask.getChild("halo");
        this.ring = this.body.getChild("ring");
        this.armLeft = this.body.getChild("arm_left");
        this.armRight = this.body.getChild("arm_right");
        this.stripLeft = this.body.getChild("strip_left");
        this.stripRight = this.body.getChild("strip_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
        this.kneeLeft = this.legLeft.getChild("knee");
        this.kneeRight = this.legRight.getChild("knee");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.5F, -14.0F, -2.0F, 5.0F, 14.0F, 4.0F),
                PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition mask = body.addOrReplaceChild("mask",
                CubeListBuilder.create().texOffs(20, 16).addBox(-3.5F, -6.0F, -2.0F, 7.0F, 7.0F, 3.0F),
                PartPose.offset(0.0F, -14.0F, -1.0F));
        mask.addOrReplaceChild("halo",
                CubeListBuilder.create().texOffs(0, 48).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 1.0F, 8.0F),
                PartPose.offset(0.0F, -5.0F, 1.0F));
        body.addOrReplaceChild("ring",
                CubeListBuilder.create().texOffs(0, 36).addBox(-5.0F, 0.0F, -5.0F, 10.0F, 1.0F, 10.0F),
                PartPose.offset(0.0F, -4.0F, 0.0F));
        PartDefinition armLeft = body.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(42, 0).addBox(0.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offset(2.5F, -12.0F, 0.0F));
        armLeft.addOrReplaceChild("tablet",
                CubeListBuilder.create().texOffs(42, 16).addBox(-1.0F, 0.0F, -3.0F, 6.0F, 8.0F, 1.0F),
                PartPose.offset(-1.0F, 9.0F, -1.0F));
        body.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(42, 0).mirror().addBox(-2.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offset(-2.5F, -12.0F, 0.0F));
        body.addOrReplaceChild("strip_left",
                CubeListBuilder.create().texOffs(42, 28).addBox(0.0F, 0.0F, 0.0F, 1.0F, 12.0F, 3.0F),
                PartPose.offset(1.5F, -6.0F, 1.5F));
        body.addOrReplaceChild("strip_right",
                CubeListBuilder.create().texOffs(52, 28).addBox(-1.0F, 0.0F, 0.0F, 1.0F, 12.0F, 3.0F),
                PartPose.offset(-1.5F, -6.0F, 1.5F));
        PartDefinition legLeft = root.addOrReplaceChild("leg_left",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F),
                PartPose.offset(1.4F, 12.0F, 0.0F));
        legLeft.addOrReplaceChild("knee",
                CubeListBuilder.create().texOffs(16, 0).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 10.0F, 0.0F));
        PartDefinition legRight = root.addOrReplaceChild("leg_right",
                CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F),
                PartPose.offset(-1.4F, 12.0F, 0.0F));
        legRight.addOrReplaceChild("knee",
                CubeListBuilder.create().texOffs(16, 0).mirror().addBox(-1.5F, 0.0F, -2.0F, 3.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 10.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(State state) {
        super.setupAnim(state);
        float sway = Mth.sin(state.ageInTicks * 0.07F);
        this.body.xRot = sway * 0.04F;
        this.body.zRot = sway * 0.02F;
        this.mask.xRot = state.xRot * ((float) Math.PI / 180.0F) * 0.35F;
        this.mask.yRot = state.yRot * ((float) Math.PI / 180.0F) * 0.4F;
        this.halo.yRot = state.ageInTicks * 0.04F;
        this.ring.yRot = -state.ageInTicks * 0.05F;
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.7F * state.walkAnimationSpeed;
        this.legLeft.xRot = swing;
        this.legRight.xRot = -swing;
        this.kneeLeft.xRot = 0.15F + Math.abs(swing) * 0.2F;
        this.kneeRight.xRot = 0.15F + Math.abs(swing) * 0.2F;
        this.armLeft.xRot = -0.4F - state.attack * 0.8F;
        this.armRight.xRot = -0.2F + swing * 0.3F;
        this.armLeft.zRot = -0.12F;
        this.armRight.zRot = 0.18F;
        this.stripLeft.xRot = 0.1F + sway * 0.12F;
        this.stripRight.xRot = 0.1F - sway * 0.12F;
    }

    public static class State extends LivingEntityRenderState {
        public float attack;
    }
}
