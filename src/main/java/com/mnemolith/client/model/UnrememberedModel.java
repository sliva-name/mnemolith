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
 * The Unremembered: not a recoloured faded. Same worn body plan, but crowned with recollite splinters,
 * pauldrons on the shoulders, a heart shard twice the faded's splinter, and a heavy robe skirt over the
 * veil, so at boss scale (1.8x) it reads as a crowned figure in robes, not a tall person. Texture is the
 * Unremembered's own 64x64; the new parts reuse its cloth and splinter regions.
 */
public class UnrememberedModel extends EntityModel<UnrememberedModel.State> {
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart veil;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public UnrememberedModel(ModelPart root) {
        super(root, net.minecraft.client.renderer.rendertype.RenderTypes::entityTranslucent);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.veil = this.body.getChild("veil");
        this.armLeft = this.body.getChild("arm_left");
        this.armRight = this.body.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F)
                        .texOffs(56, 0).addBox(-1.0F, -9.0F, -2.6F, 2.0F, 3.0F, 1.0F),
                PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        // a crown of five recollite splinters, tilted like broken glass
        head.addOrReplaceChild("crown_front",
                CubeListBuilder.create().texOffs(56, 0).addBox(-0.6F, -3.4F, -0.6F, 1.2F, 3.4F, 1.2F),
                PartPose.offsetAndRotation(0.0F, -7.6F, -3.4F, -0.35F, 0.0F, 0.0F));
        head.addOrReplaceChild("crown_back",
                CubeListBuilder.create().texOffs(56, 0).addBox(-0.6F, -3.0F, -0.6F, 1.2F, 3.0F, 1.2F),
                PartPose.offsetAndRotation(0.0F, -7.8F, 3.4F, 0.3F, 0.0F, 0.0F));
        head.addOrReplaceChild("crown_left",
                CubeListBuilder.create().texOffs(56, 0).addBox(-0.6F, -3.2F, -0.6F, 1.2F, 3.2F, 1.2F),
                PartPose.offsetAndRotation(3.6F, -7.7F, 0.0F, 0.0F, 0.0F, -0.4F));
        head.addOrReplaceChild("crown_right",
                CubeListBuilder.create().texOffs(56, 0).addBox(-0.6F, -3.2F, -0.6F, 1.2F, 3.2F, 1.2F),
                PartPose.offsetAndRotation(-3.6F, -7.7F, 0.0F, 0.0F, 0.0F, 0.4F));
        head.addOrReplaceChild("crown_top",
                CubeListBuilder.create().texOffs(56, 0).addBox(-0.7F, -3.8F, -0.7F, 1.4F, 3.8F, 1.4F),
                PartPose.offsetAndRotation(0.8F, -7.9F, 0.6F, 0.12F, 0.0F, -0.15F));
        body.addOrReplaceChild("veil",
                CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, 0.0F, -2.5F, 9.0F, 9.0F, 5.0F),
                PartPose.offset(0.0F, -1.0F, 0.0F));
        // robe skirt: wider and heavier than the veil, fraying over the legs
        body.addOrReplaceChild("skirt",
                CubeListBuilder.create().texOffs(0, 32).addBox(-5.2F, 6.0F, -3.2F, 10.4F, 8.0F, 6.4F),
                PartPose.offset(0.0F, -1.0F, 0.0F));
        body.addOrReplaceChild("pauldron_left",
                CubeListBuilder.create().texOffs(16, 16).addBox(0.0F, -1.5F, -2.6F, 3.4F, 3.4F, 5.2F),
                PartPose.offset(3.8F, -11.5F, 0.0F));
        body.addOrReplaceChild("pauldron_right",
                CubeListBuilder.create().texOffs(16, 16).addBox(-3.4F, -1.5F, -2.6F, 3.4F, 3.4F, 5.2F),
                PartPose.offset(-3.8F, -11.5F, 0.0F));
        body.addOrReplaceChild("heart_shard",
                CubeListBuilder.create().texOffs(56, 0).addBox(-1.1F, -8.6F, -3.1F, 2.2F, 4.2F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        body.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(40, 16).addBox(0.0F, -1.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                PartPose.offset(4.0F, -11.0F, 0.0F));
        body.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-3.0F, -1.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                PartPose.offset(-4.0F, -11.0F, 0.0F));
        root.addOrReplaceChild("leg_left",
                CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                PartPose.offset(2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("leg_right",
                CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                PartPose.offset(-2.0F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(State state) {
        super.setupAnim(state);
        float t = state.ageInTicks;
        float sway = Mth.sin(t * 0.06F);
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.6F * state.walkAnimationSpeed;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.body.y = 12.0F;
        this.body.xRot = sway * 0.03F;
        this.body.zRot = 0.0F;
        this.veil.xRot = 0.08F + Mth.sin(t * 0.11F) * 0.06F + Math.abs(swing) * 0.2F;
        this.legLeft.xRot = swing;
        this.legRight.xRot = -swing;
        this.legLeft.y = 12.0F;
        this.legRight.y = 12.0F;
        this.armLeft.xRot = -swing * 0.8F - state.attack * 1.4F;
        this.armRight.xRot = swing * 0.8F - state.attack * 1.4F;
        this.armLeft.zRot = -0.06F - sway * 0.04F;
        this.armRight.zRot = 0.06F + sway * 0.04F;
    }

    public static class State extends LivingEntityRenderState {
        public float attack;
        public boolean revealed;
    }
}
