package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.ReplicantModel;
import com.mnemolith.entity.mob.MomentReplicant;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class ReplicantRenderer extends MemoryMobRenderer<MomentReplicant, ReplicantModel> {
    public ReplicantRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new ReplicantModel(context.bakeLayer(ModEntityRenderers.MOMENT_REPLICANT)),
                0.4F,
                Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/moment_replicant.png"));
    }
}
