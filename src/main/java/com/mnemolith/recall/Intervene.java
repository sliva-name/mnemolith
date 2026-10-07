package com.mnemolith.recall;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.entity.ModEffects;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Stage 4. After the Scar, one fragment can change the tag of a memory that is already written.
 * The new tag is the temper of the nearest scarred echo you own, or silence when you have none.
 * Pressure is scored again, and a residue wearing the old tag nearby takes the new one.
 * A muted chunk does not stop this: intervention cuts through silence. Nothing here writes chat.
 */
public final class Intervene {
    /** Graftable tags, in the order a second pass walks when the chosen temper is already the imprint's tag. */
    private static final ImprintTag[] CYCLE = {
            ImprintTag.SILENCE, ImprintTag.DEATH, ImprintTag.FIRE, ImprintTag.FALL, ImprintTag.EXPLOSION,
            ImprintTag.LIGHTNING, ImprintTag.PORTAL, ImprintTag.SCULK
    };

    private Intervene() {}

    public static boolean enabled() {
        return CommonConfig.INTERVENE_ENABLED.get();
    }

    /**
     * The item path. Fake players, spectators and the unrecorded effect do not rewrite.
     * A claim mod cancels the right-click before this runs. Returns whether the fragment should be consumed.
     */
    public static boolean tryUse(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!enabled() || refuses(player)) {
            return false;
        }
        return rewrite(player, level, pos);
    }

    /** Spectators, fake players, and a player the world is not writing. */
    public static boolean refuses(ServerPlayer player) {
        return player.isSpectator() || player.hasEffect(ModEffects.UNRECORDED) || player instanceof FakePlayer;
    }

    /**
     * Rewrites the loudest imprint within range of {@code pos}. Does not check {@link #refuses}: the QA suite calls
     * this directly, the way {@link UseMemory#offer} bypasses the player tick.
     */
    public static boolean rewrite(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return false;
        }
        int range = Math.max(1, CommonConfig.INTERVENE_RANGE.get());
        Found found = loudest(level, pos, range, CommonConfig.INTERVENE_ONCE.get());
        if (found == null) {
            return false;
        }
        ImprintTag next = nextTag(found.imprint.tag(), temperNear(player, level, pos, range));
        Imprint rewritten = found.imprint.rewritten(next);
        if (!found.memory.replaceImprint(found.imprint, rewritten)) {
            return false;
        }
        int spike = CommonConfig.INTERVENE_INSTABILITY.get();
        if (spike > 0) {
            found.memory.addInstability(spike, CommonConfig.PRESSURE_SOFT_CAP.get());
        }
        MemoryPressure.recompute(found.chunk, found.memory);
        retint(level, found.imprint.origin(), found.imprint.tag(), next, range);
        level.playSound(null, found.imprint.origin(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS, 0.7F, 0.5F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                found.imprint.origin().getX() + 0.5D,
                found.imprint.origin().getY() + 0.5D,
                found.imprint.origin().getZ() + 0.5D,
                24, 0.4D, 0.6D, 0.4D, 0.04D);
        MemoryFx.write(level, found.imprint.origin());
        Mnemolith.LOGGER.debug("Mnemolith imprint rewritten {} -> {} at {}",
                found.imprint.tag().getSerializedName(), next.getSerializedName(), found.imprint.origin().toShortString());
        return true;
    }

    private static @Nullable Found loudest(ServerLevel level, BlockPos pos, int range, boolean once) {
        int radius = (range + 15) >> 4;
        int originX = pos.getX() >> 4;
        int originZ = pos.getZ() >> 4;
        double limit = (double) range * range;
        Found best = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = originX + dx;
                int chunkZ = originZ + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                if (memory == null) {
                    continue;
                }
                for (int index = 0; index < memory.imprintCount(); index++) {
                    Imprint imprint = memory.imprintAt(index);
                    if (once && imprint.rewritten()) {
                        continue;
                    }
                    if (imprint.origin().distSqr(pos) > limit) {
                        continue;
                    }
                    if (best == null || louder(imprint, best.imprint)) {
                        best = new Found(chunk, memory, imprint);
                    }
                }
            }
        }
        return best;
    }

    /** Higher pressure first, then the newer write. */
    private static boolean louder(Imprint candidate, Imprint current) {
        int pressure = Integer.compare(candidate.pressureContribution(), current.pressureContribution());
        if (pressure != 0) {
            return pressure > 0;
        }
        return candidate.writtenAt() > current.writtenAt();
    }

    /** Nearest scarred echo this player owns that already carries a graft, or null. */
    private static @Nullable Temper temperNear(ServerPlayer player, ServerLevel level, BlockPos pos, int range) {
        AABB box = new AABB(pos).inflate(range);
        EchoEntity nearest = null;
        double best = (double) range * range + 1.0D;
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, box, echo -> player.getUUID().equals(echo.ownerId())
                && echo.scarred()
                && echo.graftTemper() != null)) {
            double distance = echo.blockPosition().distSqr(pos);
            if (distance < best) {
                best = distance;
                nearest = echo;
            }
        }
        return nearest == null ? null : nearest.graftTemper();
    }

    private static ImprintTag nextTag(ImprintTag current, @Nullable Temper temper) {
        ImprintTag wanted = temper == null ? ImprintTag.SILENCE : temper.tag();
        if (wanted != current) {
            return wanted;
        }
        for (int index = 0; index < CYCLE.length; index++) {
            if (CYCLE[index] == current) {
                return CYCLE[(index + 1) % CYCLE.length];
            }
        }
        return ImprintTag.SILENCE;
    }

    /** Residues wearing the old tag, close to where it was written, take the new temper. A washed lie does not. */
    private static void retint(ServerLevel level, BlockPos origin, ImprintTag previous, ImprintTag next, int range) {
        AABB box = new AABB(origin).inflate(Math.max(range, 12));
        for (ResidueEntity residue : level.getEntitiesOfClass(ResidueEntity.class, box, entity -> entity.tag() == previous && !entity.washed())) {
            residue.retint(next);
        }
    }

    private record Found(LevelChunk chunk, ChunkMemory memory, Imprint imprint) {}
}
