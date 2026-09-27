package com.mnemolith.recall;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.FakePlace;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.RecallGhostPayload;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Per-player gesture log. Recording is event-driven. A wow moment (replicant recall or a local flash)
 * shares one cooldown. The server decides; the client only draws {@link RecallGhostPayload}.
 */
public final class LivingMemory {
    /** Same kind, closer than two blocks, inside this window: one gesture, not a burst. */
    public static final int DEBOUNCE_TICKS = 20;
    private static final double STEP_SQR = 4.0D;

    private static final Map<UUID, Deque<BlockPos>> STEPS = new ConcurrentHashMap<>();

    private LivingMemory() {}

    public static boolean enabled() {
        return CommonConfig.RECALL_ENABLED.get();
    }

    public static GestureLog log(ServerPlayer player) {
        return player.getData(ModAttachments.GESTURE_LOG.get());
    }

    public static int size(ServerPlayer player) {
        return log(player).size();
    }

    public static @Nullable Gesture newest(ServerPlayer player) {
        return log(player).newest();
    }

    /** Drops the unsaved step ring (logout or a dimension change). The saved log stays. */
    public static void forgetSteps(UUID player) {
        STEPS.remove(player);
    }

    public static void clearSteps() {
        STEPS.clear();
    }

    public static void tick(ServerPlayer player) {
        if (!accept(player)) {
            return;
        }
        noteStep(player);
        if ((player.tickCount + (player.getId() & 31)) % 40 != 0) {
            return;
        }
        if (!showing(player) || !cooldownReady(player)) {
            return;
        }
        Optional<Gesture> gesture = localCandidate(player);
        if (gesture.isEmpty() || player.getRandom().nextDouble() >= CommonConfig.RECALL_LOCAL_CHANCE.get()) {
            return;
        }
        playLocal(player, gesture.get());
    }

    public static void onAttack(ServerPlayer player, @Nullable Entity target) {
        if (!accept(player) || !CommonConfig.RECALL_RECORD_ATTACKS.get()) {
            return;
        }
        String targetId = "";
        if (target != null) {
            targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        }
        remember(player, GestureKind.ATTACK, player.blockPosition(), player.getYRot(), player.getXRot(), itemId(player.getMainHandItem()), "", -1, targetId);
    }

    public static void onPlace(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!accept(player) || !CommonConfig.RECALL_RECORD_PLACES.get() || FakePlace.skippingOwnerImprint()) {
            return;
        }
        if (state.getBlock() == com.mnemolith.content.ModBlocks.REPLICATED_MOMENT.get()) {
            return;
        }
        remember(player, GestureKind.PLACE, pos, player.getYRot(), player.getXRot(), "", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), -1, "");
    }

    public static void onUse(ServerPlayer player, BlockPos pos, net.minecraft.core.@Nullable Direction face, BlockState state) {
        if (!accept(player) || !CommonConfig.RECALL_RECORD_USES.get() || !trackedUse(state)) {
            return;
        }
        int ordinal = face == null ? -1 : face.ordinal();
        remember(player, GestureKind.USE, pos, player.getYRot(), player.getXRot(), itemId(player.getMainHandItem()), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), ordinal, "");
    }

    public static void onFall(ServerPlayer player, double distance) {
        if (!accept(player) || !CommonConfig.RECALL_RECORD_FALLS.get()) {
            return;
        }
        if (distance < CommonConfig.FALL_DISTANCE_MIN.get() && !(distance > 3.0D)) {
            return;
        }
        remember(player, GestureKind.FALL, player.blockPosition(), player.getYRot(), player.getXRot(), "", "", -1, "");
    }

    public static boolean trackedUse(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof LeverBlock
                || block instanceof ButtonBlock;
    }

    /**
     * Writes one gesture, debounced. Does not reject fake players: callers that listen to world events
     * check {@link #accept} first, and the QA suite calls this directly.
     */
    public static boolean remember(ServerPlayer player, GestureKind kind, BlockPos pos, float yaw, float pitch, String itemId, String blockId, int face, String targetId) {
        if (!(player.level() instanceof ServerLevel level) || !enabled()) {
            return false;
        }
        long now = level.getGameTime();
        GestureLog log = log(player);
        Gesture last = log.newest();
        if (last != null && last.kind() == kind && now - last.gameTime() < DEBOUNCE_TICKS && last.pos().distSqr(pos) < STEP_SQR) {
            return false;
        }
        boolean distorted = player.getRandom().nextDouble() < CommonConfig.RECALL_DISTORTION_CHANCE.get();
        Gesture gesture = new Gesture(kind, level.dimension().identifier().toString(), pos, yaw, pitch, itemId, blockId, face, targetId, now, distorted, copySteps(player));
        log.add(gesture, now, CommonConfig.RECALL_MAX_GESTURES.get(), CommonConfig.RECALL_MAX_AGE.get());
        return true;
    }

    /** Inserts a gesture as given, including one whose time is already in the past. No debounce. */
    public static void plant(ServerPlayer player, Gesture gesture) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        log(player).add(gesture, level.getGameTime(), CommonConfig.RECALL_MAX_GESTURES.get(), CommonConfig.RECALL_MAX_AGE.get());
    }

    public static Optional<Gesture> qualifying(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return log(player).qualifying(level.getGameTime(), CommonConfig.RECALL_MIN_AGE.get(), CommonConfig.RECALL_MAX_AGE.get());
    }

    public static boolean cooldownReady(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        long wait = CommonConfig.RECALL_COOLDOWN.get();
        if (wait <= 0) {
            return true;
        }
        return level.getGameTime() - log(player).lastWow() >= wait;
    }

    public static void markWow(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            log(player).markWow(level.getGameTime());
        }
    }

    /** True when a recall roll is allowed: old gesture, cooldown, and the player is in the audience. */
    public static boolean canRollRecall(ServerPlayer player) {
        return accept(player) && showing(player) && cooldownReady(player) && qualifying(player).isPresent();
    }

    /** Rolls {@code replicantRecallChance} once. On success the replicant enters RECALL and the cooldown starts. */
    public static boolean rollRecall(MomentReplicant replicant, ServerPlayer player) {
        if (!canRollRecall(player)) {
            return false;
        }
        if (player.getRandom().nextDouble() >= CommonConfig.RECALL_CHANCE.get()) {
            return false;
        }
        Optional<Gesture> chosen = qualifying(player);
        if (chosen.isEmpty()) {
            return false;
        }
        markWow(player);
        Gesture gesture = chosen.get();
        replicant.beginRecall(player, gesture);
        Investigate.onRecall(player, gesture);
        return true;
    }

    /**
     * An old gesture of this player, in this dimension, within {@code localRadius}, whose chunk is not muted.
     * Mute at the site suppresses the flash. It does not suppress a replicant recall somewhere else.
     */
    public static Optional<Gesture> localCandidate(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !enabled()) {
            return Optional.empty();
        }
        long now = level.getGameTime();
        long minAge = CommonConfig.RECALL_MIN_AGE.get();
        long maxAge = CommonConfig.RECALL_MAX_AGE.get();
        String dimension = level.dimension().identifier().toString();
        int radius = CommonConfig.RECALL_LOCAL_RADIUS.get();
        double bestDistance = radius * (double) radius;
        BlockPos feet = player.blockPosition();
        Gesture best = null;
        for (Gesture gesture : log(player).gestures()) {
            if (!dimension.equals(gesture.dimension())) {
                continue;
            }
            long age = now - gesture.gameTime();
            if (age < minAge || age > maxAge) {
                continue;
            }
            double distance = feet.distSqr(gesture.pos());
            if (distance > bestDistance) {
                continue;
            }
            if (LoadedChunkMemory.isMuted(level, gesture.pos())) {
                continue;
            }
            best = gesture;
            bestDistance = distance;
        }
        return Optional.ofNullable(best);
    }

    /** Sends the owner-only flash and starts the shared cooldown. No chat. */
    public static void playLocal(ServerPlayer player, Gesture gesture) {
        markWow(player);
        List<BlockPos> trail = RecallSpace.trail(gesture);
        if (trail.size() > Gesture.TRAIL_CAP) {
            trail = trail.subList(trail.size() - Gesture.TRAIL_CAP, trail.size());
        }
        Investigate.onFlash(player, gesture);
        PacketDistributor.sendToPlayer(player, new RecallGhostPayload(
                RecallSpace.place(gesture),
                RecallSpace.yaw(gesture),
                gesture.pitch(),
                gesture.kind().ordinal(),
                gesture.distorted(),
                trail));
    }

    private static boolean accept(ServerPlayer player) {
        return enabled() && !(player instanceof FakePlayer) && player.level() instanceof ServerLevel;
    }

    /** Creative, spectator, an open menu, or a storm overhead: no wow moment. Recording still happens. */
    private static boolean showing(ServerPlayer player) {
        if (CommonConfig.RECALL_SKIP_CREATIVE.get() && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (player.containerMenu != player.inventoryMenu) {
            return false;
        }
        if (CommonConfig.RECALL_SKIP_STORM.get() && player.level() instanceof ServerLevel level && Storms.at(level, ChunkPos.containing(player.blockPosition())) != null) {
            return false;
        }
        return true;
    }

    private static void noteStep(ServerPlayer player) {
        BlockPos now = player.blockPosition();
        Deque<BlockPos> steps = STEPS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        BlockPos last = steps.peekLast();
        if (last != null && last.distSqr(now) < STEP_SQR) {
            return;
        }
        steps.addLast(now.immutable());
        while (steps.size() > Gesture.TRAIL_CAP) {
            steps.removeFirst();
        }
    }

    private static List<BlockPos> copySteps(ServerPlayer player) {
        Deque<BlockPos> steps = STEPS.get(player.getUUID());
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(steps));
    }

    private static String itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }
}
