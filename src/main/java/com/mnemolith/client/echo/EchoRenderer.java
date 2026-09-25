package com.mnemolith.client.echo;

import org.jspecify.annotations.Nullable;

import com.mnemolith.echo.EchoView;
import com.mnemolith.entity.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
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

/**
 * Player-model renderer for echoes and shells, using the owner's skin. Echoes are translucent and tinted pink;
 * the targeted echo pulses brighter while the lens view is up. Shells are opaque with a faint grey cast.
 * Holds a slim twin so both skin models look right.
 */
public class EchoRenderer<T extends Avatar & ClientAvatarEntity> extends AvatarRenderer<T> {
    /** Rose pink, about half opaque (back faces are culled, so layers do not stack up). */
    public static final int ECHO_TINT = 0x88FFA8D6;
    public static final int SHELL_TINT = 0xFFC9C3CC;

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
