package com.mnemolith.client.render;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.FadedModel;
import com.mnemolith.entity.mob.Faded;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Translucent: barely there unseen, nearly solid while a lens reveals it. The alpha breathes a little. */
public class FadedRenderer extends MobRenderer<Faded, FadedModel.State, FadedModel> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/faded.png");
    public static final int UNSEEN_ALPHA = 0x78;
    public static final int REVEALED_ALPHA = 0xE0;

    public FadedRenderer(EntityRendererProvider.Context context) {
        super(context, new FadedModel(context.bakeLayer(ModEntityRenderers.FADED)), 0.3F);
    }

    @Override
    public FadedModel.State createRenderState() {
        return new FadedModel.State();
    }

    @Override
    public void extractRenderState(Faded entity, FadedModel.State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attack = entity.getAttackAnim(partialTick);
        state.scene = entity.scene();
        state.revealed = entity.revealed();
    }

    @Override
    public Identifier getTextureLocation(FadedModel.State state) {
        return TEXTURE;
    }

    @Override
    protected int getModelTint(FadedModel.State state) {
        int base = state.revealed ? REVEALED_ALPHA : UNSEEN_ALPHA;
        int alpha = Mth.clamp(base + (int) (Mth.sin(state.ageInTicks * 0.09F) * 0x14), 0x20, 0xFF);
        return alpha << 24 | 0xFFFFFF;
    }

    @Override
    protected @Nullable RenderType getRenderType(FadedModel.State state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        if (isBodyVisible) {
            return RenderTypes.entityTranslucent(this.getTextureLocation(state));
        }
        return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }
}
