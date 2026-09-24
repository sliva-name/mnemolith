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
 * Slim split figure with a second shell offset behind it.
 * Telegraph lifts the real arms; the shell lags. The strike drops one arm
 * while the shell is still raised. Texture is 64×64.
 */
public class ReplicantModel extends EntityModel<MemoryMobRenderState> {
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;
    private final ModelPart echoHead;
    private final ModelPart echoBody;
    private final ModelPart echoArmLeft;
    private final ModelPart echoArmRight;

    public ReplicantModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.armLeft = root.getChild("arm_left");
        this.armRight = root.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
        this.echoHead = root.getChild("echo_head");
        this.echoBody = root.getChild("echo_body");
        this.echoArmLeft = root.getChild("echo_arm_left");
        this.echoArmRight = root.getChild("echo_arm_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create().texOffs(0, 14).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 12.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild(
                "arm_left",
                CubeListBuilder.create().texOffs(16, 14).addBox(0.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offset(2.0F, 1.0F, 0.0F));
        root.addOrReplaceChild(
                "arm_right",
                CubeListBuilder.create().texOffs(16, 14).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offset(-2.0F, 1.0F, 0.0F));
        root.addOrReplaceChild(
                "leg_left",
                CubeListBuilder.create().texOffs(28, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F),
                PartPose.offset(1.0F, 12.0F, 0.0F));
        root.addOrReplaceChild(
                "leg_right",
                CubeListBuilder.create().texOffs(28, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F),
                PartPose.offset(-1.0F, 12.0F, 0.0F));
        root.addOrReplaceChild(
                "echo_head",
                CubeListBuilder.create().texOffs(32, 16).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 0.0F, 2.0F));
        root.addOrReplaceChild(
                "echo_body",
                CubeListBuilder.create().texOffs(32, 30).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 12.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 2.0F));
        root.addOrReplaceChild(
                "echo_arm_left",
                CubeListBuilder.create().texOffs(48, 0).addBox(0.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offset(2.0F, 1.0F, 2.0F));
        root.addOrReplaceChild(
                "echo_arm_right",
                CubeListBuilder.create().texOffs(48, 0).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offset(-2.0F, 1.0F, 2.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(MemoryMobRenderState state) {
        super.setupAnim(state);
        this.body.xRot = 0.0F;
        this.body.y = 0.0F;
        this.head.y = 0.0F;
        this.head.x = 0.0F;
        this.head.xRot = Mth.sin(state.ageInTicks * 0.08F) * 0.05F;
        this.head.xScale = 1.0F;
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.2F * state.walkAnimationSpeed;
        this.legLeft.xRot = swing;
        this.legRight.xRot = -swing;
        this.armLeft.xRot = -swing * 0.6F;
        this.armRight.xRot = swing * 0.6F;
        this.echoHead.x = 0.0F;
        this.echoBody.x = 0.0F;
        if (state.action == MobActions.TELEGRAPH) {
            this.armLeft.xRot = -2.5F;
            this.armRight.xRot = -2.5F;
            this.head.xRot = -0.35F + Mth.sin(state.ageInTicks * 0.4F) * 0.08F;
            float drift = Mth.sin(state.ageInTicks * 0.5F) * 0.6F;
            this.echoHead.x = drift;
            this.echoBody.x = drift;
        } else if (state.action == MobActions.ATTACK) {
            this.armRight.xRot = -0.35F;
            this.armLeft.xRot = -0.4F;
            this.head.xRot = 0.15F;
        } else if (state.action == MobActions.FLEE) {
            this.body.xRot = 0.5F;
            this.head.xRot = 0.35F;
        }
        this.echoHead.xRot = this.head.xRot * 0.45F;
        this.echoHead.y = this.head.y;
        this.echoBody.xRot = this.body.xRot * 0.35F;
        this.echoBody.y = this.body.y;
        this.echoArmLeft.xRot = this.armLeft.xRot * 0.45F;
        this.echoArmRight.xRot = this.armRight.xRot * 0.45F;
        if (state.action == MobActions.TELEGRAPH) {
            this.echoArmLeft.xRot = -1.15F;
            this.echoArmRight.xRot = -1.15F;
        } else if (state.action == MobActions.ATTACK) {
            this.echoArmRight.xRot = -1.6F;
            this.echoArmLeft.xRot = -1.2F;
        }
        if (state.deathTime > 0.0F) {
            float sink = state.deathTime * 0.15F;
            this.body.y = sink;
            this.head.y = sink;
            this.echoBody.y = sink;
            this.echoHead.y = sink;
            this.head.xScale = Math.max(0.2F, 1.0F - state.deathTime * 0.03F);
        }
    }
}
