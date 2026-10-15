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

import com.mnemolith.worldgen.hollows.HollowFlickers;

/**
 * A worn-out person: head, a narrow body that frays into a veil instead of a waist, thin arms, short legs under the
 * veil, and a recollite splinter where the heart would be. Replays the five flicker scenes. Texture is 64×64.
 */
public class FadedModel extends EntityModel<FadedModel.State> {
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart veil;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public FadedModel(ModelPart root) {
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
        body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        body.addOrReplaceChild("veil",
                CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, 0.0F, -2.5F, 9.0F, 9.0F, 5.0F),
                PartPose.offset(0.0F, -1.0F, 0.0F));
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
        switch (state.scene) {
            case HollowFlickers.WORK -> {
                float hit = Mth.sin(t * 0.35F);
                this.armRight.xRot = -1.6F + hit * 0.7F;
                this.armLeft.xRot = -0.9F + hit * 0.3F;
                this.head.xRot = 0.5F;
            }
            case HollowFlickers.KNEEL -> {
                this.body.y = 17.0F;
                this.body.xRot = 0.35F;
                this.head.xRot = 0.6F;
                this.legLeft.xRot = -1.5F;
                this.legRight.xRot = -0.2F;
                this.legLeft.y = 17.0F;
                this.legRight.y = 17.0F;
                this.armLeft.xRot = -0.5F;
                this.armRight.xRot = -0.5F;
            }
            case HollowFlickers.FALL -> {
                float k = Mth.sin(t * 0.2F);
                this.body.xRot = -0.3F + k * 0.15F;
                this.armLeft.zRot = -2.4F;
                this.armRight.zRot = 2.4F;
                this.legLeft.xRot = 0.5F;
                this.legRight.xRot = -0.4F;
            }
            case HollowFlickers.FLARE -> {
                this.armLeft.xRot = -2.8F;
                this.armRight.xRot = -2.8F;
                this.armLeft.zRot = -0.3F + sway * 0.2F;
                this.armRight.zRot = 0.3F - sway * 0.2F;
                this.head.xRot = -0.6F;
            }
            default -> {
            }
        }
    }

    public static class State extends LivingEntityRenderState {
        public float attack;
        public int scene = -1;
        public boolean revealed;
    }
}
