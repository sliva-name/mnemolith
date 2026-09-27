package com.mnemolith.worldgen.structure;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Rare chair on the floor of a village house. Wired onto house template-pool elements, not streets. */
public final class PleadingChairProcessor implements StructureProcessor {
    public static final PleadingChairProcessor INSTANCE = new PleadingChairProcessor();
    public static final MapCodec<PleadingChairProcessor> CODEC = MapCodec.unit(INSTANCE);

    private PleadingChairProcessor() {}

    @Override
    public MapCodec<? extends StructureProcessor> codec() {
        return CODEC;
    }

    /** The whole house, so a piece split across chunks still has one indoor spot. */
    @Override
    public boolean evaluatesEntirePieceState() {
        return true;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level,
            BlockPos position,
            BlockPos referencePos,
            List<StructureTemplate.StructureBlockInfo> originalBlockInfoList,
            List<StructureTemplate.StructureBlockInfo> processedBlockInfoList,
            StructurePlaceSettings settings) {
        if (!ChairHouses.selected(position)) {
            return processedBlockInfoList;
        }
        RandomSource random = RandomSource.create(net.minecraft.util.Mth.getSeed(position));
        random.nextInt(ChairHouses.CHANCE);
        BlockPos spot = ChairHouses.chooseSpot(processedBlockInfoList, random);
        if (spot == null) {
            return processedBlockInfoList;
        }
        BoundingBox box = settings.getBoundingBox();
        if (box != null && !box.isInside(spot)) {
            return processedBlockInfoList;
        }
        ChairHouses.spawn(level, spot, ChairHouses.yawTowardCenter(processedBlockInfoList, spot));
        return processedBlockInfoList;
    }
}
