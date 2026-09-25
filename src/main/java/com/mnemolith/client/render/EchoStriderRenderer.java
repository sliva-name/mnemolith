package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.EchoStriderModel;
import com.mnemolith.entity.mob.EchoStrider;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class EchoStriderRenderer extends MemoryMobRenderer<EchoStrider, EchoStriderModel> {
    public EchoStriderRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new EchoStriderModel(context.bakeLayer(ModEntityRenderers.ECHO_STRIDER)),
                0.45F,
                Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/echo_strider.png"));
    }
}
