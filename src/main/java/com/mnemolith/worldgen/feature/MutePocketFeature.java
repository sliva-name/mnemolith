package com.mnemolith.worldgen.feature;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.WorldgenTuning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/** A small stone room whose mute-stone lining suppresses imprint writes. */
public class MutePocketFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chests/mute_pocket"));

    public MutePocketFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return this.placePocket(context.level(), context.origin(), context.random(), false);
    }

    public boolean placePocket(WorldGenLevel level, BlockPos origin, RandomSource random, boolean forced) {
        if (!forced && (!WorldgenTuning.pocketsEnabled() || random.nextInt(100) >= WorldgenTuning.pocketChance())) {
            return false;
        }
        BlockPos floor = this.floorAt(level, origin, random, forced);
        if (floor == null) {
            return false;
        }
        int stones = 0;
        int half = WorldgenTuning.POCKET_HALF;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = 1; dy <= 2; dy++) {
                    cursor.set(floor.getX() + dx, floor.getY() + dy, floor.getZ() + dz);
                    if (this.carve(level, cursor)) {
                        // interior
                    }
                }
                cursor.set(floor.getX() + dx, floor.getY(), floor.getZ() + dz);
                if (this.placeMute(level, cursor)) {
                    stones++;
                }
                boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                boolean doorway = dx == half && dz == 0;
                if (edge && !doorway) {
                    cursor.set(floor.getX() + dx, floor.getY() + 1, floor.getZ() + dz);
                    if (this.placeMute(level, cursor)) {
                        stones++;
                    }
                }
            }
        }
        if (stones == 0) {
            return false;
        }
        if (forced || random.nextInt(2) == 0) {
            this.placeChest(level, floor.above(), random);
        }
        Mnemolith.LOGGER.debug("Mnemolith mute pocket at {},{},{} stones={}", floor.getX(), floor.getY(), floor.getZ(), stones);
        return true;
    }

    private BlockPos floorAt(WorldGenLevel level, BlockPos origin, RandomSource random, boolean forced) {
        if (forced) {
            BlockPos under = origin.below(4);
            return under.getY() > level.getMinY() ? under : origin;
        }
        int minY = Math.max(level.getMinY() + 1, Math.min(WorldgenTuning.pocketMinY(), WorldgenTuning.pocketMaxY()));
        int maxY = Math.min(level.getMaxY() - 4, Math.max(WorldgenTuning.pocketMinY(), WorldgenTuning.pocketMaxY()));
        if (minY > maxY) {
            return null;
        }
        int y = minY + random.nextInt(maxY - minY + 1);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        if (y > surface - 8) {
            return null;
        }
        BlockPos floor = new BlockPos(origin.getX(), y, origin.getZ());
        return this.isStone(level.getBlockState(floor)) ? floor : null;
    }

    private boolean carve(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() == Blocks.BEDROCK || !this.isStone(state)) {
            return false;
        }
        return level.setBlock(pos, Blocks.CAVE_AIR.defaultBlockState(), 2);
    }

    private boolean placeMute(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() == Blocks.BEDROCK) {
            return false;
        }
        if (!this.isStone(state) && !state.isAir()) {
            return false;
        }
        if (!level.setBlock(pos, ModBlocks.MUTE_STONE.get().defaultBlockState(), 2)) {
            return false;
        }
        ChunkAccess chunk = level.getChunk(pos);
        LoadedChunkMemory.addMuteStone(chunk, pos);
        return true;
    }

    private void placeChest(WorldGenLevel level, BlockPos pos, RandomSource random) {
        if (!level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(LOOT);
            chest.setLootTableSeed(random.nextLong());
        }
    }

    private boolean isStone(BlockState state) {
        return state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.is(BlockTags.BASE_STONE_OVERWORLD);
    }
}
