package com.mnemolith.content;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

import com.mojang.datafixers.util.Pair;

/**
 * Survival-facing pointers: nearest residue, fracture chunk, or chronicle observatory.
 * Used by the memory compass and the kin witness testimony.
 */
public final class MemoryNavigation {
    public static final TagKey<Structure> ON_OBSERVATORY_MAPS = TagKey.create(
            Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "on_observatory_explorer_maps"));

    /** How far (blocks) a residue may sit and still be considered. */
    public static final double RESIDUE_RANGE = 96.0D;
    /** Loaded-chunk Chebyshev radius for fracture search. */
    public static final int FRACTURE_CHUNK_RADIUS = 8;
    /** Structure-locator radius in chunks (same order as explorer maps). */
    public static final int OBSERVATORY_SEARCH_CHUNKS = 50;

    public enum Kind {
        RESIDUE,
        FRACTURE,
        OBSERVATORY
    }

    public record Target(Kind kind, BlockPos pos) {}

    private MemoryNavigation() {}

    /** Closest of residue, fracture, observatory. Null when nothing is in range. */
    public static @Nullable Target nearest(ServerLevel level, BlockPos origin) {
        Target best = null;
        double bestDist = Double.MAX_VALUE;

        Target residue = nearestResidue(level, origin);
        if (residue != null) {
            double d = residue.pos().distSqr(origin);
            if (d < bestDist) {
                bestDist = d;
                best = residue;
            }
        }
        Target fracture = nearestFracture(level, origin);
        if (fracture != null) {
            double d = fracture.pos().distSqr(origin);
            if (d < bestDist) {
                bestDist = d;
                best = fracture;
            }
        }
        Target observatory = nearestObservatory(level, origin);
        if (observatory != null) {
            double d = observatory.pos().distSqr(origin);
            if (d < bestDist) {
                best = observatory;
            }
        }
        return best;
    }

    public static @Nullable Target nearestResidue(ServerLevel level, BlockPos origin) {
        AABB box = new AABB(origin).inflate(RESIDUE_RANGE);
        List<ResidueEntity> residues = level.getEntitiesOfClass(ResidueEntity.class, box, ResidueEntity::isAlive);
        ResidueEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ResidueEntity residue : residues) {
            double d = residue.blockPosition().distSqr(origin);
            if (d < bestDist) {
                bestDist = d;
                best = residue;
            }
        }
        return best == null ? null : new Target(Kind.RESIDUE, best.blockPosition().immutable());
    }

    public static @Nullable Target nearestFracture(ServerLevel level, BlockPos origin) {
        int originX = origin.getX() >> 4;
        int originZ = origin.getZ() >> 4;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -FRACTURE_CHUNK_RADIUS; dx <= FRACTURE_CHUNK_RADIUS; dx++) {
            for (int dz = -FRACTURE_CHUNK_RADIUS; dz <= FRACTURE_CHUNK_RADIUS; dz++) {
                int chunkX = originX + dx;
                int chunkZ = originZ + dz;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkMemory memory = LoadedChunkMemory.existing(level.getChunk(chunkX, chunkZ));
                if (memory == null || MemoryPressure.band(memory.cachedPressure()) != PressureBand.FRACTURE) {
                    continue;
                }
                BlockPos center = new BlockPos((chunkX << 4) + 8, origin.getY(), (chunkZ << 4) + 8);
                double d = center.distSqr(origin);
                if (d < bestDist) {
                    bestDist = d;
                    best = center;
                }
            }
        }
        return best == null ? null : new Target(Kind.FRACTURE, best.immutable());
    }

    public static @Nullable Target nearestObservatory(ServerLevel level, BlockPos origin) {
        Optional<HolderSet.Named<Structure>> tag = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .get(ON_OBSERVATORY_MAPS);
        if (tag.isEmpty()) {
            return null;
        }
        try {
            Pair<BlockPos, net.minecraft.core.Holder<Structure>> found = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, tag.get(), origin, OBSERVATORY_SEARCH_CHUNKS, false);
            if (found == null) {
                return null;
            }
            return new Target(Kind.OBSERVATORY, found.getFirst().immutable());
        } catch (RuntimeException ex) {
            Mnemolith.LOGGER.debug("Observatory locate failed", ex);
            return null;
        }
    }

    /** Horizontal bearing in degrees, 0 = south, clockwise (Minecraft yaw sense). */
    public static float bearingDegrees(BlockPos from, BlockPos to) {
        double dx = (to.getX() + 0.5D) - (from.getX() + 0.5D);
        double dz = (to.getZ() + 0.5D) - (from.getZ() + 0.5D);
        return (float) (Math.toDegrees(Math.atan2(-dx, dz)) + 360.0D) % 360.0F;
    }

    /** Eight-way compass label key suffix: n, ne, e, se, s, sw, w, nw. */
    public static String cardinalKey(float bearingDegrees) {
        int sector = Math.round(bearingDegrees / 45.0F) & 7;
        return switch (sector) {
            case 0 -> "s";
            case 1 -> "sw";
            case 2 -> "w";
            case 3 -> "nw";
            case 4 -> "n";
            case 5 -> "ne";
            case 6 -> "e";
            default -> "se";
        };
    }
}
