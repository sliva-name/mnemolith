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
    private boolean traceFracture;
    private float traceAlpha = -1.0F;
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
        Particle particle = spawn(level, x, y, z, 0.0D, 0.0D, 0.0D, sprites, echoColour(tag), 0.2F, 80, random);
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

    /**
     * A visible-memory trace mote (see {@code MemoryTrace}): the band sets size, life, rise and alpha;
     * the loudest kind sets the colour and borrows its after-image motion, kept low over the ground.
     * Fracture motes pulse. {@code tagOrdinal} is the snapshot's loudest ordinal (-1 for none).
     */
    static @Nullable Particle spawnTrace(ClientLevel level, double x, double y, double z, int tagOrdinal, int bandOrdinal, boolean seam, SpriteSet sprites, RandomSource random) {
        com.mnemolith.pressure.PressureBand band = com.mnemolith.pressure.PressureBand.byOrdinal(bandOrdinal);
        com.mnemolith.particle.MemoryTrace.Style style = com.mnemolith.particle.MemoryTrace.style(band);
        if (style.motes() <= 0) {
            return null;
        }
        Particle particle = spawn(level, x, y, z, 0.0D, style.rise(), 0.0D, sprites,
                com.mnemolith.particle.MemoryTrace.colour(tagOrdinal), style.size(), style.life(), random);
        if (!(particle instanceof MemoryParticle mote)) {
            return particle;
        }
        if (seam) {
            mote.quadSize *= 0.85F;
        }
        mote.alpha = style.alpha();
        mote.traceAlpha = style.alpha();
        mote.traceFracture = band == com.mnemolith.pressure.PressureBand.FRACTURE;
        // traces hug the surface and linger: almost no drift, so a stain stays where the place put it
        mote.friction = 0.98F;
        com.mnemolith.imprint.ImprintTag tag = com.mnemolith.particle.MemoryTrace.tag(tagOrdinal);
        if (tag != null) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            switch (com.mnemolith.particle.AfterEcho.motionFor(tag)) {
                case COLUMN -> { mote.setParticleSpeed(Math.cos(angle) * 0.006D, style.rise() * 3.0D, Math.sin(angle) * 0.006D); mote.friction = 0.94F; }
                case RIPPLE -> mote.setParticleSpeed(Math.cos(angle) * 0.006D, style.rise() * 0.3D, Math.sin(angle) * 0.006D);
                case SINK -> mote.setParticleSpeed(0.0D, -0.012D, 0.0D);
                case GROUND -> mote.setParticleSpeed(Math.cos(angle) * 0.005D, 0.0D, Math.sin(angle) * 0.005D);
                case OUTLINE -> { mote.setParticleSpeed(0.0D, style.rise() * 0.3D, 0.0D); mote.quadSize *= 1.3F; }
            }
        }
        return mote;
    }

    /**
     * After-images wear the same colour language as traces and the lens glyph: the kind's glyph colour,
     * washed 35% toward the pale after-image tone so they stay ghostly and never read as fresh events.
     */
    static int echoColour(com.mnemolith.imprint.ImprintTag tag) {
        int glyph = com.mnemolith.common.MemoryGlyphs.colour(tag);
        if (glyph == 0) {
            return MemoryPalette.AFTERIMAGE;
        }
        int pale = MemoryPalette.AFTERIMAGE;
        int r = (int) (((glyph >> 16) & 0xFF) * 0.65F + ((pale >> 16) & 0xFF) * 0.35F);
        int g = (int) (((glyph >> 8) & 0xFF) * 0.65F + ((pale >> 8) & 0xFF) * 0.35F);
        int b = (int) ((glyph & 0xFF) * 0.65F + (pale & 0xFF) * 0.35F);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.traceAlpha >= 0.0F) {
            // stains fade in, linger, then dissolve; a fracture trace breathes on top of that
            float t = (float) this.age / (float) this.lifetime;
            float fade = Math.min(1.0F, t * 8.0F) * (t > 0.6F ? Math.max(0.12F, 1.0F - (t - 0.6F) * 2.2F) : 1.0F);
            this.alpha = this.traceAlpha * fade;
            if (this.traceFracture) {
                this.alpha *= 0.85F + 0.15F * (float) Math.sin(this.age * 0.45F);
            }
        }
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
