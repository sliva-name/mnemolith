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
 * Hood, cloak, and satchel. The flee pose leans forward with both arms reaching:
 * that is the snatch the player sees after a slip is taken. Texture is 64×64.
 */
public class ArchivistModel extends EntityModel<MemoryMobRenderState> {
    private final ModelPart body;
    private final ModelPart hood;
    private final ModelPart cloak;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public ArchivistModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.hood = this.body.getChild("hood");
        this.cloak = this.body.getChild("cloak");
        this.armLeft = this.body.getChild("arm_left");
        this.armRight = this.body.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild(
                "body",
                CubeListBuilder.create().texOffs(0, 16).addBox(-5.0F, 0.0F, -3.0F, 10.0F, 10.0F, 6.0F),
                PartPose.offset(0.0F, 6.0F, 0.0F));
        body.addOrReplaceChild(
                "hood",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -6.0F, -4.0F, 8.0F, 6.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 1.0F));
        body.addOrReplaceChild(
                "cloak",
                CubeListBuilder.create().texOffs(0, 34).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 14.0F, 2.0F),
                PartPose.offset(0.0F, -1.0F, 2.0F));
        body.addOrReplaceChild(
                "satchel",
                CubeListBuilder.create().texOffs(32, 0).addBox(0.0F, 0.0F, 0.0F, 3.0F, 5.0F, 3.0F),
                PartPose.offset(4.0F, 4.0F, 2.0F));
        body.addOrReplaceChild(
                "arm_left",
                CubeListBuilder.create().texOffs(32, 16).addBox(0.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                PartPose.offset(5.0F, 1.0F, 0.0F));
        body.addOrReplaceChild(
                "arm_right",
                CubeListBuilder.create().texOffs(32, 16).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                PartPose.offset(-5.0F, 1.0F, 0.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(32, 28).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F);
        root.addOrReplaceChild("leg_left", leg, PartPose.offset(2.0F, 16.0F, 0.0F));
        root.addOrReplaceChild("leg_right", leg, PartPose.offset(-2.0F, 16.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(MemoryMobRenderState state) {
        super.setupAnim(state);
        float bob = Mth.sin(state.ageInTicks * 0.1F) * 0.25F;
        this.body.y = 6.0F + bob;
        this.body.xRot = 0.0F;
        this.body.zRot = 0.0F;
        this.hood.xRot = 0.0F;
        this.hood.xScale = 1.0F;
        this.cloak.xRot = 0.0F;
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.1F * state.walkAnimationSpeed;
        this.armLeft.xRot = swing;
        this.armRight.xRot = -swing;
        this.legLeft.xRot = -swing;
        this.legRight.xRot = swing;
        this.legLeft.y = 16.0F;
        this.legRight.y = 16.0F;
        if (state.action == MobActions.FLEE) {
            this.body.xRot = 0.9F;
            this.hood.xRot = 0.35F;
            this.armLeft.xRot = -0.65F;
            this.armRight.xRot = -0.65F;
        } else if (state.action == MobActions.ATTACK) {
            this.armLeft.xRot = -1.65F;
            this.armRight.xRot = -1.65F;
            this.hood.xRot = 0.2F;
        } else if (state.action == MobActions.TELEGRAPH) {
            this.hood.xRot = Mth.sin(state.ageInTicks * 0.5F) * 0.2F;
            this.armLeft.xRot = -0.8F;
            this.armRight.xRot = -0.4F;
        }
        if (state.deathTime > 0.0F) {
            this.body.y += state.deathTime * 0.3F;
            this.hood.xScale = Math.max(0.2F, 1.0F - state.deathTime * 0.03F);
            this.legLeft.y = 16.0F + state.deathTime * 0.3F;
            this.legRight.y = this.legLeft.y;
        }
    }
}
