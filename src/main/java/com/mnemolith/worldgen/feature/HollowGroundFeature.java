package com.mnemolith.worldgen.feature;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.worldgen.hollows.Hollows;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Memory Hollows ground pass ({@code top_layer_modification}, once per chunk): every column whose surface is in the
 * biome gets hollow turf for grass and hollowstone for the stone 3 to 18 blocks below, then a few small recollite ore
 * clusters inside that layer. Reads one biome per column and writes only inside its own chunk.
 */
public class HollowGroundFeature extends Feature<NoneFeatureConfiguration> {
    public static final int LAYER_TOP = 3;
    public static final int LAYER_BOTTOM = 18;
    /** How far down from the heightmap the pass looks through leaves, logs and plants for the ground. */
    private static final int GROUND_SCAN = 24;

    public HollowGroundFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos origin = context.origin();
        return convert(context.level(), origin.getX() & ~15, origin.getZ() & ~15, 16, context.random(), false).columns() > 0;
    }

    public record Result(int columns, int turf, int stone, int ore) {}

    /**
     * Converts a {@code size} x {@code size} square (a whole chunk in worldgen). {@code forced} skips the biome test
     * (QA and tests on a flat world).
     */
    public static Result convert(WorldGenLevel level, int minX, int minZ, int size, RandomSource random, boolean forced) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState turf = ModBlocks.HOLLOW_TURF.get().defaultBlockState();
        BlockState hollowstone = ModBlocks.HOLLOWSTONE.get().defaultBlockState();
        int columns = 0;
        int turfed = 0;
        int stoned = 0;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                int top = ground(level, cursor, x, z);
                if (top <= level.getMinY()) {
                    continue;
                }
                cursor.set(x, top, z);
                if (!forced && !Hollows.is(level.getBiome(cursor))) {
                    continue;
                }
                columns++;
                if (level.getBlockState(cursor).is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(cursor, turf, 2);
                    turfed++;
                }
                int from = Math.max(level.getMinY() + 1, top - LAYER_BOTTOM);
                for (int y = top - LAYER_TOP; y >= from; y--) {
                    cursor.setY(y);
                    if (stoneLike(level.getBlockState(cursor))) {
                        level.setBlock(cursor, hollowstone, 2);
                        stoned++;
                    }
                }
                lowest = Math.min(lowest, top);
                highest = Math.max(highest, top);
            }
        }
        int ore = 0;
        if (columns > 0) {
            int clusters = 2 + random.nextInt(3);
            for (int i = 0; i < clusters; i++) {
                ore += oreCluster(level, cursor, minX, minZ, size, lowest, random, hollowstone);
            }
        }
        return new Result(columns, turfed, stoned, ore);
    }

    /** The top solid ground block of a column, looking down through leaves, logs and plants. */
    static int ground(WorldGenLevel level, BlockPos.MutableBlockPos cursor, int x, int z) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        int stop = Math.max(level.getMinY(), y - GROUND_SCAN);
        for (; y > stop; y--) {
            BlockState state = level.getBlockState(cursor.set(x, y, z));
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.canBeReplaced()) {
                continue;
            }
            return y;
        }
        return y;
    }

    public static boolean stoneLike(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE) || state.is(Blocks.TUFF);
    }

    /** A random walk of 2 to 5 ore blocks through hollowstone, kept inside the square. */
    private static int oreCluster(WorldGenLevel level, BlockPos.MutableBlockPos cursor, int minX, int minZ, int size, int lowest,
            RandomSource random, BlockState hollowstone) {
        int max = size - 1;
        int x = minX + random.nextInt(size);
        int z = minZ + random.nextInt(size);
        int y = lowest - LAYER_TOP - 1 - random.nextInt(LAYER_BOTTOM - LAYER_TOP - 2);
        BlockState ore = ModBlocks.RECOLLITE_ORE.get().defaultBlockState();
        int cluster = 2 + random.nextInt(4);
        int placed = 0;
        for (int i = 0; i < cluster * 3 && placed < cluster; i++) {
            cursor.set(x, y, z);
            if (level.getBlockState(cursor).is(hollowstone.getBlock())) {
                level.setBlock(cursor, ore, 2);
                placed++;
            }
            switch (random.nextInt(6)) {
                case 0 -> x = Math.min(minX + max, x + 1);
                case 1 -> x = Math.max(minX, x - 1);
                case 2 -> z = Math.min(minZ + max, z + 1);
                case 3 -> z = Math.max(minZ, z - 1);
                case 4 -> y++;
                default -> y--;
            }
        }
        return placed;
    }
}
