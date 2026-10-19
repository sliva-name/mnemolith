package com.mnemolith.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A hushling is not a small pale faded: the faded frame plus a deep hood over the head, a dark mouth plate
 * and a long tattered skirt that hides the legs. Parts reuse the hushling texture's cloth regions.
 */
public class HushlingModel extends FadedModel {
    public HushlingModel(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = FadedModel.createMesh();
        PartDefinition body = mesh.getRoot().getChild("body");
        body.addOrReplaceChild("skirt",
                CubeListBuilder.create().texOffs(0, 32).addBox(-5.6F, -0.5F, -3.4F, 11.2F, 8.5F, 6.8F),
                PartPose.offset(0.0F, -1.0F, 0.0F));
        PartDefinition head = body.getChild("head");
        head.addOrReplaceChild("hood",
                CubeListBuilder.create().texOffs(0, 32).addBox(-4.8F, -10.0F, -4.8F, 9.6F, 5.4F, 9.6F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("hood_drape_left",
                CubeListBuilder.create().texOffs(0, 32).addBox(-0.8F, -4.6F, -4.4F, 1.6F, 6.4F, 8.8F),
                PartPose.offset(4.6F, 0.0F, 0.0F));
        head.addOrReplaceChild("hood_drape_right",
                CubeListBuilder.create().texOffs(0, 32).addBox(-0.8F, -4.6F, -4.4F, 1.6F, 6.4F, 8.8F),
                PartPose.offset(-4.6F, 0.0F, 0.0F));
        head.addOrReplaceChild("hood_back",
                CubeListBuilder.create().texOffs(0, 32).addBox(-4.4F, -4.6F, -0.8F, 8.8F, 6.4F, 1.6F),
                PartPose.offset(0.0F, 0.0F, 4.6F));
        head.addOrReplaceChild("hood_peak",
                CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -11.4F, -1.0F, 2.0F, 2.4F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -8.6F, 0.6F, 0.3F, 0.0F, 0.0F));
        head.addOrReplaceChild("mouth_plate",
                CubeListBuilder.create().texOffs(16, 16).addBox(-1.8F, -3.4F, -4.3F, 3.6F, 2.2F, 0.8F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
