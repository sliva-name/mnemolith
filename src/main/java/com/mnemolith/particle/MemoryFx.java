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

    public static void write(ServerLevel level, BlockPos pos) {
        burst(level, ModParticles.IMPRINT_SHIMMER.get(), pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D, 4, 0.25D);
    }

    public static void extract(ServerLevel level, BlockPos pos) {
        burst(level, ModParticles.IMPRINT_EXTRACT.get(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 10, 0.35D);
        Mnemolith.LOGGER.info("Mnemolith fx extract at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
    }

    public static void composeSuccess(ServerLevel level, BlockPos pos) {
        burst(level, ModParticles.COMPOSE_SUCCESS.get(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 12, 0.4D);
        Mnemolith.LOGGER.info("Mnemolith fx compose_success at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
    }

    public static void composeFail(ServerLevel level, BlockPos pos) {
        burst(level, ModParticles.COMPOSE_FAIL.get(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 10, 0.3D);
        Mnemolith.LOGGER.info("Mnemolith fx compose_fail at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
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
        Mnemolith.LOGGER.info("Mnemolith fx pressure at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
    }

    public static void mute(ServerLevel level, BlockPos pos) {
        burst(level, ModParticles.MUTE_HAZE.get(), pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 8, 0.45D);
        Mnemolith.LOGGER.info("Mnemolith fx mute at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
    }

    public static void mob(ServerLevel level, SimpleParticleType type, double x, double y, double z, int count) {
        burst(level, type, x, y, z, count, 0.25D);
    }

    private static void burst(ServerLevel level, SimpleParticleType type, double x, double y, double z, int count, double spread) {
        level.sendParticles(type, x, y, z, count, spread, spread * 0.6D, spread, 0.02D);
    }
}
