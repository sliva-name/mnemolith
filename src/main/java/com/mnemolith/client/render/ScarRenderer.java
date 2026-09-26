package com.mnemolith.client.render;

import java.util.List;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.ResidueModel;
import com.mnemolith.echo.EchoView;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.entity.echo.ScarEntity;
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
 * The Scar: the residue fragment grown to more than twice its size, cracked and dark, its tint drifting through the
 * tempers that merged into it. Solid enough to see without the lens; flares while casting a recall, bright when read.
 * In the lens view a label shows the reading progress (or "read").
 */
public class ScarRenderer extends MobRenderer<ScarEntity, ScarRenderState, ResidueModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/scar.png");
    private static final float SCALE = 2.2F;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int LABEL_BACKGROUND = 0xC0200A18;

    public ScarRenderer(EntityRendererProvider.Context context) {
        super(context, new ResidueModel(context.bakeLayer(ModEntityRenderers.RESIDUE)), 0.0F);
    }

    @Override
    public ScarRenderState createRenderState() {
        return new ScarRenderState();
    }

    @Override
    public void extractRenderState(ScarEntity entity, ScarRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.id = entity.getId();
        List<Temper> tempers = entity.tempers();
        if (state.tempers.length != tempers.size()) {
            state.tempers = new int[tempers.size()];
        }
        for (int i = 0; i < tempers.size(); i++) {
            state.tempers[i] = tempers.get(i).rgb();
        }
        state.pinned = entity.isPinned();
        state.casting = entity.isCasting();
        state.read = entity.readProgress() / (float) ScarEntity.READ_TICKS;
        state.strength = entity.merged();
        state.rgb = blend(state.tempers, (entity.tickCount + partialTick) / 40.0F);
        state.lightCoords = Math.max(state.lightCoords, 0xC000C0);
    }

    /** Slides through the merged colours, one every two seconds. */
    private static int blend(int[] colours, float t) {
        if (colours.length == 0) {
            return 0x9A7FD0;
        }
        int i = Mth.floor(t) % colours.length;
        int a = colours[i];
        int b = colours[(i + 1) % colours.length];
        float f = t - Mth.floor(t);
        int r = (int) Mth.lerp(f, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(f, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(f, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    @Override
    protected void scale(ScarRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(ScarRenderState state) {
        return TEXTURE;
    }

    @Override
    protected int getModelTint(ScarRenderState state) {
        int alpha = state.pinned ? 0xF0 : state.casting ? 0xE0 : 0xB8;
        return alpha << 24 | state.rgb;
    }

    @Override
    protected @Nullable RenderType getRenderType(ScarRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        return RenderTypes.entityTranslucentCullItemTarget(TEXTURE);
    }

    @Override
    public void submit(ScarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        if (EchoView.thermal()) {
            Minecraft minecraft = Minecraft.getInstance();
            Component line = Component.translatable("mnemolith.scar.label", state.strength);
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
            collector.submitSpecial(RenderPhaseKeys.ALWAYS_ON_TOP, new NameTagFeatureRenderer.Submit(pose, -minecraft.font.width(line) / 2.0F, -10.0F, line,
                    FULL_BRIGHT, 0xFFE8D8FF, LABEL_BACKGROUND, Font.DisplayMode.NORMAL));
            collector.submitSpecial(RenderPhaseKeys.ALWAYS_ON_TOP, new NameTagFeatureRenderer.Submit(pose, -minecraft.font.width(status) / 2.0F, 0.0F, status,
                    FULL_BRIGHT, state.pinned ? 0xFFFFF2FA : 0xFFD8C8E0, LABEL_BACKGROUND, Font.DisplayMode.NORMAL));
            poseStack.popPose();
        }
    }
}
