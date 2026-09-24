package com.mnemolith.imprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.content.ModItems;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;

import com.mnemolith.audio.ModSounds;

/** Writes, extracts, and spikes chunk memory. Callers are world events, items, and the smoke command. */
public final class ImprintWriter {
    private ImprintWriter() {}

    public static boolean tryWrite(ServerLevel level, BlockPos pos, ImprintTag tag, @Nullable UUID player, boolean throttled) {
        return write(level, pos, List.of(tag), player, throttled);
    }

    public static boolean write(ServerLevel level, BlockPos pos, List<ImprintTag> tags, @Nullable UUID player, boolean throttled) {
        if (!CommonConfig.WRITE_IMPRINTS.get() || tags.isEmpty()) {
            return false;
        }
        if (LoadedChunkMemory.isMuted(level, pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
        long now = level.getGameTime();
        if (throttled && !memory.acceptsThrottledWrite(now, CommonConfig.WRITE_DEBOUNCE_TICKS.get())) {
            return false;
        }
        boolean wrote = false;
        for (ImprintTag tag : tags) {
            int intensity = intensityFor(tag);
            Imprint imprint = new Imprint(tag, intensity, pos.immutable(), Optional.ofNullable(player), Imprint.contextHash(tag, pos, now), now);
            memory.addImprint(imprint, CommonConfig.MAX_IMPRINTS_PER_CHUNK.get());
            wrote = true;
        }
        if (!wrote) {
            return false;
        }
        if (throttled) {
            memory.markWritten(now);
        }
        PressureBand band = MemoryPressure.recompute(chunk, memory);
        for (ImprintTag tag : tags) {
            Mnemolith.LOGGER.info(
                    "Mnemolith imprint {} at {},{},{} pressure={} band={}",
                    tag.getSerializedName(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    memory.cachedPressure(),
                    band);
        }
        level.playSound(null, pos, ModSounds.IMPRINT_WRITE.get(), SoundSource.BLOCKS, 0.6F, 1.2F);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, ImprintConstants.SERVER_PARTICLE_COUNT, 0.3D, 0.3D, 0.3D, 0.01D);
        return true;
    }

    public static void spike(ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 0) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
        memory.addInstability(amount, CommonConfig.PRESSURE_SOFT_CAP.get());
        MemoryPressure.recompute(chunk, memory);
    }

    public static Optional<Imprint> extract(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || memory.imprintCount() == 0) {
            return memory != null ? thisOrNeighbors(level, pos, player, memory) : Optional.empty();
        }
        Optional<Imprint> removed = memory.removeHighest();
        if (removed.isEmpty()) {
            return Optional.empty();
        }
        memory.setArchival(true);
        MemoryPressure.recompute(chunk, memory);
        Imprint imprint = removed.get();
        if (player != null) {
            ItemStack slip = new ItemStack(ModItems.IMPRINT_SLIP.get());
            slip.set(ModDataComponents.IMPRINT_CAST.get(), ImprintCast.from(imprint));
            if (!player.getInventory().add(slip)) {
                player.drop(slip, false);
            }
        }
        level.playSound(null, pos, ModSounds.EXTRACT.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, ImprintConstants.SERVER_PARTICLE_COUNT, 0.4D, 0.4D, 0.4D, 0.2D);
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
                Optional<Imprint> removed = memory.removeHighest();
                if (removed.isEmpty()) {
                    continue;
                }
                memory.setArchival(true);
                MemoryPressure.recompute(chunk, memory);
                giveSlip(level, neighbor, player, removed.get());
                Mnemolith.LOGGER.info("Mnemolith extract reach at {},{},{}", neighbor.getX(), neighbor.getY(), neighbor.getZ());
                return removed;
            }
        }
        return Optional.empty();
    }

    private static void giveSlip(ServerLevel level, BlockPos pos, ServerPlayer player, Imprint imprint) {
        ItemStack slip = new ItemStack(ModItems.IMPRINT_SLIP.get());
        slip.set(ModDataComponents.IMPRINT_CAST.get(), ImprintCast.from(imprint));
        if (!player.getInventory().add(slip)) {
            player.drop(slip, false);
        }
        level.playSound(null, pos, ModSounds.EXTRACT.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, ImprintConstants.SERVER_PARTICLE_COUNT, 0.4D, 0.4D, 0.4D, 0.2D);
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

    public static @Nullable UUID playerId(@Nullable Player player) {
        return player == null ? null : player.getUUID();
    }
}
