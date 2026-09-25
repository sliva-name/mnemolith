package com.mnemolith.client.echo;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.echo.EchoView;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;

/**
 * Player-model renderer for echoes and shells, using the owner's skin. Echoes are translucent and tinted pink.
 * Shells are opaque with a faint grey cast. Holds a slim twin so both skin models look right.
 * <p>
 * While the lens view is up, every echo also gets a filled light-pink silhouette in the always-on-top phase (depth is
 * cleared first, so it shows through walls), the targeted one near-white and pulsing, and your own echoes carry a
 * status label (job, progress, or why they stopped) above the head.
 */
public class EchoRenderer<T extends Avatar & ClientAvatarEntity> extends AvatarRenderer<T> {
    /** Rose pink, about half opaque (back faces are culled, so layers do not stack up). */
    public static final int ECHO_TINT = 0x88FFA8D6;
    public static final int SHELL_TINT = 0xFFC9C3CC;
    /** Filled silhouette through walls: light pink, mostly opaque. */
    public static final int SILHOUETTE_TINT = 0xD0FFB3DC;
    private static final Identifier SILHOUETTE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/echo_silhouette.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int LABEL_TEXT = 0xFFFFE8F4;
    private static final int LABEL_STOPPED = 0xFFFFB089;
    private static final int LABEL_BACKGROUND = 0xC0200A18;

    private static @Nullable EchoRenderer<?> echoRenderer;
    private static @Nullable EchoRenderer<?> shellRenderer;
    private final boolean echo;
    private final @Nullable EchoRenderer<T> slim;

    public EchoRenderer(EntityRendererProvider.Context context, boolean echo) {
        this(context, echo, false);
    }

    private EchoRenderer(EntityRendererProvider.Context context, boolean echo, boolean slimModel) {
        super(context, slimModel);
        this.echo = echo;
        this.slim = slimModel ? null : new EchoRenderer<>(context, echo, true);
        if (!slimModel) {
            if (echo) {
                echoRenderer = this;
            } else {
                shellRenderer = this;
            }
        }
    }

    /**
     * {@code EntityRenderDispatcher.submit} picks the renderer for any {@link AvatarRenderState} from the player
     * renderers, so echoes and shells would be drawn as plain players. The player renderer fires
     * {@link RenderPlayerEvent.Pre} first; for our entity types we cancel it and submit through this renderer instead.
     * The pose stack is already at the entity, and the render offset (crouch) is the same for both renderers.
     */
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
        if (event.getRenderer() instanceof EchoRenderer<?>) {
            return;
        }
        AvatarRenderState state = event.getRenderState();
        EchoRenderer<?> target = state.entityType == ModEntities.ECHO.get() ? echoRenderer
                : state.entityType == ModEntities.ECHO_SHELL.get() ? shellRenderer : null;
        if (target == null) {
            return;
        }
        event.setCanceled(true);
        CameraRenderState camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        target.submit(state, event.getPoseStack(), event.getSubmitNodeCollector(), camera);
    }

    @Override
    public void submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (this.slim != null && state.skin.model() == PlayerModelType.SLIM) {
            this.slim.submit(state, poseStack, submitNodeCollector, camera);
            return;
        }
        super.submit(state, poseStack, submitNodeCollector, camera);
        if (this.echo && EchoView.thermal()) {
            this.submitSilhouette(state, poseStack, submitNodeCollector);
            submitStatusLabel(state, poseStack, submitNodeCollector, camera);
        }
    }

    /** The same model and pose as the body, drawn with a white texture and a pink tint after the depth buffer is cleared. */
    private void submitSilhouette(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        float scale = state.scale;
        poseStack.scale(scale, scale, scale);
        this.setupRotations(state, poseStack, state.bodyRot, scale);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.scale(state, poseStack);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitSpecial(RenderPhaseKeys.ALWAYS_ON_TOP, new ModelFeatureRenderer.Submit<>(
                RenderTypes.entityTranslucentCullItemTarget(SILHOUETTE), poseStack.last().copy(), this.model, state,
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, silhouetteTint(state.id), null, null));
        poseStack.popPose();
    }

    /** Light pink for every echo; the target is near-white pink and pulses about twice a second. */
    public static int silhouetteTint(int entityId) {
        if (!EchoView.isTarget(entityId)) {
            return SILHOUETTE_TINT;
        }
        double phase = (System.currentTimeMillis() % 600L) / 600.0D * Math.PI * 2.0D;
        int alpha = 0xC8 + (int) (0x37 * (0.5D + 0.5D * Math.sin(phase)));
        return (alpha << 24) | 0xFFF0F8;
    }

    /** Status of your own echo above its head, readable at a distance (scaled up with distance) and through walls. */
    private static void submitStatusLabel(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !(minecraft.level.getEntity(state.id) instanceof EchoEntity echo) || !echo.isOwnedBy(minecraft.player)) {
            return;
        }
        Component text = echo.jobStatus();
        if (text.getString().isEmpty()) {
            return;
        }
        float distance = (float) Math.sqrt(state.distanceToCameraSq);
        // Grows with distance so the label keeps roughly the same on-screen size from 6 to ~35 blocks.
        float grow = Mth.clamp(distance / 6.0F, 1.0F, 6.0F);
        poseStack.pushPose();
        poseStack.translate(0.0F, state.boundingBoxHeight + 0.45F + 0.1F * grow, 0.0F);
        poseStack.mulPose(camera.orientation);
        float s = 0.025F * grow;
        poseStack.scale(s, -s, s);
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        float x = -minecraft.font.width(text) / 2.0F;
        int color = echo.jobStopped() ? LABEL_STOPPED : LABEL_TEXT;
        // Stage 3: a second line with the memory band of the chunk it works in, from saturation up.
        // With two lines the status moves up one row so the lower line, not the status, sits just above the head.
        com.mnemolith.pressure.PressureBand strain = echo.strain();
        boolean strained = strain.ordinal() >= com.mnemolith.pressure.PressureBand.SATURATED.ordinal();
        collector.submitSpecial(RenderPhaseKeys.ALWAYS_ON_TOP, new NameTagFeatureRenderer.Submit(pose, x, strained ? -10.0F : 0.0F, text, FULL_BRIGHT, color, LABEL_BACKGROUND, Font.DisplayMode.NORMAL));
        if (strained) {
            Component line = Component.translatable("mnemolith.job.strain", Component.translatable(strain.translationKey()));
            float lx = -minecraft.font.width(line) / 2.0F;
            int lineColor = switch (strain) {
                case SATURATED -> 0xFFFFE08A;
                case OVERLOADED -> 0xFFFFA060;
                default -> 0xFFFF7080;
            };
            collector.submitSpecial(RenderPhaseKeys.ALWAYS_ON_TOP, new NameTagFeatureRenderer.Submit(pose, lx, 0.0F, line, FULL_BRIGHT, lineColor, LABEL_BACKGROUND, Font.DisplayMode.NORMAL));
        }
        poseStack.popPose();
    }

    @Override
    protected int getModelTint(AvatarRenderState state) {
        if (!this.echo) {
            return SHELL_TINT;
        }
        if (EchoView.thermal() && state.id == EchoView.targetId()) {
            double phase = (System.currentTimeMillis() % 600L) / 600.0D * Math.PI * 2.0D;
            int alpha = 0xA0 + (int) (0x50 * (0.5D + 0.5D * Math.sin(phase)));
            return (alpha << 24) | 0xFFEAF6;
        }
        return ECHO_TINT;
    }

    @Override
    protected @Nullable RenderType getRenderType(AvatarRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        if (this.echo && isBodyVisible) {
            return RenderTypes.entityTranslucentCullItemTarget(this.getTextureLocation(state));
        }
        return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }
}
