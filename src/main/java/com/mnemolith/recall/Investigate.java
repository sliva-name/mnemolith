package com.mnemolith.recall;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.OriginReadPayload;
import com.mnemolith.network.TraceMarkPayload;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Stage 2. The world keeps a few anchors from this play, and a raised chronicle lens can name what a place is.
 * The server decides. The client only draws the line and the footsteps. Nothing here writes chat.
 */
public final class Investigate {
    /** How close a looked-at block must be to a gesture or an anchor. */
    private static final double READ_SQR = 4.0D;
    /** A brushed imprint is named only when the look is this close to its origin. */
    private static final double IMPRINT_SQR = 9.0D;
    /** First echo: a body already this close. Checked only until that anchor exists. */
    private static final double ECHO_RANGE = 8.0D;

    private static final Map<UUID, Aim> AIM = new HashMap<>();
    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    private Investigate() {}

    public static boolean enabled() {
        return CommonConfig.INVESTIGATE_ENABLED.get();
    }

    public static void tick(ServerPlayer player) {
        if (!enabled() || !(player.level() instanceof ServerLevel level) || !live(player)) {
            return;
        }
        int phase = player.tickCount + (player.getId() & 31);
        if (phase % 40 == 0) {
            witness(player, level);
        }
        if (ChronicleLensItem.isHeld(player) && phase % 20 == 0) {
            footprints(player, level);
        }
        if (!ChronicleLensItem.isFocusing(player)) {
            release(player);
            return;
        }
        if (phase % 4 != 0) {
            return;
        }
        Optional<BlockPos> aimed = aim(player, level);
        if (aimed.isEmpty()) {
            release(player);
            return;
        }
        look(player, aimed.get(), 4);
    }

    public static void forgetSession(UUID player) {
        AIM.remove(player);
        MARKS.remove(player);
    }

    public static void clearSessions() {
        AIM.clear();
        MARKS.clear();
    }

    public static boolean onDeath(ServerPlayer player) {
        if (!live(player)) {
            return false;
        }
        return keep(player, AnchorKind.DEATH, player.blockPosition(), -1, false, 0, player.getYRot());
    }

    public static boolean onMute(ServerPlayer player, BlockPos pos) {
        if (!live(player)) {
            return false;
        }
        return keep(player, AnchorKind.MUTE, pos, -1, false, 0, player.getYRot());
    }

    public static boolean onBlank(ServerPlayer player) {
        if (!live(player)) {
            return false;
        }
        return keep(player, AnchorKind.BLANK, player.blockPosition(), -1, false, 0, player.getYRot());
    }

    public static boolean onEcho(ServerPlayer player) {
        if (!live(player)) {
            return false;
        }
        return keep(player, AnchorKind.ECHO, player.blockPosition(), -1, false, 0, player.getYRot());
    }

    /** Players standing in {@code chunk} when it first fractures. */
    public static void onFracture(ServerLevel level, ChunkPos chunk, int pressure) {
        if (!enabled() || !CommonConfig.INVESTIGATE_RECORD_FRACTURE.get()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (live(player) && player.chunkPosition().equals(chunk)) {
                onFracture(player, player.blockPosition(), pressure);
            }
        }
    }

    public static boolean onFracture(ServerPlayer player, BlockPos pos, int pressure) {
        if (!live(player)) {
            return false;
        }
        return keep(player, AnchorKind.FRACTURE, pos, -1, false, pressure, player.getYRot());
    }

    public static boolean onLoud(ServerPlayer player, BlockPos pos, int pressure) {
        if (!live(player)) {
            return false;
        }
        return keep(player, AnchorKind.LOUD, pos, -1, false, pressure, player.getYRot());
    }

    public static boolean onFlash(ServerPlayer player, Gesture gesture) {
        return trace(player, AnchorKind.FLASH, gesture);
    }

    public static boolean onRecall(ServerPlayer player, Gesture gesture) {
        return trace(player, AnchorKind.RECALL, gesture);
    }

    /**
     * Writes one anchor without the live-player gate. World events use {@link #onDeath} and the other
     * {@code on*} methods, which skip fake and spectator players. The QA suite calls this directly.
     */
    public static boolean keep(ServerPlayer player, AnchorKind kind, BlockPos pos, int gesture, boolean distorted, int pressure, float yaw) {
        if (!enabled() || !records(kind) || !(player.level() instanceof ServerLevel)) {
            return false;
        }
        return note(player, kind, pos, gesture, distorted, pressure, yaw);
    }

    /**
     * The raised lens has been on {@code aimed} for another {@code ticks}. Empty until the look is long enough
     * to name, or when nothing there is this player's to read.
     */
    public static Optional<Origin> look(ServerPlayer player, BlockPos aimed, int ticks) {
        if (!enabled() || !(player.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        Subject subject = find(player, level, aimed);
        Aim aim = AIM.computeIfAbsent(player.getUUID(), id -> new Aim());
        if (subject == null) {
            release(player);
            return Optional.empty();
        }
        if (!subject.key.equals(aim.key)) {
            if (aim.named) {
                tell(player, null);
            }
            aim.key = subject.key;
            aim.held = 0;
            aim.named = false;
        }
        aim.held += Math.max(0, ticks);
        int need = Math.max(1, CommonConfig.INVESTIGATE_READ_TICKS.get());
        if (aim.held < need) {
            return Optional.empty();
        }
        if (!aim.named) {
            aim.named = true;
            unlock(player, subject);
            tell(player, subject.origin);
            if (subject.gesture != null && subject.origin.source() == Origin.GESTURE) {
                onFlash(player, subject.gesture);
            }
            sendMark(player, display(subject.pos, subject.origin.distorted(), subject.yaw), subject.origin.distorted());
        }
        return Optional.of(subject.origin);
    }

    public static int size(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return 0;
        }
        return PlayAnchors.get(level.getServer()).size(player.getUUID());
    }

    public static int count(ServerPlayer player, AnchorKind kind) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return 0;
        }
        return PlayAnchors.get(level.getServer()).count(player.getUUID(), kind);
    }

    public static int loudPressure(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return -1;
        }
        return PlayAnchors.get(level.getServer()).loudPressure(player.getUUID());
    }

    public static Optional<Anchor> nearest(ServerPlayer player, BlockPos pos, double blocks) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return Optional.empty();
        }
        return PlayAnchors.get(level.getServer()).within(
                player.getUUID(),
                level.dimension().identifier().toString(),
                pos,
                blocks,
                level.getGameTime(),
                CommonConfig.INVESTIGATE_MAX_AGE.get());
    }

    public static void forget(ServerPlayer player) {
        forgetSession(player.getUUID());
        if (player.level() instanceof ServerLevel level && level.getServer() != null) {
            PlayAnchors.get(level.getServer()).forget(player.getUUID());
        }
    }

    /** True when an echo body is already close and this player had not met one yet. */
    public static boolean noticeEcho(ServerPlayer player) {
        if (!enabled() || !CommonConfig.INVESTIGATE_RECORD_ECHO.get() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level.getServer() != null && PlayAnchors.get(level.getServer()).has(player.getUUID(), AnchorKind.ECHO)) {
            return false;
        }
        if (level.getEntitiesOfClass(EchoEntity.class, player.getBoundingBox().inflate(ECHO_RANGE)).isEmpty()) {
            return false;
        }
        return keep(player, AnchorKind.ECHO, player.blockPosition(), -1, false, 0, player.getYRot());
    }

    private static void witness(ServerPlayer player, ServerLevel level) {
        if (CommonConfig.INVESTIGATE_RECORD_ECHO.get() && (level.getServer() == null || !PlayAnchors.get(level.getServer()).has(player.getUUID(), AnchorKind.ECHO))) {
            noticeEcho(player);
        }
        if (!CommonConfig.INVESTIGATE_RECORD_LOUD.get()) {
            return;
        }
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(player.blockPosition()));
        if (memory != null && memory.cachedPressure() >= CommonConfig.SATURATED_THRESHOLD.get()) {
            onLoud(player, player.blockPosition(), memory.cachedPressure());
        }
    }

    private static void footprints(ServerPlayer player, ServerLevel level) {
        if (level.getServer() == null) {
            return;
        }
        Optional<Anchor> anchor = PlayAnchors.get(level.getServer()).within(
                player.getUUID(),
                level.dimension().identifier().toString(),
                player.blockPosition(),
                CommonConfig.INVESTIGATE_TRACE_RADIUS.get(),
                level.getGameTime(),
                CommonConfig.INVESTIGATE_MAX_AGE.get());
        if (anchor.isEmpty()) {
            return;
        }
        BlockPos shown = display(anchor.get().pos(), anchor.get().distorted(), anchor.get().yaw());
        Mark mark = MARKS.get(player.getUUID());
        long now = level.getGameTime();
        int linger = Math.max(20, CommonConfig.INVESTIGATE_LINGER.get());
        if (mark != null && mark.pos.equals(shown) && now - mark.when < linger) {
            return;
        }
        MARKS.put(player.getUUID(), new Mark(shown, now));
        sendMark(player, shown, anchor.get().distorted());
    }

    private static Optional<BlockPos> aim(ServerPlayer player, ServerLevel level) {
        double range = CommonConfig.INVESTIGATE_READ_RANGE.get();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.of(player)));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        return Optional.of(hit.getBlockPos());
    }

    private static @Nullable Subject find(ServerPlayer player, ServerLevel level, BlockPos aimed) {
        long now = level.getGameTime();
        String dimension = level.dimension().identifier().toString();
        if (level.getServer() != null) {
            Optional<Anchor> anchor = PlayAnchors.get(level.getServer()).nearest(player.getUUID(), dimension, aimed, READ_SQR, now, CommonConfig.INVESTIGATE_MAX_AGE.get());
            if (anchor.isPresent()) {
                return fromAnchor(anchor.get(), now);
            }
        }
        Subject gesture = fromGesture(player, level, aimed, dimension, now);
        if (gesture != null) {
            return gesture;
        }
        return fromImprint(player, level, aimed, now);
    }

    private static Subject fromAnchor(Anchor anchor, long now) {
        long age = Math.max(0L, now - anchor.gameTime());
        Origin origin = new Origin(Origin.ANCHOR, anchor.kind().ordinal(), Origin.ageBand(age), anchor.distorted(), !anchor.kind().place());
        return new Subject(origin, anchor.pos(), anchor.yaw(), null, key(origin, anchor.pos()));
    }

    private static @Nullable Subject fromGesture(ServerPlayer player, ServerLevel level, BlockPos aimed, String dimension, long now) {
        long maxAge = CommonConfig.RECALL_MAX_AGE.get();
        Gesture best = null;
        double bestDistance = READ_SQR;
        for (Gesture gesture : LivingMemory.log(player).gestures()) {
            if (!dimension.equals(gesture.dimension())) {
                continue;
            }
            long age = now - gesture.gameTime();
            if (age < 0L || age > maxAge) {
                continue;
            }
            double distance = gesture.pos().distSqr(aimed);
            if (distance > bestDistance) {
                continue;
            }
            best = gesture;
            bestDistance = distance;
        }
        if (best == null) {
            return null;
        }
        Origin origin = new Origin(Origin.GESTURE, best.kind().ordinal(), Origin.ageBand(now - best.gameTime()), best.distorted(), true);
        return new Subject(origin, best.pos(), best.yaw(), best, key(origin, best.pos()));
    }

    private static @Nullable Subject fromImprint(ServerPlayer player, ServerLevel level, BlockPos aimed, long now) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(aimed));
        if (memory == null || memory.imprintCount() == 0) {
            return null;
        }
        Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
        Imprint best = null;
        double bestDistance = IMPRINT_SQR;
        for (int i = 0; i < memory.imprintCount(); i++) {
            Imprint imprint = memory.imprintAt(i);
            if (!discovery.hasTag(imprint.tag())) {
                continue;
            }
            double distance = imprint.origin().distSqr(aimed);
            if (distance > bestDistance) {
                continue;
            }
            best = imprint;
            bestDistance = distance;
        }
        if (best == null) {
            return null;
        }
        long age = Math.max(0L, now - best.writtenAt());
        Origin origin = new Origin(Origin.IMPRINT, best.tag().ordinal(), Origin.ageBand(age), best.rewritten(), best.player().isPresent());
        return new Subject(origin, best.origin(), 0.0F, null, key(origin, best.origin()));
    }

    private static void unlock(ServerPlayer player, Subject subject) {
        FoundMemory memory = player.getData(ModAttachments.FOUND_MEMORY.get());
        boolean changed = false;
        if (subject.origin.source() == Origin.GESTURE) {
            changed = memory.noteGesture(subject.origin.kind());
        } else if (subject.origin.source() == Origin.ANCHOR) {
            changed = memory.noteAnchor(subject.origin.kind());
        } else {
            changed = memory.noteImprint();
        }
        if (subject.origin.distorted()) {
            changed = memory.noteDistorted() || changed;
        }
        if (changed) {
            player.syncData(ModAttachments.FOUND_MEMORY.get());
        }
    }

    private static boolean trace(ServerPlayer player, AnchorKind kind, Gesture gesture) {
        if (gesture == null) {
            return false;
        }
        return keep(player, kind, gesture.pos(), gesture.kind().ordinal(), gesture.distorted(), 0, gesture.yaw());
    }

    private static boolean note(ServerPlayer player, AnchorKind kind, BlockPos pos, int gesture, boolean distorted, int pressure, float yaw) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return false;
        }
        Anchor anchor = new Anchor(
                kind,
                player.getUUID(),
                level.dimension().identifier().toString(),
                pos,
                level.getGameTime(),
                gesture,
                distorted,
                pressure,
                yaw);
        return PlayAnchors.get(level.getServer()).add(
                anchor,
                level.getGameTime(),
                CommonConfig.INVESTIGATE_MAX_ANCHORS.get(),
                CommonConfig.INVESTIGATE_MAX_AGE.get());
    }

    private static void release(ServerPlayer player) {
        Aim aim = AIM.get(player.getUUID());
        if (aim == null) {
            return;
        }
        if (aim.named) {
            tell(player, null);
        }
        AIM.remove(player.getUUID());
    }

    private static void tell(ServerPlayer player, @Nullable Origin origin) {
        if (player instanceof FakePlayer) {
            return;
        }
        OriginReadPayload payload = origin == null
                ? OriginReadPayload.clear()
                : new OriginReadPayload(true, origin.source(), origin.kind(), origin.age(), origin.distorted(), origin.personal());
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void sendMark(ServerPlayer player, BlockPos pos, boolean distorted) {
        if (player instanceof FakePlayer) {
            return;
        }
        int linger = Math.max(20, CommonConfig.INVESTIGATE_LINGER.get());
        PacketDistributor.sendToPlayer(player, new TraceMarkPayload(pos, linger, distorted));
    }

    private static BlockPos display(BlockPos pos, boolean distorted, float yaw) {
        if (!distorted) {
            return pos;
        }
        return pos.relative(Direction.fromYRot(yaw).getClockWise());
    }

    private static String key(Origin origin, BlockPos pos) {
        return origin.source() + ":" + origin.kind() + ":" + pos.asLong() + ":" + origin.distorted();
    }

    private static boolean records(AnchorKind kind) {
        return switch (kind) {
            case DEATH -> CommonConfig.INVESTIGATE_RECORD_DEATH.get();
            case MUTE -> CommonConfig.INVESTIGATE_RECORD_MUTE.get();
            case BLANK -> CommonConfig.INVESTIGATE_RECORD_BLANK.get();
            case ECHO -> CommonConfig.INVESTIGATE_RECORD_ECHO.get();
            case FRACTURE -> CommonConfig.INVESTIGATE_RECORD_FRACTURE.get();
            case LOUD -> CommonConfig.INVESTIGATE_RECORD_LOUD.get();
            case FLASH, RECALL -> CommonConfig.INVESTIGATE_RECORD_TRACES.get();
        };
    }

    /** Fake players and spectators do not leave traces through world events or the player tick. */
    private static boolean live(ServerPlayer player) {
        return !(player instanceof FakePlayer) && !player.isSpectator();
    }

    private static final class Aim {
        private String key = "";
        private int held;
        private boolean named;
    }

    private static final class Mark {
        private final BlockPos pos;
        private final long when;

        private Mark(BlockPos pos, long when) {
            this.pos = pos;
            this.when = when;
        }
    }

    private record Subject(Origin origin, BlockPos pos, float yaw, @Nullable Gesture gesture, String key) {}
}
