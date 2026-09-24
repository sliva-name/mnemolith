package com.mnemolith.client.model;

import com.mnemolith.client.render.MemoryMobRenderState;
import com.mnemolith.entity.MobActions;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Upright copy of a person: head, chest, arms, legs. The telegraph lifts both arms. */
public class ReplicantModel extends EntityModel<MemoryMobRenderState> {
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public ReplicantModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.armLeft = root.getChild("arm_left");
        this.armRight = root.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 0).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 10.0F, 4.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(0, 16).addBox(0.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), PartPose.offset(3.0F, 1.0F, 0.0F));
        root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(8, 16).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), PartPose.offset(-3.0F, 1.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offset(1.5F, 12.0F, 0.0F));
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(24, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offset(-1.5F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(MemoryMobRenderState state) {
        super.setupAnim(state);
        this.body.xRot = 0.0F;
        this.body.y = 0.0F;
        this.head.y = 0.0F;
        this.head.xScale = 1.0F;
        float idle = Mth.sin(state.ageInTicks * 0.08F) * 0.05F;
        this.head.xRot = idle;
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.2F * state.walkAnimationSpeed;
        this.legLeft.xRot = swing;
        this.legRight.xRot = -swing;
        this.armLeft.xRot = -swing * 0.6F;
        this.armRight.xRot = swing * 0.6F;
        if (state.action == MobActions.TELEGRAPH) {
            this.armLeft.xRot = -2.4F;
            this.armRight.xRot = -2.4F;
            this.head.xRot = -0.3F + Mth.sin(state.ageInTicks * 0.4F) * 0.1F;
        } else if (state.action == MobActions.ATTACK) {
            this.armRight.xRot = -1.6F;
        } else if (state.action == MobActions.FLEE) {
            this.body.xRot = 0.5F;
            this.head.xRot = 0.4F;
        }
        if (state.deathTime > 0.0F) {
            float sink = state.deathTime * 0.15F;
            this.body.y = sink;
            this.head.y = sink;
            this.head.xScale = Math.max(0.2F, 1.0F - state.deathTime * 0.03F);
        }
    }
}
