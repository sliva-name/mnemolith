package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.ReplicantModel;
import com.mnemolith.entity.mob.MomentReplicant;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;

public class ReplicantRenderer extends MemoryMobRenderer<MomentReplicant, ReplicantModel> {
    private final ItemModelResolver itemModels;

    public ReplicantRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new ReplicantModel(context.bakeLayer(ModEntityRenderers.MOMENT_REPLICANT)),
                0.4F,
                Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/moment_replicant.png"));
        this.itemModels = context.getItemModelResolver();
        this.addLayer(new ItemInHandLayer<>(this));
    }

    @Override
    public void extractRenderState(MomentReplicant entity, MemoryMobRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModels, partialTick);
    }
}
