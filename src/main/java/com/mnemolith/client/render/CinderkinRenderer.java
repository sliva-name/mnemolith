package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.FadedModel;
import com.mnemolith.entity.mob.Cinderkin;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** The faded's frame in charcoal with glowing cracks: opaque and full-bright, so it glows in the Nether's dark. */
public class CinderkinRenderer extends MobRenderer<Cinderkin, FadedModel.State, FadedModel> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/cinderkin.png");

    public CinderkinRenderer(EntityRendererProvider.Context context) {
        super(context, new FadedModel(context.bakeLayer(ModEntityRenderers.FADED)), 0.4F);
    }

    @Override
    public FadedModel.State createRenderState() {
        return new FadedModel.State();
    }

    @Override
    public void extractRenderState(Cinderkin entity, FadedModel.State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attack = entity.getAttackAnim(partialTick);
        state.revealed = true;
    }

    @Override
    public Identifier getTextureLocation(FadedModel.State state) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(Cinderkin entity, BlockPos pos) {
        return 15;
    }
}
