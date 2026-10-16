package com.mnemolith.particle;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

/**
 * Edge-triggered bursts. Each call sends one vanilla level-particles packet group.
 * Nothing here runs on a tick, and this class does not touch client render types.
 */
public final class MemoryFx {
    private MemoryFx() {}

    /** The written motes of the shared language: settling downward (the write sound is played by the writer). */
    public static void write(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 4; i++) {
            double ox = (level.getRandom().nextDouble() - 0.5D) * 0.6D;
            double oz = (level.getRandom().nextDouble() - 0.5D) * 0.6D;
            level.sendParticles(ModParticles.MEMORY_WRITTEN.get(), pos.getX() + 0.5D + ox, pos.getY() + 1.1D, pos.getZ() + 0.5D + oz, 0, 0.0D, -1.0D, 0.0D, 0.03D);
        }
    }

    public static void extract(ServerLevel level, BlockPos pos) {
        burst(level, ModParticles.IMPRINT_EXTRACT.get(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 10, 0.35D);
        Mnemolith.LOGGER.debug("Mnemolith fx extract at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
    }

    public static void composeSuccess(ServerLevel level, BlockPos pos) {
        burst(level, ModParticles.COMPOSE_SUCCESS.get(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 12, 0.4D);
        Mnemolith.LOGGER.debug("Mnemolith fx compose_success at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
    }

    public static void composeFail(ServerLevel level, BlockPos pos) {
        burst(level, ModParticles.COMPOSE_FAIL.get(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 10, 0.3D);
        Mnemolith.LOGGER.debug("Mnemolith fx compose_fail at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
    }

    /** Formula landing burst. The compose path already logs its own line. */
    public static void landing(ServerLevel level, double x, double y, double z) {
        burst(level, ModParticles.COMPOSE_SUCCESS.get(), x, y, z, 6, 0.35D);
    }

    public static void pressure(ServerLevel level, BlockPos pos) {
        double y = pos.getY() + 0.3D;
        for (int step = 0; step < 8; step++) {
            double angle = step * Math.PI / 4.0D;
            level.sendParticles(
                    ModParticles.PRESSURE_WARN.get(),
                    pos.getX() + 0.5D + Math.cos(angle) * 1.5D,
                    y,
                    pos.getZ() + 0.5D + Math.sin(angle) * 1.5D,
                    1,
                    0.02D,
                    0.08D,
                    0.02D,
                    0.01D);
        }
        level.playSound(null, pos, ModSounds.PRESSURE_WARN.get(), SoundSource.BLOCKS, 0.7F, 0.8F);
        Mnemolith.LOGGER.debug("Mnemolith fx pressure at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
    }

    /** A mute stone holding its chunk: the shared kept cue. */
    public static void mute(ServerLevel level, BlockPos pos) {
        MemoryFeel.cue(level, MemoryFeel.Kind.KEPT, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8);
        Mnemolith.LOGGER.debug("Mnemolith fx mute at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
    }

    public static void mob(ServerLevel level, SimpleParticleType type, double x, double y, double z, int count) {
        burst(level, type, x, y, z, count, 0.25D);
    }

    private static void burst(ServerLevel level, SimpleParticleType type, double x, double y, double z, int count, double spread) {
        level.sendParticles(type, x, y, z, count, spread, spread * 0.6D, spread, 0.02D);
    }
}
