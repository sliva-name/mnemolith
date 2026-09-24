package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.EchoStriderModel;
import com.mnemolith.entity.mob.EchoStrider;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class EchoStriderRenderer extends MobRenderer<EchoStrider, MemoryMobRenderState, EchoStriderModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/echo_strider.png");

    public EchoStriderRenderer(EntityRendererProvider.Context context) {
        super(context, new EchoStriderModel(context.bakeLayer(ModEntityRenderers.ECHO_STRIDER)), 0.45F);
    }

    @Override
    public MemoryMobRenderState createRenderState() {
        return new MemoryMobRenderState();
    }

    @Override
    public void extractRenderState(EchoStrider entity, MemoryMobRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.action = entity.action();
    }

    @Override
    public Identifier getTextureLocation(MemoryMobRenderState state) {
        return TEXTURE;
    }
}
