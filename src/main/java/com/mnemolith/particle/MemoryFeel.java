package com.mnemolith.particle;

import java.util.EnumMap;
import java.util.Map;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The shared language of memory ({@code docs/design/memory-feel.md}). Five event kinds, each with one particle family,
 * colour, motion and sound. Systems call {@link #cue} when the event happens (edge-triggered, never per tick).
 * Particles go through the client memory particle budget and toggles; sounds can be muted per client with
 * {@code memoryEventSounds}.
 */
public final class MemoryFeel {
    public enum Kind { WRITTEN, ERASED, KEPT, CAUGHT, FORGOTTEN }

    /** Cues sent per kind since start; QA reads it. Server thread only. */
    private static final Map<Kind, Integer> SENT = new EnumMap<>(Kind.class);
    private static BlockPos lastPos = BlockPos.ZERO;

    private MemoryFeel() {}

    /** One cue of {@code kind} at {@code pos}; {@code strength} 1–12 scales motes and volume, not colour. */
    public static void cue(ServerLevel level, Kind kind, BlockPos pos, int strength) {
        cue(level, kind, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, strength);
    }

    public static void cue(ServerLevel level, Kind kind, double x, double y, double z, int strength) {
        int n = Math.max(1, Math.min(12, strength));
        SimpleParticleType type = particle(kind);
        switch (kind) {
            case WRITTEN -> {
                // motes settle downward into the ground
                for (int i = 0; i < n; i++) {
                    double ox = (level.getRandom().nextDouble() - 0.5D) * 0.8D;
                    double oz = (level.getRandom().nextDouble() - 0.5D) * 0.8D;
                    level.sendParticles(type, x + ox, y + 0.6D, z + oz, 0, 0.0D, -1.0D, 0.0D, 0.03D);
                }
            }
            case ERASED -> {
                // motes rise and scatter
                for (int i = 0; i < n; i++) {
                    double ox = (level.getRandom().nextDouble() - 0.5D) * 0.6D;
                    double oz = (level.getRandom().nextDouble() - 0.5D) * 0.6D;
                    level.sendParticles(type, x + ox, y + 0.2D, z + oz, 0, ox, 1.0D, oz, 0.08D);
                }
            }
            case KEPT -> {
                // a ring that closes inward
                int ring = Math.max(6, n);
                for (int i = 0; i < ring; i++) {
                    double angle = i * Math.PI * 2.0D / ring;
                    double cx = Math.cos(angle);
                    double cz = Math.sin(angle);
                    level.sendParticles(type, x + cx * 1.2D, y + 0.4D, z + cz * 1.2D, 0, -cx, 0.0D, -cz, 0.04D);
                }
            }
            case CAUGHT -> {
                // motes arc toward the nearest player
                ServerPlayer near = (ServerPlayer) level.getNearestPlayer(x, y, z, 16.0D, false);
                double tx = near == null ? 0.0D : near.getX() - x;
                double ty = near == null ? 1.0D : near.getY() + 1.0D - y;
                double tz = near == null ? 0.0D : near.getZ() - z;
                double len = Math.max(0.001D, Math.sqrt(tx * tx + ty * ty + tz * tz));
                for (int i = 0; i < n; i++) {
                    level.sendParticles(type, x, y + 0.3D + i * 0.05D, z, 0, tx / len, ty / len + 0.3D, tz / len, 0.12D);
                }
            }
            case FORGOTTEN -> {
                // motes hang still, then vanish
                level.sendParticles(type, x, y + 0.5D, z, n, 0.5D, 0.4D, 0.5D, 0.0D);
            }
        }
        level.playSound(null, x, y, z, sound(kind), SoundSource.BLOCKS, 0.3F + 0.05F * n, 1.0F);
        SENT.merge(kind, 1, Integer::sum);
        lastPos = BlockPos.containing(x, y, z);
    }

    public static SimpleParticleType particle(Kind kind) {
        return switch (kind) {
            case WRITTEN -> ModParticles.MEMORY_WRITTEN.get();
            case ERASED -> ModParticles.MEMORY_ERASED.get();
            case KEPT -> ModParticles.MEMORY_KEPT.get();
            case CAUGHT -> ModParticles.MEMORY_CAUGHT.get();
            case FORGOTTEN -> ModParticles.MEMORY_FORGOTTEN.get();
        };
    }

    public static SoundEvent sound(Kind kind) {
        return switch (kind) {
            case WRITTEN -> ModSounds.MEMORY_WRITTEN.get();
            case ERASED -> ModSounds.MEMORY_ERASED.get();
            case KEPT -> ModSounds.MEMORY_KEPT.get();
            case CAUGHT -> ModSounds.MEMORY_CAUGHT.get();
            case FORGOTTEN -> ModSounds.MEMORY_FORGOTTEN.get();
        };
    }

    /** True for the shared memory sounds (the client toggle {@code memoryEventSounds} mutes exactly these). */
    public static boolean isMemorySound(Identifier id) {
        return id != null && Mnemolith.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("memory.");
    }

    public static int sent(Kind kind) {
        return SENT.getOrDefault(kind, 0);
    }

    public static BlockPos lastPos() {
        return lastPos;
    }
}
