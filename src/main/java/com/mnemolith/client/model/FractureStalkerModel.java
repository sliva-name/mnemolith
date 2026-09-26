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
 * A low horned hunter: long body, a jaw that opens on the bite, a bladed tail, and spines along the back.
 * Texture is 64×64. The elite uses the same mesh with a cracked texture and a larger scale.
 */
public class FractureStalkerModel extends EntityModel<FractureStalkerModel.State> {
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tail;
    private final ModelPart[] spines;
    private final ModelPart leg0;
    private final ModelPart leg1;
    private final ModelPart leg2;
    private final ModelPart leg3;

    public FractureStalkerModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.tail = this.body.getChild("tail");
        this.spines = new ModelPart[] {
                this.body.getChild("spine0"), this.body.getChild("spine1"), this.body.getChild("spine2")
        };
        this.leg0 = root.getChild("leg0");
        this.leg1 = root.getChild("leg1");
        this.leg2 = root.getChild("leg2");
        this.leg3 = root.getChild("leg3");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -5.0F, -7.0F, 8.0F, 5.0F, 14.0F),
                PartPose.offset(0.0F, 16.0F, 1.0F));
        PartDefinition tail = body.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 8.0F),
                PartPose.offset(0.0F, -3.0F, 7.0F));
        tail.addOrReplaceChild("tail_tip",
                CubeListBuilder.create().texOffs(22, 20).addBox(-1.5F, -1.0F, 0.0F, 3.0F, 1.0F, 4.0F),
                PartPose.offset(0.0F, -0.5F, 8.0F));
        for (int i = 0; i < 3; i++) {
            body.addOrReplaceChild("spine" + i,
                    CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -4.0F, -1.0F, 1.0F, 4.0F, 2.0F),
                    PartPose.offset(0.0F, -5.0F, -4.0F + i * 4.0F));
        }
        body.addOrReplaceChild("shoulder_left",
                CubeListBuilder.create().texOffs(0, 46).addBox(0.0F, -1.0F, -1.5F, 5.0F, 2.0F, 3.0F),
                PartPose.offset(2.0F, -4.0F, -4.0F));
        body.addOrReplaceChild("shoulder_right",
                CubeListBuilder.create().texOffs(0, 46).mirror().addBox(-5.0F, -1.0F, -1.5F, 5.0F, 2.0F, 3.0F),
                PartPose.offset(-2.0F, -4.0F, -4.0F));
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, -4.0F, -6.0F, 6.0F, 5.0F, 6.0F),
                PartPose.offset(0.0F, 13.5F, -6.0F));
        head.addOrReplaceChild("snout",
                CubeListBuilder.create().texOffs(26, 32).addBox(-2.0F, -1.0F, -5.0F, 4.0F, 3.0F, 5.0F),
                PartPose.offset(0.0F, -1.0F, -6.0F));
        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(26, 42).addBox(-2.0F, 0.0F, -4.0F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 0.2F, -6.0F));
        head.addOrReplaceChild("horn_left",
                CubeListBuilder.create().texOffs(46, 0).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offsetAndRotation(2.0F, -4.0F, -2.0F, -0.4F, 0.0F, 0.35F));
        head.addOrReplaceChild("horn_right",
                CubeListBuilder.create().texOffs(46, 8).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offsetAndRotation(-2.0F, -4.0F, -2.0F, -0.4F, 0.0F, -0.35F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(46, 20).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F);
        CubeListBuilder paw = CubeListBuilder.create().texOffs(46, 32).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 2.0F, 4.0F);
        String[] names = {"leg0", "leg1", "leg2", "leg3"};
        float[] xs = {-3.2F, 3.2F, -3.2F, 3.2F};
        float[] zs = {5.0F, 5.0F, -4.0F, -4.0F};
        for (int i = 0; i < 4; i++) {
            PartDefinition part = root.addOrReplaceChild(names[i], leg, PartPose.offset(xs[i], 15.0F, zs[i]));
            part.addOrReplaceChild("paw", paw, PartPose.offset(0.0F, 6.0F, 0.5F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(State state) {
        super.setupAnim(state);
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.1F * state.walkAnimationSpeed;
        this.leg0.xRot = swing;
        this.leg3.xRot = swing;
        this.leg1.xRot = -swing;
        this.leg2.xRot = -swing;
        this.body.xRot = Mth.sin(state.ageInTicks * 0.08F) * 0.03F + state.walkAnimationSpeed * 0.15F;
        this.head.xRot = state.xRot * ((float) Math.PI / 180.0F) * 0.6F;
        this.head.yRot = state.yRot * ((float) Math.PI / 180.0F) * 0.35F;
        this.jaw.xRot = 0.15F + state.attack * 0.7F;
        this.tail.yRot = Mth.sin(state.ageInTicks * 0.12F) * 0.35F;
        this.tail.xRot = -0.25F;
        for (int i = 0; i < this.spines.length; i++) {
            this.spines[i].xRot = -0.3F + Mth.sin(state.ageInTicks * 0.1F + i) * 0.05F;
        }
    }

    public static class State extends LivingEntityRenderState {
        public boolean elite;
        public float attack;
    }
}
