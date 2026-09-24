package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.ArchivistModel;
import com.mnemolith.client.model.EchoStriderModel;
import com.mnemolith.client.model.ReplicantModel;
import com.mnemolith.entity.ModEntities;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Layer and renderer registration. Referenced only from {@link com.mnemolith.MnemolithClient}. */
public final class ModEntityRenderers {
    public static final ModelLayerLocation ECHO_STRIDER = layer("echo_strider");
    public static final ModelLayerLocation ARCHIVIST = layer("archivist");
    public static final ModelLayerLocation MOMENT_REPLICANT = layer("moment_replicant");

    private ModEntityRenderers() {}

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ECHO_STRIDER, EchoStriderModel::createBodyLayer);
        event.registerLayerDefinition(ARCHIVIST, ArchivistModel::createBodyLayer);
        event.registerLayerDefinition(MOMENT_REPLICANT, ReplicantModel::createBodyLayer);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ECHO_STRIDER.get(), EchoStriderRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCHIVIST.get(), ArchivistRenderer::new);
        event.registerEntityRenderer(ModEntities.MOMENT_REPLICANT.get(), ReplicantRenderer::new);
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, name), "main");
    }
}
