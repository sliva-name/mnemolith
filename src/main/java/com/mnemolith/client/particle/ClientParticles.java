package com.mnemolith.client.particle;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.common.MemoryPalette;
import com.mnemolith.particle.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.particle.Particle;
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
        sprite(event, ModParticles.IMPRINT_SHIMMER.get(), MemoryPalette.SHIMMER, 0.14F, 18);
        sprite(event, ModParticles.IMPRINT_EXTRACT.get(), MemoryPalette.BONE, 0.16F, 16);
        sprite(event, ModParticles.MEMORY_WRITTEN.get(), MemoryPalette.SHIMMER, 0.14F, 20);
        sprite(event, ModParticles.MEMORY_ERASED.get(), MemoryPalette.ERASED, 0.17F, 16);
        sprite(event, ModParticles.MEMORY_KEPT.get(), MemoryPalette.INDIGO, 0.22F, 26);
        sprite(event, ModParticles.MEMORY_CAUGHT.get(), MemoryPalette.PIGMENT, 0.15F, 18);
        sprite(event, ModParticles.MEMORY_FORGOTTEN.get(), MemoryPalette.FADED, 0.17F, 34);
        event.registerSpriteSet(ModParticles.MEMORY_AFTERIMAGE.get(), sprites -> (options, level, x, y, z, dx, dy, dz, random) -> {
            if (!ClientConfig.MEMORY_AFTER_IMAGES.get()) {
                return null;
            }
            // the x-velocity carries the tag code (see AfterEcho): motion and the faint sound follow the remembered kind
            com.mnemolith.imprint.ImprintTag tag = com.mnemolith.particle.AfterEcho.tagFor((float) dx);
            Particle particle = MemoryParticle.spawnAfter(level, x, y, z, tag, sprites, random);
            if (particle != null && ClientConfig.MEMORY_AFTER_ECHOES.get()) {
                AfterEchoAudio.hear(level, x, y, z, tag);
            }
            return particle;
        });
        event.registerSpriteSet(ModParticles.MEMORY_TRACE.get(), sprites -> (options, level, x, y, z, dx, dy, dz, random) -> {
            if (!ClientConfig.MEMORY_TRACES.get()) {
                return null;
            }
            // the x-velocity carries the loudest tag ordinal (-1 for none), the y-velocity the band ordinal,
            // and a z-velocity of 1 marks a fracture seam mote (finer than the cluster stains)
            return MemoryParticle.spawnTrace(level, x, y, z, Math.round((float) dx), Math.round((float) dy), dz > 0.5D, sprites, random);
        });
        sprite(event, ModParticles.COMPOSE_SUCCESS.get(), MemoryPalette.PIGMENT, 0.18F, 20);
        sprite(event, ModParticles.COMPOSE_FAIL.get(), MemoryPalette.EMBER, 0.16F, 16);
        sprite(event, ModParticles.PRESSURE_WARN.get(), MemoryPalette.EMBER, 0.22F, 14);
        sprite(event, ModParticles.MUTE_HAZE.get(), MemoryPalette.INDIGO, 0.28F, 28);
        sprite(event, ModParticles.STRIDER_TRAIL.get(), MemoryPalette.TRAIL, 0.16F, 14);
        sprite(event, ModParticles.ARCHIVIST_SNATCH.get(), MemoryPalette.BONE, 0.18F, 16);
        sprite(event, ModParticles.REPLICANT_TELEGRAPH.get(), MemoryPalette.TELEGRAPH, 0.18F, 16);
        for (com.mnemolith.echo.graft.Temper temper : com.mnemolith.echo.graft.Temper.values()) {
            sprite(event, com.mnemolith.echo.graft.EchoGrafts.particle(temper), temper.rgb(), 0.15F, 22);
        }
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
        double dx = x - player.getX();
        double dy = y - player.getY();
        double dz = z - player.getZ();
        if (dx * dx + dy * dy + dz * dz > 48.0D * 48.0D) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5D) * 6.0D;
            double oz = (random.nextDouble() - 0.5D) * 6.0D;
            Minecraft.getInstance().level.addParticle(ModParticles.IMPRINT_SHIMMER.get(), x + ox, y, z + oz, 0.0D, 0.02D, 0.0D);
        }
    }

    /** One faint distant replay per burst and kind; the particles only ever reach the viewer, so only they hear it. */
    private static final class AfterEchoAudio {
        private static final java.util.Map<Long, Long> LAST = new java.util.HashMap<>();

        static void hear(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z, com.mnemolith.imprint.ImprintTag tag) {
            long now = level.getGameTime();
            long key = net.minecraft.core.BlockPos.asLong((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)) * 31L + tag.ordinal();
            Long last = LAST.get(key);
            if (last != null && now - last < 25L) {
                return;
            }
            LAST.put(key, now);
            if (LAST.size() > 64) {
                LAST.clear();
            }
            net.minecraft.sounds.SoundEvent sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(com.mnemolith.Mnemolith.MOD_ID, com.mnemolith.particle.AfterEcho.soundName(tag)));
            if (sound != null) {
                level.playLocalSound(x, y, z, sound, net.minecraft.sounds.SoundSource.AMBIENT, 0.55F, 1.0F, false);
            }
        }
    }

    private static void sprite(RegisterParticleProvidersEvent event, SimpleParticleType type, int rgb, float size, int life) {
        event.registerSpriteSet(type, sprites -> (options, level, x, y, z, dx, dy, dz, random) ->
                MemoryParticle.spawn(level, x, y, z, dx, dy, dz, sprites, rgb, size, life, random));
    }
}
