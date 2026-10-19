package com.mnemolith.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A cinderkin is not a charcoal faded: the faded frame plus a glowing ember core in the chest, two basalt
 * horn shards on the head and cinder pauldrons. Parts reuse the cinderkin texture's cloth/splinter regions.
 */
public class CinderkinModel extends FadedModel {
    public CinderkinModel(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = FadedModel.createMesh();
        PartDefinition body = mesh.getRoot().getChild("body");
        body.addOrReplaceChild("ember_core",
                CubeListBuilder.create().texOffs(56, 0).addBox(-1.2F, -9.4F, -2.9F, 2.4F, 3.6F, 1.2F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        body.addOrReplaceChild("pauldron_left",
                CubeListBuilder.create().texOffs(16, 16).addBox(-0.4F, -1.6F, -2.6F, 3.6F, 3.2F, 5.2F),
                PartPose.offset(4.0F, -11.0F, 0.0F));
        body.addOrReplaceChild("pauldron_right",
                CubeListBuilder.create().texOffs(16, 16).addBox(-3.2F, -1.6F, -2.6F, 3.6F, 3.2F, 5.2F),
                PartPose.offset(-4.0F, -11.0F, 0.0F));
        PartDefinition head = body.getChild("head");
        head.addOrReplaceChild("horn_left",
                CubeListBuilder.create().texOffs(56, 0).addBox(-0.7F, -3.6F, -0.7F, 1.4F, 3.6F, 1.4F),
                PartPose.offsetAndRotation(3.2F, -7.4F, 0.0F, 0.0F, 0.0F, -0.35F));
        head.addOrReplaceChild("horn_right",
                CubeListBuilder.create().texOffs(56, 0).addBox(-0.7F, -3.6F, -0.7F, 1.4F, 3.6F, 1.4F),
                PartPose.offsetAndRotation(-3.2F, -7.4F, 0.0F, 0.0F, 0.0F, 0.35F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
