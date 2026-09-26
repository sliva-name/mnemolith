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
 * A low archive beetle: plated abdomen, a stitched ledger on the back, jointed legs, and a pair of mandibles.
 * Feet sit at y=24. Texture is 64×64, painted at double density.
 */
public class LedgerMiteModel extends EntityModel<LedgerMiteModel.State> {
    private final ModelPart abdomen;
    private final ModelPart thorax;
    private final ModelPart head;
    private final ModelPart mandibleLeft;
    private final ModelPart mandibleRight;
    private final ModelPart antennaLeft;
    private final ModelPart antennaRight;
    private final ModelPart[] upper;
    private final ModelPart[] lower;

    public LedgerMiteModel(ModelPart root) {
        super(root);
        this.abdomen = root.getChild("abdomen");
        this.thorax = this.abdomen.getChild("thorax");
        this.head = this.thorax.getChild("head");
        this.mandibleLeft = this.head.getChild("mandible_left");
        this.mandibleRight = this.head.getChild("mandible_right");
        this.antennaLeft = this.head.getChild("antenna_left");
        this.antennaRight = this.head.getChild("antenna_right");
        this.upper = new ModelPart[6];
        this.lower = new ModelPart[6];
        for (int i = 0; i < 6; i++) {
            this.upper[i] = root.getChild("leg" + i);
            this.lower[i] = this.upper[i].getChild("lower");
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition abdomen = root.addOrReplaceChild("abdomen",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -2.0F, -3.0F, 8.0F, 3.0F, 7.0F),
                PartPose.offset(0.0F, 21.0F, 2.0F));
        abdomen.addOrReplaceChild("ledger",
                CubeListBuilder.create().texOffs(16, 24).addBox(-2.5F, -1.0F, -2.0F, 5.0F, 1.0F, 4.0F),
                PartPose.offset(0.0F, -2.0F, 0.5F));
        PartDefinition thorax = abdomen.addOrReplaceChild("thorax",
                CubeListBuilder.create().texOffs(0, 12).addBox(-3.0F, -2.0F, -2.5F, 6.0F, 3.0F, 5.0F),
                PartPose.offset(0.0F, -1.0F, -5.5F));
        PartDefinition head = thorax.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(32, 0).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 3.0F, 4.0F),
                PartPose.offset(0.0F, -0.5F, -2.5F));
        head.addOrReplaceChild("mandible_left",
                CubeListBuilder.create().texOffs(32, 8).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 1.0F, 3.0F),
                PartPose.offset(1.2F, 0.2F, -3.5F));
        head.addOrReplaceChild("mandible_right",
                CubeListBuilder.create().texOffs(44, 8).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 1.0F, 3.0F),
                PartPose.offset(-1.2F, 0.2F, -3.5F));
        head.addOrReplaceChild("antenna_left",
                CubeListBuilder.create().texOffs(48, 0).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(1.2F, -2.0F, -3.0F));
        head.addOrReplaceChild("antenna_right",
                CubeListBuilder.create().texOffs(54, 0).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(-1.2F, -2.0F, -3.0F));
        CubeListBuilder upper = CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F);
        CubeListBuilder lower = CubeListBuilder.create().texOffs(8, 24).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F);
        for (int i = 0; i < 6; i++) {
            float x = i % 2 == 0 ? -4.2F : 4.2F;
            float z = -5.0F + (i / 2) * 4.0F;
            PartDefinition leg = root.addOrReplaceChild("leg" + i, upper, PartPose.offset(x, 21.0F, z));
            leg.addOrReplaceChild("lower", lower, PartPose.offset(0.0F, 3.0F, 0.0F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(State state) {
        super.setupAnim(state);
        float bob = Mth.sin(state.ageInTicks * 0.18F) * 0.15F;
        this.abdomen.y = 21.0F + bob;
        this.abdomen.xRot = Mth.sin(state.ageInTicks * 0.08F) * 0.03F;
        this.head.xRot = state.xRot * ((float) Math.PI / 180.0F) * 0.4F;
        this.head.yRot = state.yRot * ((float) Math.PI / 180.0F) * 0.25F;
        float bite = state.attack * 0.7F;
        this.mandibleLeft.yRot = -0.4F - bite;
        this.mandibleRight.yRot = 0.4F + bite;
        this.mandibleLeft.zRot = 0.15F;
        this.mandibleRight.zRot = -0.15F;
        this.antennaLeft.xRot = -0.6F + Mth.sin(state.ageInTicks * 0.15F) * 0.25F;
        this.antennaRight.xRot = -0.6F + Mth.cos(state.ageInTicks * 0.15F) * 0.25F;
        this.antennaLeft.zRot = 0.35F;
        this.antennaRight.zRot = -0.35F;
        float swing = Mth.cos(state.walkAnimationPos * 0.8F) * 0.9F * state.walkAnimationSpeed;
        for (int i = 0; i < 6; i++) {
            boolean left = i % 2 == 0;
            float phase = (i / 2 == 1) ? -swing : swing;
            this.upper[i].zRot = left ? 0.7F : -0.7F;
            this.upper[i].xRot = left ? phase : -phase;
            this.lower[i].xRot = 0.8F + Math.abs(phase) * 0.4F;
            this.upper[i].y = 21.0F + bob;
        }
    }

    public static class State extends LivingEntityRenderState {
        public float attack;
    }
}
