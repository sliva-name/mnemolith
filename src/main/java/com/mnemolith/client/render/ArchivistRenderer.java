package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.ArchivistModel;
import com.mnemolith.entity.mob.Archivist;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class ArchivistRenderer extends MemoryMobRenderer<Archivist, ArchivistModel> {
    public ArchivistRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new ArchivistModel(context.bakeLayer(ModEntityRenderers.ARCHIVIST)),
                0.35F,
                Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/archivist.png"));
    }
}
