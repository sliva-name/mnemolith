package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.FadedModel;
import com.mnemolith.client.model.HushlingModel;
import com.mnemolith.entity.mob.Hushling;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** The faded's frame at 0.8 scale in pale bone-white cloth with a dark mouth; always drawn revealed. */
public class HushlingRenderer extends MobRenderer<Hushling, FadedModel.State, HushlingModel> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/hushling.png");

    public HushlingRenderer(EntityRendererProvider.Context context) {
        super(context, new HushlingModel(context.bakeLayer(ModEntityRenderers.HUSHLING)), 0.4F);
    }

    @Override
    public FadedModel.State createRenderState() {
        return new FadedModel.State();
    }

    @Override
    public void extractRenderState(Hushling entity, FadedModel.State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attack = entity.getAttackAnim(partialTick);
        state.revealed = true;
    }

    @Override
    public Identifier getTextureLocation(FadedModel.State state) {
        return TEXTURE;
    }

    @Override
    protected void scale(FadedModel.State state, com.mojang.blaze3d.vertex.PoseStack poseStack) {
        poseStack.scale(0.8F, 0.8F, 0.8F);
    }
}
