package com.mnemolith.network;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server handlers for the echo payloads. The client only asks; every rule is checked here. */
public final class EchoNetwork {
    /** Widest angle (degrees) between the look direction and the echo that the server accepts; the client aim assist is narrower. */
    private static final double MAX_ANGLE = 20.0D;

    private EchoNetwork() {}

    public static void handlePossess(EchoPossessPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.hasDisconnected() || player.isRemoved()) {
            return;
        }
        if (!ChronicleLensItem.isFocusing(player)) {
            return;
        }
        if (!(player.level().getEntity(payload.entityId()) instanceof EchoEntity echo)) {
            return;
        }
        if (!echo.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.not_yours", echo.ownerName()), true);
            return;
        }
        double range = CommonConfig.ECHO_POSSESS_RANGE.get() + 2.0D;
        Vec3 eye = player.getEyePosition();
        Vec3 toEcho = echo.getBoundingBox().getCenter().subtract(eye);
        if (toEcho.lengthSqr() > range * range) {
            return;
        }
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, toEcho.normalize().dot(player.getLookAngle())))));
        if (angle > MAX_ANGLE) {
            return;
        }
        EchoPossession.Result result = EchoPossession.possess(player, echo);
        if (result != EchoPossession.Result.POSSESSED) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.possess_" + result.name().toLowerCase(java.util.Locale.ROOT)), true);
            Mnemolith.LOGGER.debug("Mnemolith echo possess refused player={} result={}", player.getGameProfile().name(), result);
        }
    }

    public static void handleUnpossess(EchoUnpossessPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && !player.hasDisconnected() && !player.isRemoved() && player.isAlive()) {
            EchoPossession.unpossess(player, EchoPossession.Reason.KEY);
        }
    }
}
