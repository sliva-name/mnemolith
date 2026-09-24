package com.mnemolith.client.particle;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.ClientConfig;
import com.mnemolith.particle.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client particles. Dedicated servers do not load this class. */
public final class ClientParticles {
    private ClientParticles() {}

    public static void init() {
        Mnemolith.LOGGER.debug("Mnemolith client particles ready ({})", Minecraft.class.getSimpleName());
    }

    public static void register(RegisterParticleProvidersEvent event) {
        sprite(event, ModParticles.IMPRINT_SHIMMER.get(), 0x6E7CC4, 0.14F, 18);
        sprite(event, ModParticles.IMPRINT_EXTRACT.get(), 0xE6DCC8, 0.16F, 16);
        sprite(event, ModParticles.COMPOSE_SUCCESS.get(), 0x3E8E7E, 0.18F, 20);
        sprite(event, ModParticles.COMPOSE_FAIL.get(), 0xE07A4A, 0.16F, 16);
        sprite(event, ModParticles.PRESSURE_WARN.get(), 0xE07A4A, 0.22F, 14);
        sprite(event, ModParticles.MUTE_HAZE.get(), 0x3D4A8A, 0.28F, 28);
        sprite(event, ModParticles.STRIDER_TRAIL.get(), 0x7ED0C2, 0.16F, 14);
        sprite(event, ModParticles.ARCHIVIST_SNATCH.get(), 0xE6DCC8, 0.18F, 16);
        sprite(event, ModParticles.REPLICANT_TELEGRAPH.get(), 0xC45A6A, 0.18F, 16);
    }

    public static void shimmer(LocalPlayer player, ChunkPos chunk) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        double density = ClientConfig.PARTICLE_DENSITY.get();
        if (!ClientConfig.IMPRINT_PARTICLES.get() || density <= 0.0D) {
            return;
        }
        int count = (int) Math.round(2.0D * density);
        if (count <= 0) {
            return;
        }
        RandomSource random = player.getRandom();
        double x = chunk.getMiddleBlockX() + 0.5D;
        double z = chunk.getMiddleBlockZ() + 0.5D;
        double y = player.getY() + 1.2D;
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5D) * 6.0D;
            double oz = (random.nextDouble() - 0.5D) * 6.0D;
            Minecraft.getInstance().level.addParticle(ModParticles.IMPRINT_SHIMMER.get(), x + ox, y, z + oz, 0.0D, 0.02D, 0.0D);
        }
    }

    private static void sprite(RegisterParticleProvidersEvent event, SimpleParticleType type, int rgb, float size, int life) {
        event.registerSpriteSet(type, sprites -> (options, level, x, y, z, dx, dy, dz, random) ->
                MemoryParticle.spawn(level, x, y, z, dx, dy, dz, sprites, rgb, size, life, random));
    }
}
