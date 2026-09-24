package com.mnemolith.client.particle;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.ClientConfig;
import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ChunkPos;

/** Client particles. Dedicated servers do not load this class. */
public final class ClientParticles {
    private ClientParticles() {}

    public static void init() {
        Mnemolith.LOGGER.debug("Mnemolith client particles ready ({})", Minecraft.class.getSimpleName());
    }

    public static void shimmer(LocalPlayer player, ChunkPos chunk) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        double density = ClientConfig.PARTICLE_DENSITY.get();
        int count = (int) Math.round(ImprintConstants.LENS_PARTICLES_PER_CHUNK * density);
        double x = chunk.getMiddleBlockX() + 0.5D;
        double z = chunk.getMiddleBlockZ() + 0.5D;
        double y = player.getY() + 1.0D;
        for (int i = 0; i < count; i++) {
            Minecraft.getInstance().level.addParticle(ParticleTypes.SCULK_SOUL, x, y, z, 0.0D, 0.02D, 0.0D);
        }
    }
}
