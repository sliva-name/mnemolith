package com.mnemolith.worldgen.feature;

import com.mnemolith.content.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A broken hollowstone remnant ({@code surface_structures}): a lone pillar, a squat 2x2 stump or the two legs of a
 * fallen arch, 3 to 7 high, with a few loose bricks around. One in three shows a recollite ore block; one in twelve
 * is capped with a block of recollite. Stays within 3 blocks of its origin.
 */
public class HollowRemnantFeature extends Feature<NoneFeatureConfiguration> {
    public HollowRemnantFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return build(context.level(), context.origin(), context.random()) > 0;
    }

    /** Places a remnant standing on the block below {@code origin}. Returns the number of blocks set (0: refused). */
    public static int build(WorldGenLevel level, BlockPos origin, RandomSource random) {
        BlockPos below = origin.below();
        if (!level.getFluidState(origin).isEmpty() || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return 0;
        }
        int placed = 0;
        int shape = random.nextInt(3);
        int height = 3 + random.nextInt(5);
        boolean showsOre = random.nextInt(3) == 0;
        boolean crystalCap = random.nextInt(12) == 0;
        switch (shape) {
            case 0 -> placed += column(level, origin, height, random, showsOre, crystalCap);
            case 1 -> {
                int stump = Math.max(2, height - 2);
                placed += column(level, origin, stump, random, showsOre, false);
                placed += column(level, origin.east(), stump - random.nextInt(2), random, false, false);
                placed += column(level, origin.south(), stump - random.nextInt(2), random, false, crystalCap);
                placed += column(level, origin.south().east(), stump - 1 - random.nextInt(2), random, false, false);
            }
            default -> {
                Direction along = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
                BlockPos other = origin.relative(along, 3);
                placed += column(level, origin, height, random, showsOre, false);
                placed += column(level, other, Math.max(2, height - 1 - random.nextInt(3)), random, false, crystalCap);
                // What is left of the lintel: one or two bricks on the taller leg, leaning out.
                BlockPos lintel = origin.above(height).relative(along);
                if (level.isEmptyBlock(lintel)) {
                    level.setBlock(lintel, brick(random), 2);
                    placed++;
                }
            }
        }
        for (int i = 0, loose = 1 + random.nextInt(3); i < loose; i++) {
            BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,
                    origin.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
            if (level.isEmptyBlock(at) && level.getFluidState(at).isEmpty()) {
                BlockState piece = random.nextInt(3) == 0
                        ? ModBlocks.HOLLOWSTONE_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM)
                        : brick(random);
                level.setBlock(at, piece, 2);
                placed++;
            }
        }
        return placed;
    }

    private static int column(WorldGenLevel level, BlockPos base, int height, RandomSource random, boolean showsOre, boolean crystalCap) {
        int placed = 0;
        // Footing: one block into the ground so the column does not float on a slope.
        level.setBlock(base.below(), ModBlocks.HOLLOWSTONE.get().defaultBlockState(), 2);
        int oreAt = showsOre ? 1 + random.nextInt(Math.max(1, height - 1)) : -1;
        for (int y = 0; y < height; y++) {
            BlockPos at = base.above(y);
            if (!level.isEmptyBlock(at) && !level.getBlockState(at).canBeReplaced()) {
                break;
            }
            BlockState state = y == oreAt ? ModBlocks.RECOLLITE_ORE.get().defaultBlockState() : brick(random);
            level.setBlock(at, state, 2);
            placed++;
        }
        BlockPos cap = base.above(height);
        if (placed == height && level.isEmptyBlock(cap)) {
            if (crystalCap) {
                level.setBlock(cap, ModBlocks.RECOLLITE_BLOCK.get().defaultBlockState(), 2);
                placed++;
            } else if (random.nextInt(3) == 0) {
                level.setBlock(cap, ModBlocks.HOLLOWSTONE_BRICK_SLAB.get().defaultBlockState(), 2);
                placed++;
            }
        }
        return placed;
    }

    private static BlockState brick(RandomSource random) {
        return random.nextInt(10) < 3 ? ModBlocks.HOLLOWSTONE.get().defaultBlockState() : ModBlocks.HOLLOWSTONE_BRICKS.get().defaultBlockState();
    }
}
