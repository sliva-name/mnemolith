package com.mnemolith.imprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;

import com.mnemolith.audio.ModSounds;

/** Writes, extracts, and spikes chunk memory. Callers are world events, items, and the smoke command. */
public final class ImprintWriter {
    private ImprintWriter() {}

    public static boolean tryWrite(ServerLevel level, BlockPos pos, ImprintTag tag, @Nullable UUID player, boolean throttled) {
        // write() runs the same mute and debounce checks as acceptsThrottled(), so no separate pre-check here.
        return write(level, pos, List.of(tag), player, throttled);
    }

    /** False when a muted chunk or the build/redstone pause would drop the write. Does not create memory. */
    public static boolean acceptsThrottled(ServerLevel level, BlockPos pos) {
        if (!CommonConfig.WRITE_IMPRINTS.get() || LoadedChunkMemory.isMuted(level, pos)) {
            return false;
        }
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        if (memory == null) {
            return true;
        }
        return memory.acceptsThrottledWrite(level.getGameTime(), CommonConfig.WRITE_DEBOUNCE_TICKS.get());
    }

    public static boolean write(ServerLevel level, BlockPos pos, List<ImprintTag> tags, @Nullable UUID player, boolean throttled) {
        if (!CommonConfig.WRITE_IMPRINTS.get() || tags.isEmpty()) {
            return false;
        }
        if (LoadedChunkMemory.isMuted(level, pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        long now = level.getGameTime();
        ChunkMemory existing = LoadedChunkMemory.existing(chunk);
        if (throttled && existing != null && !existing.acceptsThrottledWrite(now, CommonConfig.WRITE_DEBOUNCE_TICKS.get())) {
            return false;
        }
        ChunkMemory memory = existing != null ? existing : LoadedChunkMemory.getOrCreate(chunk);
        for (ImprintTag tag : tags) {
            int intensity = intensityFor(tag);
            Imprint imprint = new Imprint(tag, intensity, pos.immutable(), Optional.ofNullable(player), Imprint.contextHash(tag, pos, now), now);
            memory.addImprint(imprint, CommonConfig.MAX_IMPRINTS_PER_CHUNK.get());
        }
        if (throttled) {
            memory.markWritten(now);
        }
        PressureBand band = MemoryPressure.recompute(chunk, memory);
        for (ImprintTag tag : tags) {
            Mnemolith.LOGGER.debug(
                    "Mnemolith imprint {} at {},{},{} pressure={} band={}",
                    tag.getSerializedName(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    memory.cachedPressure(),
                    band);
        }
        level.playSound(null, pos, ModSounds.IMPRINT_WRITE.get(), SoundSource.BLOCKS, 0.6F, 1.2F);
        MemoryFx.write(level, pos);
        return true;
    }

    public static int spike(ServerLevel level, BlockPos pos, int amount) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
        if (amount > 0) {
            memory.addInstability(amount, CommonConfig.PRESSURE_SOFT_CAP.get());
            MemoryPressure.recompute(chunk, memory);
        }
        Mnemolith.LOGGER.info(
                "Mnemolith instability spike amount={} pressure={} band={}",
                amount,
                memory.cachedPressure(),
                MemoryPressure.band(memory.cachedPressure()));
        return memory.cachedPressure();
    }

    public static Optional<Imprint> extract(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || memory.imprintCount() == 0) {
            return memory != null ? thisOrNeighbors(level, pos, player, memory) : Optional.empty();
        }
        Optional<Imprint> removed = takeHighest(chunk, memory);
        if (removed.isEmpty()) {
            return Optional.empty();
        }
        giveSlip(level, pos, player, removed.get());
        return removed;
    }

    private static Optional<Imprint> thisOrNeighbors(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, ChunkMemory origin) {
        if (origin.strataCount() <= 0 || player == null) {
            return Optional.empty();
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int chunkX = (pos.getX() >> 4) + dx;
                int chunkZ = (pos.getZ() >> 4) + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                BlockPos neighbor = new BlockPos(chunkX << 4, pos.getY(), chunkZ << 4);
                LevelChunk chunk = level.getChunkAt(neighbor);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                if (memory == null || memory.imprintCount() == 0) {
                    continue;
                }
                Optional<Imprint> removed = takeHighest(chunk, memory);
                if (removed.isEmpty()) {
                    continue;
                }
                giveSlip(level, neighbor, player, removed.get());
                Mnemolith.LOGGER.info("Mnemolith extract reach at {},{},{}", neighbor.getX(), neighbor.getY(), neighbor.getZ());
                return removed;
            }
        }
        return Optional.empty();
    }

    /** Removes the strongest imprint and marks the chunk archival. Empty when nothing could be removed. */
    private static Optional<Imprint> takeHighest(LevelChunk chunk, ChunkMemory memory) {
        Optional<Imprint> removed = memory.removeHighest();
        if (removed.isPresent()) {
            memory.setArchival(true);
            MemoryPressure.recompute(chunk, memory);
        }
        return removed;
    }

    /** Hands the slip to the player (if any), notes the tag, then plays the extract sound and particles. */
    private static void giveSlip(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, Imprint imprint) {
        if (player != null) {
            ItemStack slip = ImprintSlips.of(imprint);
            if (!player.getInventory().add(slip)) {
                player.drop(slip, false);
            }
            DiscoveryNotes.noteTag(player, imprint.tag());
        }
        level.playSound(null, pos, ModSounds.EXTRACT.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        MemoryFx.extract(level, pos);
    }

    public static int intensityFor(ImprintTag tag) {
        int intensity = switch (tag) {
            case DEATH -> ImprintConstants.DEATH_INTENSITY;
            case EXPLOSION -> ImprintConstants.EXPLOSION_INTENSITY;
            case FALL -> ImprintConstants.FALL_INTENSITY;
            case FIRE -> ImprintConstants.FIRE_INTENSITY;
            case SILENCE -> ImprintConstants.SILENCE_INTENSITY;
            case PLAYER -> ImprintConstants.PLAYER_INTENSITY;
            case BUILD -> ImprintConstants.BUILD_INTENSITY;
            case REDSTONE -> ImprintConstants.REDSTONE_INTENSITY;
            case PATH -> ImprintConstants.PATH_INTENSITY;
        };
        return Math.max(ImprintConstants.INTENSITY_MIN, Math.min(ImprintConstants.INTENSITY_MAX, intensity));
    }

    public static List<ImprintTag> witnessedTags(ServerLevel level, BlockPos pos, List<ImprintTag> tags) {
        if (!tags.contains(ImprintTag.DEATH) || tags.contains(ImprintTag.SILENCE)) {
            return tags;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= ImprintConstants.WITNESS_RANGE_SQR) {
                return tags;
            }
        }
        List<ImprintTag> withSilence = new ArrayList<>(tags);
        withSilence.add(ImprintTag.SILENCE);
        return withSilence;
    }
}
