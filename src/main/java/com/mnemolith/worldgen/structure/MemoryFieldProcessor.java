package com.mnemolith.worldgen.structure;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Marks a memory field plaza and seeds loud pressure so a storm can gather —
 * elevated lookout keeps the player safer from stalkers while watching.
 */
public final class MemoryFieldProcessor implements StructureProcessor {
    public static final MemoryFieldProcessor INSTANCE = new MemoryFieldProcessor();
    public static final MapCodec<MemoryFieldProcessor> CODEC = MapCodec.unit(INSTANCE);

    private MemoryFieldProcessor() {}

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
        if (processedBlockInfo.state().getBlock() != ModBlocks.ARCHIVAL_STRATUM.get()) {
            return processedBlockInfo;
        }
        try {
            ChunkAccess chunk = level.getChunk(processedBlockInfo.pos());
            boolean first = LoadedChunkMemory.markMemoryField(chunk);
            LoadedChunkMemory.noteStratum(chunk, processedBlockInfo.pos());
            if (first) {
                ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
                BlockPos pos = processedBlockInfo.pos();
                long now = 0L;
                if (level instanceof WorldGenLevel worldGen && worldGen.getLevel() instanceof ServerLevel server) {
                    now = server.getGameTime();
                }
                memory.addInstability(55, CommonConfig.PRESSURE_SOFT_CAP.get());
                int max = CommonConfig.MAX_IMPRINTS_PER_CHUNK.get();
                memory.addImprint(new Imprint(ImprintTag.DEATH, 2, pos.immutable(), Optional.empty(), Imprint.contextHash(ImprintTag.DEATH, pos, now), now), max);
                memory.addImprint(new Imprint(ImprintTag.EXPLOSION, 2, pos.immutable(), Optional.empty(), Imprint.contextHash(ImprintTag.EXPLOSION, pos, now), now), max);
                memory.addImprint(new Imprint(ImprintTag.SILENCE, 2, pos.immutable(), Optional.empty(), Imprint.contextHash(ImprintTag.SILENCE, pos, now), now), max);
                if (chunk instanceof LevelChunk levelChunk) {
                    MemoryPressure.recomputeOnLoad(levelChunk, memory, true);
                }
                Mnemolith.LOGGER.debug("Mnemolith memory field at {},{},{}", pos.getX(), pos.getY(), pos.getZ());
            }
        } catch (RuntimeException ignored) {
            // Piece may touch a chunk the region has not opened yet.
        }
        return processedBlockInfo;
    }
}
