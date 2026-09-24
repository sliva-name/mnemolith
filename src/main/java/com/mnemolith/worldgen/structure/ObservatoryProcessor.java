package com.mnemolith.worldgen.structure;

import com.mojang.serialization.MapCodec;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Marks the chunk when the observatory's composition reel is placed. */
public final class ObservatoryProcessor implements StructureProcessor {
    public static final ObservatoryProcessor INSTANCE = new ObservatoryProcessor();
    public static final MapCodec<ObservatoryProcessor> CODEC = MapCodec.unit(INSTANCE);

    private ObservatoryProcessor() {}

    @Override
    public MapCodec<? extends StructureProcessor> codec() {
        return CODEC;
    }

    @Override
    public StructureTemplate.StructureBlockInfo process(
            LevelReader level,
            BlockPos targetPosition,
            BlockPos referencePos,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo processedBlockInfo,
            StructurePlaceSettings settings,
            StructureTemplate template) {
        if (processedBlockInfo.state().getBlock() != ModBlocks.COMPOSITION_REEL.get()) {
            return processedBlockInfo;
        }
        try {
            ChunkAccess chunk = level.getChunk(processedBlockInfo.pos());
            if (LoadedChunkMemory.markObservatory(chunk)) {
                BlockPos pos = processedBlockInfo.pos();
                Mnemolith.LOGGER.info("Mnemolith observatory at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
            }
        } catch (RuntimeException ignored) {
            // The piece can touch a chunk the region has not opened yet. The reel block still places.
        }
        return processedBlockInfo;
    }
}
