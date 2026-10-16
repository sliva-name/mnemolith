package com.mnemolith.client.render;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.FadedModel;
import com.mnemolith.entity.boss.Unremembered;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** The faded's body at {@link #SCALE}, in its own darker cloth with a splinter crown; more solid than a faded. */
public class UnrememberedRenderer extends MobRenderer<Unremembered, FadedModel.State, FadedModel> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/unremembered.png");
    public static final float SCALE = 1.8F;
    public static final int UNSEEN_ALPHA = 0x80;
    /** Unseen, the body is also washed toward a cold grey so the reveal reads as colour coming back, not only as an outline. */
    public static final int UNSEEN_TINT = 0x9CA0B4;
    public static final int REVEALED_ALPHA = 0xF0;

    public UnrememberedRenderer(EntityRendererProvider.Context context) {
        super(context, new FadedModel(context.bakeLayer(ModEntityRenderers.FADED)), 0.7F);
    }

    @Override
    public FadedModel.State createRenderState() {
        return new FadedModel.State();
    }

    @Override
    public void extractRenderState(Unremembered entity, FadedModel.State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attack = entity.getAttackAnim(partialTick);
        state.revealed = entity.revealed();
    }

    @Override
    protected void scale(FadedModel.State state, com.mojang.blaze3d.vertex.PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(FadedModel.State state) {
        return TEXTURE;
    }

    @Override
    protected int getModelTint(FadedModel.State state) {
        int base = state.revealed ? REVEALED_ALPHA : UNSEEN_ALPHA;
        int alpha = Mth.clamp(base + (int) (Mth.sin(state.ageInTicks * 0.06F) * 0x18), 0x30, 0xFF);
        return alpha << 24 | (state.revealed ? 0xFFFFFF : UNSEEN_TINT);
    }

    @Override
    protected @Nullable RenderType getRenderType(FadedModel.State state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        if (isBodyVisible) {
            return RenderTypes.entityTranslucent(this.getTextureLocation(state));
        }
        return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }
}
