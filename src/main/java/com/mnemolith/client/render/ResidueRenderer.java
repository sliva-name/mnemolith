package com.mnemolith.client.render;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.ResidueModel;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.echo.EchoView;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;

/**
 * Residual echoes: a faint fragment tinted with its temper color. Barely there to the naked eye, clear while the lens
 * is held, bright once read (pinned). While the lens view is up, a label shows what it is, its strength and how far
 * the reading has come.
 */
public class ResidueRenderer extends MobRenderer<ResidueEntity, ResidueRenderState, ResidueModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/residue.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int LABEL_BACKGROUND = 0xC0200A18;

    public ResidueRenderer(EntityRendererProvider.Context context) {
        super(context, new ResidueModel(context.bakeLayer(ModEntityRenderers.RESIDUE)), 0.0F);
    }

    @Override
    public ResidueRenderState createRenderState() {
        return new ResidueRenderState();
    }

    @Override
    public void extractRenderState(ResidueEntity entity, ResidueRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.id = entity.getId();
        Temper temper = entity.temper();
        state.rgb = temper.rgb();
        state.strength = entity.strength();
        state.pinned = entity.isPinned();
        state.read = entity.readProgress() / (float) Residues.READ_TICKS;
        state.volatileFlicker = temper == Temper.VOLATILE;
        // Always lit a little from inside.
        state.lightCoords = Math.max(state.lightCoords, 0xA000A0);
    }

    @Override
    public Identifier getTextureLocation(ResidueRenderState state) {
        return TEXTURE;
    }

    @Override
    protected int getModelTint(ResidueRenderState state) {
        int alpha;
        if (state.pinned) {
            alpha = 0xE0;
        } else if (lensUp()) {
            alpha = 0xA0 + (int) (0x30 * state.read);
        } else {
            alpha = 0x38;
        }
        if (state.volatileFlicker && !state.pinned) {
            double phase = ((System.currentTimeMillis() + state.id * 131L) % 400L) / 400.0D * Math.PI * 2.0D;
            alpha = Math.max(0x20, alpha - (int) (0x18 * (0.5D + 0.5D * Math.sin(phase))));
        }
        return alpha << 24 | state.rgb;
    }

    @Override
    protected @Nullable RenderType getRenderType(ResidueRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        return RenderTypes.entityTranslucentCullItemTarget(TEXTURE);
    }

    private static boolean lensUp() {
        Minecraft minecraft = Minecraft.getInstance();
        return EchoView.thermal() || minecraft.player != null && ChronicleLensItem.isHeld(minecraft.player);
    }

    @Override
    public void submit(ResidueRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        if (EchoView.thermal()) {
            submitLabel(state, poseStack, collector, camera);
        }
    }

    /** "Residue · Fire · strength 4" and the reading (or "read"), through walls, sized like the echo labels. */
    private static void submitLabel(ResidueRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level != null && minecraft.level.getEntity(state.id) instanceof ResidueEntity residue)) {
            return;
        }
        Component line = Component.translatable("mnemolith.residue.label", Component.translatable(residue.tag().translationKey()), state.strength);
        Component status = state.pinned ? Component.translatable("mnemolith.residue.label_read")
                : Component.translatable("mnemolith.residue.label_reading", Math.round(state.read * 100.0F));
        float distance = (float) Math.sqrt(state.distanceToCameraSq);
        float grow = Mth.clamp(distance / 6.0F, 1.0F, 6.0F);
        poseStack.pushPose();
        poseStack.translate(0.0F, state.boundingBoxHeight + 0.35F + 0.1F * grow, 0.0F);
        poseStack.mulPose(camera.orientation);
        float s = 0.025F * grow;
        poseStack.scale(s, -s, s);
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        int color = 0xFF000000 | lighten(state.rgb);
        collector.submitSpecial(RenderPhaseKeys.ALWAYS_ON_TOP, new NameTagFeatureRenderer.Submit(pose, -minecraft.font.width(line) / 2.0F, -10.0F, line,
                FULL_BRIGHT, color, LABEL_BACKGROUND, Font.DisplayMode.NORMAL));
        collector.submitSpecial(RenderPhaseKeys.ALWAYS_ON_TOP, new NameTagFeatureRenderer.Submit(pose, -minecraft.font.width(status) / 2.0F, 0.0F, status,
                FULL_BRIGHT, state.pinned ? 0xFFFFF2FA : 0xFFD8C8E0, LABEL_BACKGROUND, Font.DisplayMode.NORMAL));
        poseStack.popPose();
    }

    private static int lighten(int rgb) {
        int r = ((rgb >> 16) & 0xFF) + 0xFF >> 1;
        int g = ((rgb >> 8) & 0xFF) + 0xFF >> 1;
        int b = (rgb & 0xFF) + 0xFF >> 1;
        return r << 16 | g << 8 | b;
    }
}
