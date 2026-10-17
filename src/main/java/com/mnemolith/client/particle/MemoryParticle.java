package com.mnemolith.client.particle;

import org.jspecify.annotations.Nullable;

import com.mnemolith.common.MemoryPalette;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;

/** One tinted, fullbright memory mote. The provider returns null when the budget rejects it. */
final class MemoryParticle extends SimpleAnimatedParticle {
    private com.mnemolith.particle.AfterEcho.Motion motion;
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

    /**
     * A mote that keeps its speed instead of easing to a stop: after-images use it so each mote climbs steadily and the
     * motes of one burst read as a rising column, not as sparkles hanging in place.
     */
    static @Nullable Particle spawnRising(ClientLevel level, double x, double y, double z, double dy, SpriteSet sprites, int rgb, float size, int life, RandomSource random) {
        Particle particle = spawn(level, x, y, z, 0.0D, dy, 0.0D, sprites, rgb, size, life, random);
        if (particle instanceof MemoryParticle mote) {
            mote.friction = 1.0F;
        }
        return particle;
    }

    /** An after-image mote: motion by the remembered kind (see {@code AfterEcho}). Keeps its speed (friction 1). */
    static @Nullable Particle spawnAfter(ClientLevel level, double x, double y, double z, com.mnemolith.imprint.ImprintTag tag, SpriteSet sprites, RandomSource random) {
        Particle particle = spawn(level, x, y, z, 0.0D, 0.0D, 0.0D, sprites, MemoryPalette.AFTERIMAGE, 0.2F, 80, random);
        if (!(particle instanceof MemoryParticle mote)) {
            return particle;
        }
        mote.motion = com.mnemolith.particle.AfterEcho.motionFor(tag);
        mote.friction = 1.0F;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        switch (mote.motion) {
            case COLUMN -> { mote.setParticleSpeed(0.0D, 0.018D, 0.0D); mote.lifetime = 80; }
            case RIPPLE -> { mote.setParticleSpeed(Math.cos(angle) * 0.04D, 0.003D, Math.sin(angle) * 0.04D); mote.lifetime = 60; mote.quadSize = 0.22F; }
            case SINK -> { mote.setParticleSpeed(0.0D, -0.013D, 0.0D); mote.lifetime = 90; }
            case GROUND -> { mote.setParticleSpeed(Math.cos(angle) * 0.008D, 0.0D, Math.sin(angle) * 0.008D); mote.lifetime = 55; mote.quadSize = 0.24F; }
            case OUTLINE -> { mote.setParticleSpeed(0.0D, 0.003D, 0.0D); mote.lifetime = 115; mote.quadSize = 0.34F; }
        }
        return mote;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.motion == com.mnemolith.particle.AfterEcho.Motion.OUTLINE) {
            // dissolving outline: holds its size, then shrinks and fades over the second half
            float t = (float) this.age / (float) this.lifetime;
            if (t > 0.5F) {
                this.quadSize *= 0.985F;
                this.alpha = Math.max(0.0F, 1.0F - (t - 0.5F) * 2.0F);
            }
        } else if (this.motion == com.mnemolith.particle.AfterEcho.Motion.GROUND) {
            this.alpha = Math.max(0.0F, 0.9F * (1.0F - (float) this.age / (float) this.lifetime));
        }
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
