package com.mnemolith.worldgen.feature;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.WorldgenTuning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A short underground band of archival stratum. Placement stays inside the chunk. */
public class ArchivalVeinFeature extends Feature<NoneFeatureConfiguration> {
    public ArchivalVeinFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return this.placeVein(context.level(), context.origin(), context.random(), false);
    }

    public boolean placeVein(WorldGenLevel level, BlockPos origin, RandomSource random, boolean forced) {
        if (!forced && (!WorldgenTuning.veinsEnabled() || random.nextInt(100) >= WorldgenTuning.veinChance())) {
            return false;
        }
        int minY = Math.min(WorldgenTuning.veinMinY(), WorldgenTuning.veinMaxY());
        int maxY = Math.max(WorldgenTuning.veinMinY(), WorldgenTuning.veinMaxY());
        minY = Math.max(minY, level.getMinY());
        maxY = Math.min(maxY, level.getMaxY() - 1);
        if (minY > maxY) {
            return false;
        }
        int y = forced ? origin.getY() : minY + random.nextInt(maxY - minY + 1);
        boolean alongX = random.nextBoolean();
        int length = WorldgenTuning.veinSize();
        int placed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int step = 0; step < length; step++) {
            int x = origin.getX() + (alongX ? step - length / 2 : 0);
            int z = origin.getZ() + (alongX ? 0 : step - length / 2);
            cursor.set(x, y, z);
            if (this.placeStratum(level, cursor, forced && step == length / 2)) {
                placed++;
            }
            cursor.set(x, y - 1, z);
            if ((step & 1) == 0 && this.placeStratum(level, cursor, false)) {
                placed++;
            }
        }
        if (placed == 0) {
            return false;
        }
        if (level instanceof ServerLevel server) {
            net.minecraft.world.level.chunk.LevelChunk chunk = server.getChunkAt(origin);
            com.mnemolith.imprint.ChunkMemory memory = LoadedChunkMemory.existing(chunk);
            if (memory != null) {
                MemoryPressure.recompute(chunk, memory);
            }
        }
        Mnemolith.LOGGER.info("Mnemolith archival vein at {},{},{} blocks={}", origin.getX(), y, origin.getZ(), placed);
        return true;
    }

    private boolean placeStratum(WorldGenLevel level, BlockPos pos, boolean allowAny) {
        BlockState state = level.getBlockState(pos);
        boolean stone = state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
        if (!stone && !(allowAny && state.getBlock() != net.minecraft.world.level.block.Blocks.BEDROCK)) {
            return false;
        }
        if (!level.setBlock(pos, ModBlocks.ARCHIVAL_STRATUM.get().defaultBlockState(), 2)) {
            return false;
        }
        ChunkAccess chunk = level.getChunk(pos);
        LoadedChunkMemory.noteStratum(chunk, pos);
        return true;
    }
}
