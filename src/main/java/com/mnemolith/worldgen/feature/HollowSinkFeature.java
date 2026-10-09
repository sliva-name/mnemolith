package com.mnemolith.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A sunken hollow ({@code local_modifications}): a shallow dish, radius 3 to 6 and 1 to 3 deep, pressed into dry,
 * level ground. The floor gets grass back (the ground pass turns it into hollow turf). Refuses near water or on a
 * slope, so it never opens a hole into a lake or a cliff.
 */
public class HollowSinkFeature extends Feature<NoneFeatureConfiguration> {
    public HollowSinkFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RandomSource random = context.random();
        return press(context.level(), context.origin(), 3 + random.nextInt(4), 1 + random.nextInt(3)) > 0;
    }

    /** Presses a dish centred on the surface at {@code origin}. Returns the blocks removed (0: refused). */
    public static int press(WorldGenLevel level, BlockPos origin, int radius, int depth) {
        int cx = origin.getX();
        int cz = origin.getZ();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz) - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // Level, dry ground under the whole dish and its rim.
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                if (dx * dx + dz * dz > (radius + 1) * (radius + 1)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz) - 1;
                if (Math.abs(top - surface) > 1) {
                    return 0;
                }
                cursor.set(cx + dx, top, cz + dz);
                BlockState ground = level.getBlockState(cursor);
                if (!ground.getFluidState().isEmpty() || !level.getFluidState(cursor.move(0, 1, 0)).isEmpty() || !ground.isSolid()) {
                    return 0;
                }
            }
        }
        int removed = 0;
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = (dx * dx + dz * dz) / (double) (radius * radius);
                if (d > 1.0D) {
                    continue;
                }
                int here = (int) Math.round(depth * (1.0D - d));
                if (here <= 0) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz) - 1;
                for (int k = 0; k < here; k++) {
                    level.setBlock(cursor.set(cx + dx, top - k, cz + dz), Blocks.AIR.defaultBlockState(), 2);
                    removed++;
                }
                level.setBlock(cursor.set(cx + dx, top - here, cz + dz), grass, 2);
            }
        }
        return removed;
    }
}
