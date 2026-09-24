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

/**
 * Long low body, blank front plate, side fins, and thin legs.
 * Charge leans the body; the walk cycle swings opposite legs. Texture is 64×64.
 */
public class EchoStriderModel extends EntityModel<MemoryMobRenderState> {
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart finLeft;
    private final ModelPart finRight;
    private final ModelPart leg0;
    private final ModelPart leg1;
    private final ModelPart leg2;
    private final ModelPart leg3;

    public EchoStriderModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.finLeft = this.body.getChild("fin_left");
        this.finRight = this.body.getChild("fin_right");
        this.leg0 = root.getChild("leg0");
        this.leg1 = root.getChild("leg1");
        this.leg2 = root.getChild("leg2");
        this.leg3 = root.getChild("leg3");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild(
                "body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-10.0F, -2.0F, -3.0F, 20.0F, 4.0F, 6.0F),
                PartPose.offset(0.0F, 15.0F, 0.0F));
        body.addOrReplaceChild(
                "head",
                CubeListBuilder.create().texOffs(0, 12).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 5.0F, 3.0F),
                PartPose.offset(0.0F, -1.0F, -3.0F));
        body.addOrReplaceChild(
                "fin_left",
                CubeListBuilder.create().texOffs(0, 22).addBox(0.0F, -2.0F, -4.0F, 1.0F, 3.0F, 8.0F),
                PartPose.offset(10.0F, 0.0F, -1.0F));
        body.addOrReplaceChild(
                "fin_right",
                CubeListBuilder.create().texOffs(20, 22).addBox(-1.0F, -2.0F, -4.0F, 1.0F, 3.0F, 8.0F),
                PartPose.offset(-10.0F, 0.0F, -1.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 34).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F);
        root.addOrReplaceChild("leg0", leg, PartPose.offset(-6.0F, 16.0F, -3.0F));
        root.addOrReplaceChild("leg1", leg, PartPose.offset(6.0F, 16.0F, -3.0F));
        root.addOrReplaceChild("leg2", leg, PartPose.offset(-6.0F, 16.0F, 3.0F));
        root.addOrReplaceChild("leg3", leg, PartPose.offset(6.0F, 16.0F, 3.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(MemoryMobRenderState state) {
        super.setupAnim(state);
        float shimmer = Mth.sin(state.ageInTicks * 0.12F) * 0.35F;
        this.body.y = 15.0F + shimmer;
        this.body.xScale = 1.0F;
        this.body.yScale = 1.0F;
        this.body.zScale = 1.0F;
        this.body.xRot = 0.0F;
        this.body.zRot = 0.0F;
        this.head.y = -1.0F;
        this.head.z = -3.0F;
        this.head.xRot = 0.0F;
        this.finLeft.zRot = -0.35F;
        this.finRight.zRot = 0.35F;
        this.finLeft.xRot = Mth.sin(state.ageInTicks * 0.12F) * 0.08F;
        this.finRight.xRot = -this.finLeft.xRot;
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.9F * state.walkAnimationSpeed;
        this.leg0.xRot = swing;
        this.leg3.xRot = swing;
        this.leg1.xRot = -swing;
        this.leg2.xRot = -swing;
        if (state.action == MobActions.TELEGRAPH) {
            this.body.xRot = 0.7F;
            this.head.xRot = -0.35F;
            this.finLeft.zRot = -0.95F;
            this.finRight.zRot = 0.95F;
        } else if (state.action == MobActions.ATTACK) {
            this.body.xRot = 0.25F;
            this.head.xRot = -0.9F;
            this.head.z = -5.0F;
            this.body.zRot = 0.12F;
        } else if (state.action == MobActions.PHASE) {
            this.body.xScale = 0.7F;
            this.body.zScale = 0.7F;
            this.finLeft.zRot = -0.1F;
            this.finRight.zRot = 0.1F;
        } else if (state.action == MobActions.FLEE) {
            this.body.xRot = 0.45F;
        }
        if (state.deathTime > 0.0F) {
            float sink = state.deathTime * 0.4F;
            this.body.y += sink;
            this.body.xScale = Math.max(0.2F, this.body.xScale - state.deathTime * 0.04F);
            this.leg0.y = 16.0F + sink;
            this.leg1.y = 16.0F + sink;
            this.leg2.y = 16.0F + sink;
            this.leg3.y = 16.0F + sink;
        } else {
            this.leg0.y = 16.0F;
            this.leg1.y = 16.0F;
            this.leg2.y = 16.0F;
            this.leg3.y = 16.0F;
        }
    }
}
