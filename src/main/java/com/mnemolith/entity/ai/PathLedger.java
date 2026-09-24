package com.mnemolith.entity.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ring buffer of recent player steps, plus a coarser path imprint on the chunk.
 * Striders consume the buffer. Imprint origins stay on the chunk.
 */
public final class PathLedger {
    private static final Map<UUID, Deque<BlockPos>> PATHS = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_STEP = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_WRITE = new HashMap<>();
    private static final int CAP = 16;
    private static final double STEP_SQR = 64.0D;
    private static final double WRITE_SQR = 144.0D;

    private PathLedger() {}

    public static void note(ServerPlayer player) {
        BlockPos now = player.blockPosition().immutable();
        UUID id = player.getUUID();
        BlockPos last = LAST_STEP.get(id);
        if (last != null && last.distSqr(now) < STEP_SQR) {
            return;
        }
        LAST_STEP.put(id, now);
        Deque<BlockPos> path = PATHS.computeIfAbsent(id, key -> new ArrayDeque<>());
        path.addLast(now);
        while (path.size() > CAP) {
            path.removeFirst();
        }
        BlockPos lastWrite = LAST_WRITE.get(id);
        if (lastWrite == null || lastWrite.distSqr(now) >= WRITE_SQR) {
            LAST_WRITE.put(id, now);
            ImprintWriter.tryWrite((ServerLevel) player.level(), now, ImprintTag.PATH, id, false);
        }
    }

    public static @Nullable BlockPos peek(UUID id) {
        Deque<BlockPos> path = PATHS.get(id);
        if (path == null || path.isEmpty()) {
            return null;
        }
        return path.peekFirst();
    }

    public static void consume(UUID id, BlockPos pos) {
        Deque<BlockPos> path = PATHS.get(id);
        if (path == null || path.isEmpty()) {
            return;
        }
        if (path.peekFirst().equals(pos)) {
            path.removeFirst();
        }
    }

    public static List<UUID> owners() {
        return List.copyOf(PATHS.keySet());
    }

    public static @Nullable BlockPos nearestImprint(ServerLevel level, BlockPos from, @Nullable BlockPos skip) {
        int originX = from.getX() >> 4;
        int originZ = from.getZ() >> 4;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int chunkX = originX + dx;
                int chunkZ = originZ + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkMemory memory = LoadedChunkMemory.existing(level.getChunk(chunkX, chunkZ));
                if (memory == null) {
                    continue;
                }
                for (Imprint imprint : memory.imprintsCopy()) {
                    if (imprint.tag() != ImprintTag.PATH && imprint.tag() != ImprintTag.PLAYER) {
                        continue;
                    }
                    if (skip != null && skip.equals(imprint.origin())) {
                        continue;
                    }
                    double dist = imprint.origin().distSqr(from);
                    if (dist > 4.0D && dist < bestDist) {
                        bestDist = dist;
                        best = imprint.origin();
                    }
                }
            }
        }
        return best;
    }

    public static @Nullable BlockPos higherPressure(ServerLevel level, BlockPos from) {
        int originX = from.getX() >> 4;
        int originZ = from.getZ() >> 4;
        ChunkMemory here = null;
        if (level.getChunkSource().hasChunk(originX, originZ)) {
            here = LoadedChunkMemory.existing(level.getChunk(originX, originZ));
        }
        int current = here == null ? 0 : here.cachedPressure();
        int bestPressure = current;
        BlockPos best = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int chunkX = originX + dx;
                int chunkZ = originZ + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                var chunk = level.getChunk(chunkX, chunkZ);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                int pressure = memory == null ? 0 : memory.cachedPressure();
                if (pressure > bestPressure) {
                    bestPressure = pressure;
                    best = new BlockPos(chunk.getPos().getMiddleBlockX(), from.getY(), chunk.getPos().getMiddleBlockZ());
                }
            }
        }
        return best;
    }
}
