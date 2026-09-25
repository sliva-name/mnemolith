package com.mnemolith.network;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.entity.echo.EchoEntity;

import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.echo.job.EchoWork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
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

    /** Farthest the owner may stand from their echo to command it from the screen or while placing a blueprint. */
    private static final double COMMAND_RANGE = 64.0D;
    /** Farthest a linked chest or a blueprint anchor may be from the echo. */
    private static final double WORK_RANGE = 64.0D;

    public static void handleJob(EchoJobPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            applyJob(player, payload);
        }
    }

    /** Applies one job request from {@code player}. Returns true when it was accepted. Public for {@code /mnemolith jobqa}. */
    public static boolean applyJob(ServerPlayer player, EchoJobPayload payload) {
        if (player.hasDisconnected() || player.isRemoved() || !player.isAlive()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level) || !(level.getEntity(payload.entityId()) instanceof EchoEntity echo) || !echo.isAlive()) {
            return false;
        }
        if (!echo.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.not_yours", echo.ownerName()), true);
            return false;
        }
        if (player.distanceToSqr(echo) > COMMAND_RANGE * COMMAND_RANGE) {
            player.sendSystemMessage(Component.translatable("mnemolith.job.msg.too_far"), true);
            return false;
        }
        EchoJob job = echo.job();
        BlockPos pos = payload.pos();
        switch (payload.action()) {
            case MODE_REPLAY -> {
                EchoRecording recording = echo.recording();
                if (recording == null || recording.length() == 0) {
                    player.sendSystemMessage(Component.translatable("mnemolith.job.msg.no_recording"), true);
                    return false;
                }
                EchoLife.SpawnResult check = EchoLife.canActivate(player, recording);
                if (check != EchoLife.SpawnResult.SPAWNED) {
                    player.sendSystemMessage(Component.translatable("mnemolith.echo.activate_" + check.name().toLowerCase(java.util.Locale.ROOT)), true);
                    return false;
                }
                echo.startReplay(recording);
            }
            case MODE_MINE -> {
                echo.stopReplayIfRunning();
                job.startMining(echo);
            }
            case MODE_BUILD -> {
                echo.stopReplayIfRunning();
                job.startBuilding(echo);
            }
            case MODE_FARM -> {
                echo.stopReplayIfRunning();
                job.startFarming(echo);
            }
            case STOP -> {
                echo.stopReplayIfRunning();
                job.stop(echo);
            }
            case RADIUS -> job.setRadius(payload.value());
            case LINK_CHEST -> {
                if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 8.0D * 8.0D || !level.isLoaded(pos)) {
                    return false;
                }
                if (EchoWork.container(level, pos) == null) {
                    player.sendSystemMessage(Component.translatable("mnemolith.job.msg.not_container"), true);
                    return false;
                }
                if (echo.position().distanceToSqr(Vec3.atCenterOf(pos)) > WORK_RANGE * WORK_RANGE) {
                    player.sendSystemMessage(Component.translatable("mnemolith.job.msg.chest_far"), true);
                    return false;
                }
                if (!EchoWork.mayOpen(level, echo, pos)) {
                    player.sendSystemMessage(Component.translatable("mnemolith.job.msg.chest_denied"), true);
                    return false;
                }
                job.setChest(pos);
                player.sendSystemMessage(Component.translatable("mnemolith.job.msg.chest_linked", pos.getX(), pos.getY(), pos.getZ()), true);
            }
            case UNLINK_CHEST -> {
                job.setChest(null);
                player.sendSystemMessage(Component.translatable("mnemolith.job.msg.chest_unlinked"), true);
            }
            case PLACE_BLUEPRINT -> {
                if (!job.lesson().teachesBuilding()) {
                    return false;
                }
                if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 32.0D * 32.0D || echo.position().distanceToSqr(Vec3.atCenterOf(pos)) > WORK_RANGE * WORK_RANGE || !level.isLoaded(pos)) {
                    player.sendSystemMessage(Component.translatable("mnemolith.job.msg.anchor_far"), true);
                    return false;
                }
                echo.stopReplayIfRunning();
                job.setBlueprintAnchor(pos, Rotation.values()[Math.floorMod(payload.value(), 4)]);
                job.startBuilding(echo);
                echo.sendGhostToOwner();
                player.sendSystemMessage(Component.translatable("mnemolith.job.msg.blueprint_placed", job.plan().size()), true);
            }
            case CLEAR_BLUEPRINT -> {
                if (job.mode() == EchoJob.Mode.BUILD) {
                    job.stop(echo);
                }
                job.clearBlueprintAnchor();
                echo.sendGhostToOwner();
            }
        }
        Mnemolith.LOGGER.debug("Mnemolith echo job action player={} action={} pos={} value={}", player.getGameProfile().name(), payload.action(), pos.toShortString(), payload.value());
        return true;
    }
}
