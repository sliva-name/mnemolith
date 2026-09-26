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
    public static final ModelLayerLocation RESIDUE = layer("residue");

    private ModEntityRenderers() {}

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ECHO_STRIDER, EchoStriderModel::createBodyLayer);
        event.registerLayerDefinition(ARCHIVIST, ArchivistModel::createBodyLayer);
        event.registerLayerDefinition(MOMENT_REPLICANT, ReplicantModel::createBodyLayer);
        event.registerLayerDefinition(RESIDUE, com.mnemolith.client.model.ResidueModel::createBodyLayer);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ECHO_STRIDER.get(), EchoStriderRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCHIVIST.get(), ArchivistRenderer::new);
        event.registerEntityRenderer(ModEntities.MOMENT_REPLICANT.get(), ReplicantRenderer::new);
        event.registerEntityRenderer(ModEntities.RESIDUE.get(), ResidueRenderer::new);
        event.registerEntityRenderer(ModEntities.SCAR.get(), ScarRenderer::new);
        registerAvatar(event, ModEntities.ECHO.get(), true);
        registerAvatar(event, ModEntities.ECHO_SHELL.get(), false);
    }

    /** Client echoes and shells are always the client subclasses, so the avatar renderer can be bound to their types. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerAvatar(EntityRenderersEvent.RegisterRenderers event, net.minecraft.world.entity.EntityType<?> type, boolean echo) {
        event.registerEntityRenderer((net.minecraft.world.entity.EntityType) type, context -> new com.mnemolith.client.echo.EchoRenderer(context, echo));
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, name), "main");
    }
}
