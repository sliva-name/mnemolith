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

/** Wide hooded torso and short arms. Reads as a thief, not a strider or a person. */
public class ArchivistModel extends EntityModel<MemoryMobRenderState> {
    private final ModelPart body;
    private final ModelPart hood;
    private final ModelPart armLeft;
    private final ModelPart armRight;

    public ArchivistModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.hood = root.getChild("hood");
        this.armLeft = root.getChild("arm_left");
        this.armRight = root.getChild("arm_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -3.0F, 10.0F, 8.0F, 6.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 5.0F, 8.0F), PartPose.offset(0.0F, 8.0F, 0.0F));
        root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(0, 16).addBox(0.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offset(5.0F, 13.0F, 0.0F));
        root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(8, 16).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offset(-5.0F, 13.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(2.0F, 20.0F, 0.0F));
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(24, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(-2.0F, 20.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(MemoryMobRenderState state) {
        super.setupAnim(state);
        this.body.xRot = 0.0F;
        this.hood.xRot = 0.0F;
        this.hood.xScale = 1.0F;
        float bob = Mth.sin(state.ageInTicks * 0.1F) * 0.3F;
        this.body.y = 12.0F + bob;
        this.hood.y = 8.0F + bob;
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * state.walkAnimationSpeed;
        this.armLeft.xRot = swing;
        this.armRight.xRot = -swing;
        if (state.action == MobActions.FLEE) {
            this.body.xRot = 0.7F;
            this.hood.xRot = 0.4F;
        } else if (state.action == MobActions.ATTACK) {
            this.armRight.xRot = -1.4F;
            this.armLeft.xRot = -1.4F;
        } else if (state.action == MobActions.TELEGRAPH) {
            this.hood.y += Mth.sin(state.ageInTicks * 0.5F);
        }
        if (state.deathTime > 0.0F) {
            this.body.y += state.deathTime * 0.3F;
            this.hood.xScale = Math.max(0.2F, 1.0F - state.deathTime * 0.03F);
        }
    }
}
