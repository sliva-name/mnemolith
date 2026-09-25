package com.mnemolith.echo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side self-recording. One session per player, sampled once per player tick. The server records what it
 * accepted: position, look, sneak, sprint, swing, and the break, place, and use actions that went through.
 * Nothing here is written to disk until the session becomes a filled recording item.
 */
public final class EchoRecorder {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private EchoRecorder() {}

    private static final class Session {
        final ResourceKey<Level> dimension;
        final Vec3 origin;
        final int maxFrames;
        final ByteBuffer frames;
        final List<EchoAction> actions = new ArrayList<>();
        final List<Pending> pending = new ArrayList<>();
        int count;

        Session(ResourceKey<Level> dimension, Vec3 origin, int maxFrames) {
            this.dimension = dimension;
            this.origin = origin;
            this.maxFrames = maxFrames;
            this.frames = ByteBuffer.allocate(maxFrames * EchoRecording.FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    private static final class Pending {
        final int tick;
        final InteractionHand hand;
        final BlockHitResult hit;
        final BlockState before;
        final Item item;
        boolean placed;

        Pending(int tick, InteractionHand hand, BlockHitResult hit, BlockState before, Item item) {
            this.tick = tick;
            this.hand = hand;
            this.hit = hit;
            this.before = before;
            this.item = item;
        }
    }

    public static boolean isRecording(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static int framesRecorded(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? -1 : session.count;
    }

    public static boolean start(ServerPlayer player) {
        if (isRecording(player)) {
            return false;
        }
        int frames = Math.min(EchoRecording.MAX_FRAMES, CommonConfig.ECHO_RECORD_SECONDS.get() * 20);
        SESSIONS.put(player.getUUID(), new Session(player.level().dimension(), player.position(), frames));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.sendSystemMessage(Component.translatable("mnemolith.echo.recording_start", frames / 20), true);
        Mnemolith.LOGGER.info("Mnemolith echo recording start player={} frames={}", player.getGameProfile().name(), frames);
        return true;
    }

    /** Called from the player tick (post). Samples one frame and settles this tick's clicks. */
    public static void tick(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (!player.level().dimension().equals(session.dimension) || !player.isAlive()) {
            finish(player, "dimension");
            return;
        }
        int flags = 0;
        if (player.isShiftKeyDown()) {
            flags |= EchoRecording.FLAG_SNEAK;
        }
        if (player.isSprinting()) {
            flags |= EchoRecording.FLAG_SPRINT;
        }
        if (player.swinging && player.swingTime == 0) {
            flags |= EchoRecording.FLAG_SWING;
            if (player.swingingArm == InteractionHand.OFF_HAND) {
                flags |= EchoRecording.FLAG_SWING_OFFHAND;
            }
        }
        if (player.onGround()) {
            flags |= EchoRecording.FLAG_GROUND;
        }
        EchoRecording.writeFrame(session.frames, session.origin, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.getYHeadRot(), flags);
        session.count++;
        settle(player, session);
        int left = session.maxFrames - session.count;
        if (left <= 0) {
            finish(player, "time");
        } else if (session.count % 10 == 0) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.recording_left", (left + 19) / 20), true);
        }
    }

    private static void settle(ServerPlayer player, Session session) {
        for (Pending pending : session.pending) {
            BlockPos pos = pending.hit.getBlockPos();
            if (pending.placed) {
                add(session, EchoAction.of(pending.tick, EchoAction.Kind.PLACE, pending.hit, pending.hand == InteractionHand.OFF_HAND, pending.before.getBlock(), pending.item));
            } else {
                BlockState after = player.level().getBlockState(pos);
                if (after != pending.before && !pending.before.isAir()) {
                    add(session, EchoAction.of(pending.tick, EchoAction.Kind.USE, pending.hit, pending.hand == InteractionHand.OFF_HAND, pending.before.getBlock(), null));
                }
            }
        }
        session.pending.clear();
    }

    private static void add(Session session, EchoAction action) {
        if (session.actions.size() < EchoRecording.MAX_ACTIONS) {
            session.actions.add(action);
        }
    }

    public static void onBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        BlockHitResult hit = new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        add(session, EchoAction.of(session.count, EchoAction.Kind.BREAK, hit, false, state.getBlock(), null));
    }

    public static void onRightClickBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        BlockState before = player.level().getBlockState(hit.getBlockPos());
        session.pending.add(new Pending(session.count, hand, hit, before, player.getItemInHand(hand).getItem()));
    }

    public static void onPlace(ServerPlayer player, BlockState placed) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.pending.isEmpty()) {
            return;
        }
        Pending last = session.pending.get(session.pending.size() - 1);
        last.placed = true;
    }

    /** Ends a session and hands the player a filled recording, or the blank slip back when almost nothing was recorded. */
    public static @Nullable ItemStack finish(ServerPlayer player, String reason) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return null;
        }
        settle(player, session);
        ItemStack result;
        if (session.count < 20) {
            result = new ItemStack(ModItems.ECHO_SLIP.get());
            player.sendSystemMessage(Component.translatable("mnemolith.echo.recording_too_short"), true);
        } else {
            byte[] frames = Arrays.copyOf(session.frames.array(), session.count * EchoRecording.FRAME_BYTES);
            EchoRecording recording = new EchoRecording(player.getUUID(), player.getGameProfile().name(), session.dimension, session.origin, frames, session.actions);
            result = new ItemStack(ModItems.ECHO_RECORDING.get());
            result.set(ModDataComponents.ECHO_RECORDING.get(), recording);
            player.sendSystemMessage(Component.translatable("mnemolith.echo.recording_done", recording.seconds(), recording.actions().size()), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.1F);
        }
        Mnemolith.LOGGER.info("Mnemolith echo recording end player={} frames={} actions={} reason={}", player.getGameProfile().name(), session.count, session.actions.size(), reason);
        ItemStack given = result.copy();
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
        return given;
    }

    /** Server stopped: forget unsaved sessions (players were already handed their recordings on logout). */
    public static void clearAll() {
        SESSIONS.clear();
    }
}
