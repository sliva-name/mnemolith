package com.mnemolith.client.particle;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;

/** One tinted, fullbright memory mote. The provider returns null when the budget rejects it. */
final class MemoryParticle extends SimpleAnimatedParticle {
    private MemoryParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, int rgb, float size, int life) {
        super(level, x, y, z, sprites, 0.0F);
        this.setParticleSpeed(dx, dy, dz);
        this.lifetime = life;
        this.quadSize = size;
        this.hasPhysics = false;
        this.friction = 0.92F;
        this.setColor(rgb);
        this.setFadeColor(rgb);
        this.setSpriteFromAge(sprites);
    }

    static @Nullable Particle spawn(
            ClientLevel level,
            double x,
            double y,
            double z,
            double dx,
            double dy,
            double dz,
            SpriteSet sprites,
            int rgb,
            float size,
            int life,
            RandomSource random) {
        if (!MemoryFxBudget.allow(x, y, z, random)) {
            return null;
        }
        return new MemoryParticle(level, x, y, z, dx, dy, dz, sprites, rgb, size, life, random);
    }

    private MemoryParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double dx,
            double dy,
            double dz,
            SpriteSet sprites,
            int rgb,
            float size,
            int life,
            RandomSource random) {
        this(level, x, y, z, dx, dy, dz, sprites, rgb, size, life);
        if (random.nextBoolean()) {
            this.quadSize *= 0.85F;
        }
    }
}
