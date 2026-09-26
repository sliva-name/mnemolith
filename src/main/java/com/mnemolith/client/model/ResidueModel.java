package com.mnemolith.client.model;

import com.mnemolith.client.render.ResidueRenderState;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A fragment of a figure: a head, a torso, one arm, and a tapering wisp where the legs would be, with three shards
 * of the memory orbiting the chest. It bobs and the wisp sways; a pinned residue (read with the lens) nearly stops.
 * Texture is 64×64.
 */
public class ResidueModel extends EntityModel<ResidueRenderState> {
    private final ModelPart figure;
    private final ModelPart head;
    private final ModelPart arm;
    private final ModelPart wispTop;
    private final ModelPart wispMid;
    private final ModelPart wispTip;
    private final ModelPart[] shards = new ModelPart[3];

    public ResidueModel(ModelPart root) {
        super(root);
        this.figure = root.getChild("figure");
        this.head = this.figure.getChild("head");
        this.arm = this.figure.getChild("arm");
        this.wispTop = this.figure.getChild("wisp_top");
        this.wispMid = this.wispTop.getChild("wisp_mid");
        this.wispTip = this.wispMid.getChild("wisp_tip");
        for (int i = 0; i < 3; i++) {
            this.shards[i] = root.getChild("shard_" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition figure = root.addOrReplaceChild("figure", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        figure.addOrReplaceChild(
                "head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        figure.addOrReplaceChild(
                "torso",
                CubeListBuilder.create().texOffs(0, 14).addBox(-3.0F, 0.0F, -1.5F, 6.0F, 8.0F, 3.0F),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        figure.addOrReplaceChild(
                "arm",
                CubeListBuilder.create().texOffs(24, 0).addBox(0.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                PartPose.offset(3.0F, 4.5F, 0.0F));
        PartDefinition top = figure.addOrReplaceChild(
                "wisp_top",
                CubeListBuilder.create().texOffs(0, 28).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition mid = top.addOrReplaceChild(
                "wisp_mid",
                CubeListBuilder.create().texOffs(0, 36).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        mid.addOrReplaceChild(
                "wisp_tip",
                CubeListBuilder.create().texOffs(0, 44).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 4.0F, 1.0F),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        for (int i = 0; i < 3; i++) {
            root.addOrReplaceChild(
                    "shard_" + i,
                    CubeListBuilder.create().texOffs(40, 0).addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 1.0F),
                    PartPose.offset(0.0F, 8.0F, 0.0F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ResidueRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks * (state.pinned ? 0.15F : 1.0F);
        float bob = Mth.sin(time * 0.09F) * 1.2F;
        this.figure.y = bob;
        this.head.xRot = Mth.sin(time * 0.05F) * 0.12F + 0.15F;
        this.head.zRot = Mth.sin(time * 0.037F) * 0.1F;
        this.arm.xRot = -0.25F + Mth.sin(time * 0.07F) * 0.2F;
        this.arm.zRot = -0.12F;
        this.wispTop.xRot = Mth.sin(time * 0.11F) * 0.18F;
        this.wispMid.xRot = Mth.sin(time * 0.11F - 0.8F) * 0.25F;
        this.wispTip.xRot = Mth.sin(time * 0.11F - 1.6F) * 0.35F;
        this.wispTip.zRot = Mth.sin(time * 0.08F) * 0.2F;
        float radius = state.pinned ? 4.5F : 6.5F;
        for (int i = 0; i < 3; i++) {
            float angle = time * 0.06F + i * (Mth.TWO_PI / 3.0F);
            ModelPart shard = this.shards[i];
            shard.x = Mth.cos(angle) * radius;
            shard.z = Mth.sin(angle) * radius;
            shard.y = 8.0F + bob + Mth.sin(time * 0.1F + i * 2.0F) * 1.5F;
            shard.yRot = -angle;
            shard.zRot = 0.4F + i * 0.3F;
            shard.visible = i < Math.max(1, (state.strength + 1) / 2);
        }
    }
}
