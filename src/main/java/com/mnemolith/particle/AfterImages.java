package com.mnemolith.particle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.network.StrongMemoryPayload;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Memory feel, stage 3: the ambient cues ({@code docs/design/memory-feel.md}). Once a second per player the server
 * reads the band of the chunk underfoot (the cached pressure; nothing is recomputed or loaded).
 * <ul>
 * <li><b>Strong-memory hint:</b> stepping into a different chunk that is saturated or louder sends that player a
 * {@link StrongMemoryPayload}; the client breathes a faint edge tint while there and says "This place remembers." the
 * first time per chunk (client toggle {@code strongMemoryHint}).</li>
 * <li><b>After-images:</b> standing still for {@link #STILL_TICKS} in such a chunk shows that player alone up to
 * {@link #MAX_IMAGES} slow ghost columns at the origins of the loudest imprints, at most once per {@link #COOLDOWN}
 * (client toggle {@code memoryAfterImages}: the particle is not drawn).</li>
 * </ul>
 */
public final class AfterImages {
    public static final int PERIOD = 20;
    public static final int STILL_TICKS = 60;
    public static final int COOLDOWN = 60;
    public static final int MAX_IMAGES = 3;
    public static final int COLUMN = 6;
    /** Squared horizontal movement (blocks) still counted as standing still between two scans. */
    private static final double STILL_SQR = 0.3D * 0.3D;

    private static final Map<UUID, State> STATE = new HashMap<>();
    private static int imagesSent;
    private static int hintsSent;

    private AfterImages() {}

    /** What one scan did for a player. */
    public record Result(boolean hint, int images) {
        public static final Result NONE = new Result(false, 0);
    }

    private static final class State {
        double x;
        double z;
        long stillSince;
        long lastImages = Long.MIN_VALUE / 2;
        long chunk = Long.MIN_VALUE;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator()) {
                    scan(level, player, level.getGameTime());
                }
            }
        }
        if (STATE.size() > 256) {
            STATE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
    }

    /** One scan for {@code player} at {@code now}. Public for QA and perfqa. */
    public static Result scan(ServerLevel level, ServerPlayer player, long now) {
        State state = STATE.computeIfAbsent(player.getUUID(), id -> new State());
        double dx = player.getX() - state.x;
        double dz = player.getZ() - state.z;
        if (dx * dx + dz * dz > STILL_SQR || now < state.stillSince) {
            state.stillSince = now;
        }
        state.x = player.getX();
        state.z = player.getZ();
        BlockPos at = player.blockPosition();
        long key = ((long) (at.getX() >> 4) << 32) ^ ((at.getZ() >> 4) & 0xFFFFFFFFL) ^ ((long) level.dimension().identifier().hashCode() << 16);
        boolean entered = key != state.chunk;
        state.chunk = key;
        if (!level.hasChunkAt(at)) {
            return Result.NONE;
        }
        LevelChunk chunk = level.getChunkAt(at);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || MemoryPressure.band(memory.cachedPressure()).ordinal() < PressureBand.SATURATED.ordinal()) {
            return Result.NONE;
        }
        boolean hint = false;
        if (entered) {
            PacketDistributor.sendToPlayer(player, new StrongMemoryPayload(chunk.getPos().x(), chunk.getPos().z()));
            hintsSent++;
            hint = true;
        }
        int images = 0;
        if (now - state.stillSince >= STILL_TICKS && now - state.lastImages >= COOLDOWN) {
            images = show(level, player, memory);
            if (images > 0) {
                state.lastImages = now;
            }
        }
        return new Result(hint, images);
    }

    private static int show(ServerLevel level, ServerPlayer player, ChunkMemory memory) {
        List<Imprint> loudest = new ArrayList<>(memory.imprintsCopy());
        loudest.sort(Comparator.comparingInt(Imprint::intensity).reversed());
        int n = Math.min(MAX_IMAGES, loudest.size());
        for (int i = 0; i < n; i++) {
            BlockPos origin = loudest.get(i).origin();
            // a slow column of pale motes standing where the memory happened, sent to this player only
            level.sendParticles(player, ModParticles.MEMORY_AFTERIMAGE.get(), false, false, origin.getX() + 0.5D, origin.getY() + 1.0D,
                    origin.getZ() + 0.5D, COLUMN, 0.12D, 0.7D, 0.12D, 0.0D);
        }
        imagesSent += n;
        return n;
    }

    public static void forget(UUID player) {
        STATE.remove(player);
    }

    public static int imagesSent() {
        return imagesSent;
    }

    public static int hintsSent() {
        return hintsSent;
    }
}
