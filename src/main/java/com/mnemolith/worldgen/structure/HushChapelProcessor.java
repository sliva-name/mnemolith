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

/** Marks the chunk as a hush chapel when mute stone from the template is placed. */
public final class HushChapelProcessor implements StructureProcessor {
    public static final HushChapelProcessor INSTANCE = new HushChapelProcessor();
    public static final MapCodec<HushChapelProcessor> CODEC = MapCodec.unit(INSTANCE);

    private HushChapelProcessor() {}

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
        if (processedBlockInfo.state().getBlock() != ModBlocks.MUTE_STONE.get()) {
            return processedBlockInfo;
        }
        try {
            ChunkAccess chunk = level.getChunk(processedBlockInfo.pos());
            if (LoadedChunkMemory.markHushChapel(chunk)) {
                BlockPos pos = processedBlockInfo.pos();
                Mnemolith.LOGGER.debug("Mnemolith hush chapel at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
            }
            LoadedChunkMemory.addMuteStone(chunk, processedBlockInfo.pos());
        } catch (RuntimeException ignored) {
            // Piece may touch a chunk the region has not opened yet.
        }
        return processedBlockInfo;
    }
}
