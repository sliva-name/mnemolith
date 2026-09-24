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

/** Long low body, thin legs, a head on the front. Reads as a path, not a person. */
public class EchoStriderModel extends EntityModel<MemoryMobRenderState> {
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leg0;
    private final ModelPart leg1;
    private final ModelPart leg2;
    private final ModelPart leg3;

    public EchoStriderModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.leg0 = root.getChild("leg0");
        this.leg1 = root.getChild("leg1");
        this.leg2 = root.getChild("leg2");
        this.leg3 = root.getChild("leg3");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, 0.0F, -2.0F, 16.0F, 4.0F, 4.0F), PartPose.offset(0.0F, 16.0F, 0.0F));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32, 0).addBox(-2.0F, 0.0F, -4.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(0.0F, 15.0F, -6.0F));
        root.addOrReplaceChild("leg0", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offset(-5.0F, 18.0F, -3.0F));
        root.addOrReplaceChild("leg1", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offset(5.0F, 18.0F, -3.0F));
        root.addOrReplaceChild("leg2", CubeListBuilder.create().texOffs(8, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offset(-5.0F, 18.0F, 3.0F));
        root.addOrReplaceChild("leg3", CubeListBuilder.create().texOffs(8, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offset(5.0F, 18.0F, 3.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(MemoryMobRenderState state) {
        super.setupAnim(state);
        this.body.xScale = 1.0F;
        this.body.yScale = 1.0F;
        this.body.zScale = 1.0F;
        this.body.xRot = 0.0F;
        this.body.zRot = 0.0F;
        float shimmer = Mth.sin(state.ageInTicks * 0.12F) * 0.4F;
        this.body.y = 16.0F + shimmer;
        this.head.y = 15.0F + shimmer;
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.8F * state.walkAnimationSpeed;
        this.leg0.xRot = swing;
        this.leg3.xRot = swing;
        this.leg1.xRot = -swing;
        this.leg2.xRot = -swing;
        this.head.xRot = 0.0F;
        if (state.action == MobActions.ATTACK) {
            this.head.xRot = -1.2F;
            this.body.zRot = 0.2F;
        } else if (state.action == MobActions.TELEGRAPH) {
            this.body.y += Mth.sin(state.ageInTicks) * 0.6F;
            this.head.xRot = -0.4F;
        } else if (state.action == MobActions.PHASE) {
            this.body.xScale = 0.7F;
            this.body.zScale = 0.7F;
        } else if (state.action == MobActions.FLEE) {
            this.body.xRot = 0.4F;
        }
        if (state.deathTime > 0.0F) {
            float sink = state.deathTime * 0.4F;
            this.body.y += sink;
            this.head.y += sink;
            this.body.xScale = Math.max(0.2F, 1.0F - state.deathTime * 0.04F);
        }
    }
}
