package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.ReplicantModel;
import com.mnemolith.entity.mob.MomentReplicant;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class ReplicantRenderer extends MobRenderer<MomentReplicant, MemoryMobRenderState, ReplicantModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/moment_replicant.png");

    public ReplicantRenderer(EntityRendererProvider.Context context) {
        super(context, new ReplicantModel(context.bakeLayer(ModEntityRenderers.MOMENT_REPLICANT)), 0.4F);
    }

    @Override
    public MemoryMobRenderState createRenderState() {
        return new MemoryMobRenderState();
    }

    @Override
    public void extractRenderState(MomentReplicant entity, MemoryMobRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.action = entity.action();
    }

    @Override
    public Identifier getTextureLocation(MemoryMobRenderState state) {
        return TEXTURE;
    }
}
