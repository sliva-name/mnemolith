package com.mnemolith.pressure;

import java.util.List;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Cost of silence (P3). Mute stones are almost free safety; too long or too dense mute accumulates void pressure.
 * At the warn band the area hushes once; at the max a Silence Mirror (Scar twin) spawns and pressure cools down.
 */
public final class SilenceCost {
    private SilenceCost() {}

    public static void onPlayerPulse(ServerLevel level, ServerPlayer player) {
        if (!CommonConfig.SILENCE_COST_ENABLED.get()) {
            return;
        }
        int interval = CommonConfig.SILENCE_COST_PULSE_TICKS.get();
        if (interval <= 0) {
            return;
        }
        long time = level.getGameTime();
        if ((time + (player.getId() & 31)) % interval != 0) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null) {
            return;
        }
        if (memory.hasMuteStone()) {
            pulseMuted(level, chunk, memory, player);
        } else {
            decay(level, chunk, memory);
        }
    }

    private static void pulseMuted(ServerLevel level, LevelChunk chunk, ChunkMemory memory, ServerPlayer player) {
        long time = level.getGameTime();
        if (memory.voidCooldownUntil() > time) {
            return;
        }
        int density = neighborMuteDensity(level, chunk.getPos().x(), chunk.getPos().z());
        density += Math.max(0, memory.muteStoneCount() - 1);
        int gain = CommonConfig.SILENCE_COST_PER_PULSE.get()
                + density * CommonConfig.SILENCE_COST_DENSITY_BONUS.get();
        if (gain <= 0) {
            return;
        }
        int max = CommonConfig.SILENCE_COST_SPAWN_AT.get();
        int next = Math.min(max, memory.voidPressure() + gain);
        memory.setVoidPressure(next);
        chunk.markUnsaved();

        int warnAt = CommonConfig.SILENCE_COST_WARN_AT.get();
        if (next >= warnAt && !memory.voidWarned()) {
            memory.setVoidWarned(true);
            warn(level, chunk, player);
        }
        if (CommonConfig.SILENCE_COST_BLEACH_RECORDINGS.get() && next >= warnAt) {
            bleachNearby(player, CommonConfig.SILENCE_COST_BLEACH_FRAMES.get());
        }
        if (next >= max) {
            spawnMirror(level, chunk, memory, player);
        }
    }

    private static void decay(ServerLevel level, LevelChunk chunk, ChunkMemory memory) {
        int decay = CommonConfig.SILENCE_COST_DECAY.get();
        if (decay <= 0 || memory.voidPressure() <= 0) {
            return;
        }
        int next = Math.max(0, memory.voidPressure() - decay);
        memory.setVoidPressure(next);
        if (next < CommonConfig.SILENCE_COST_WARN_AT.get()) {
            memory.setVoidWarned(false);
        }
        chunk.markUnsaved();
    }

    private static int neighborMuteDensity(ServerLevel level, int chunkX, int chunkZ) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int x = chunkX + dx;
                int z = chunkZ + dz;
                if (!level.getChunkSource().hasChunk(x, z)) {
                    continue;
                }
                ChunkMemory neighbor = LoadedChunkMemory.existing(level.getChunk(x, z));
                if (neighbor != null && neighbor.hasMuteStone()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void warn(ServerLevel level, LevelChunk chunk, ServerPlayer player) {
        BlockPos center = muteCenter(chunk, LoadedChunkMemory.existing(chunk));
        level.playSound(null, center, ModSounds.PRESSURE_WARN.get(), SoundSource.AMBIENT, 1.2F, 0.4F);
        level.playSound(null, center, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.35F, 1.6F);
        player.sendSystemMessage(Component.translatable("mnemolith.silence.warn"), true);
        Mnemolith.LOGGER.debug(
                "Mnemolith void pressure warning at chunk {} {} pressure={}",
                chunk.getPos().x(),
                chunk.getPos().z(),
                LoadedChunkMemory.existing(chunk) == null ? 0 : LoadedChunkMemory.existing(chunk).voidPressure());
    }

    private static void spawnMirror(ServerLevel level, LevelChunk chunk, ChunkMemory memory, ServerPlayer player) {
        BlockPos home = muteCenter(chunk, memory);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.getX(), home.getZ());
        BlockPos at = new BlockPos(home.getX(), y, home.getZ());
        ScarEntity mirror = null;
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            mirror = ModEntities.SILENCE_MIRROR.get().create(level, EntitySpawnReason.EVENT);
            if (mirror != null) {
                mirror.snapTo(at.getX() + 0.5D, at.getY() + 1.5D, at.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
                mirror.setup(1 << Temper.HUSHED.id(), 3, at);
                if (!level.addFreshEntity(mirror)) {
                    mirror = null;
                }
            }
        }
        memory.setVoidPressure(0);
        memory.setVoidWarned(false);
        memory.setVoidCooldownUntil(level.getGameTime() + CommonConfig.SILENCE_COST_COOLDOWN_TICKS.get());
        chunk.markUnsaved();
        level.playSound(null, at, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0F, 1.4F);
        player.sendSystemMessage(Component.translatable(
                mirror != null ? "mnemolith.silence.mirror" : "mnemolith.silence.mirror_site"), false);
        Mnemolith.LOGGER.info(
                "Mnemolith silence mirror at {} boss={}",
                at.toShortString(),
                mirror != null);
    }

    private static BlockPos muteCenter(LevelChunk chunk, ChunkMemory memory) {
        if (memory == null || memory.muteStonesCopy().isEmpty()) {
            int x = chunk.getPos().getMiddleBlockX();
            int z = chunk.getPos().getMiddleBlockZ();
            return new BlockPos(x, chunk.getMinY() + 64, z);
        }
        long sx = 0;
        long sy = 0;
        long sz = 0;
        List<BlockPos> stones = memory.muteStonesCopy();
        for (BlockPos pos : stones) {
            sx += pos.getX();
            sy += pos.getY();
            sz += pos.getZ();
        }
        int n = stones.size();
        return new BlockPos((int) (sx / n), (int) (sy / n), (int) (sz / n));
    }

    /** Shortens echo recordings in the player's inventory: the quiet bleaches unfinished takes. */
    private static void bleachNearby(ServerPlayer player, int frames) {
        if (frames <= 0) {
            return;
        }
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(ModItems.ECHO_RECORDING.get())) {
                continue;
            }
            EchoRecording recording = stack.get(ModDataComponents.ECHO_RECORDING.get());
            if (recording == null || recording.length() <= 0) {
                continue;
            }
            EchoRecording faded = recording.bleached(frames);
            if (faded.length() == recording.length()) {
                continue;
            }
            stack.set(ModDataComponents.ECHO_RECORDING.get(), faded);
            changed = true;
        }
        if (changed) {
            player.sendSystemMessage(Component.translatable("mnemolith.silence.bleach"), true);
        }
    }
}
