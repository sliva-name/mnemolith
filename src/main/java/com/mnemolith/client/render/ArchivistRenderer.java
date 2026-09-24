package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.ArchivistModel;
import com.mnemolith.entity.mob.Archivist;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class ArchivistRenderer extends MobRenderer<Archivist, MemoryMobRenderState, ArchivistModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/archivist.png");

    public ArchivistRenderer(EntityRendererProvider.Context context) {
        super(context, new ArchivistModel(context.bakeLayer(ModEntityRenderers.ARCHIVIST)), 0.35F);
    }

    @Override
    public MemoryMobRenderState createRenderState() {
        return new MemoryMobRenderState();
    }

    @Override
    public void extractRenderState(Archivist entity, MemoryMobRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.action = entity.action();
    }

    @Override
    public Identifier getTextureLocation(MemoryMobRenderState state) {
        return TEXTURE;
    }
}
