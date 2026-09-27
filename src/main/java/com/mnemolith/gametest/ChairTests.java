package com.mnemolith.gametest;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.PleadingChair;
import com.mnemolith.worldgen.structure.ChairHouses;
import com.mnemolith.worldgen.structure.PleadingChairProcessor;
import com.mnemolith.worldgen.structure.VillageChairPools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Indoor floor, voice cooldown, and the village house pool wiring. */
final class ChairTests {
    private ChairTests() {}

    static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int hits = 0;
        boolean stable = true;
        for (int i = 0; i < 64; i++) {
            BlockPos origin = new BlockPos(i * 7, 70, i * 3);
            boolean once = ChairHouses.selected(origin);
            if (once) {
                hits++;
            }
            if (once != ChairHouses.selected(origin)) {
                stable = false;
            }
        }
        helper.assertTrue(stable, "the house roll changed between calls");
        helper.assertTrue(hits > 0 && hits < 64, "the house roll was " + hits + "/64");
        helper.assertTrue(PleadingChair.heard(false, true, 0), "a first approach should play");
        helper.assertTrue(!PleadingChair.heard(true, true, 0), "standing still should not replay");
        helper.assertTrue(!PleadingChair.heard(false, true, 10), "the cooldown should hold");

        List<StructureTemplate.StructureBlockInfo> room = room(new BlockPos(80, 90, 80), true);
        List<StructureTemplate.StructureBlockInfo> yard = room(new BlockPos(80, 90, 120), false);
        BlockPos spot = ChairHouses.chooseSpot(room, net.minecraft.util.RandomSource.create(1L));
        helper.assertTrue(spot != null, "a closed room had no floor");
        helper.assertTrue(ChairHouses.chooseSpot(yard, net.minecraft.util.RandomSource.create(1L)) == null, "an open yard grew a chair");

        // Not added to the level: a real spawn shares this batch with the other suites and shifts their random.
        PleadingChair chair = new PleadingChair(ModEntities.PLEADING_CHAIR.get(), level);
        helper.assertTrue(chair.tryVoice(level), "the clip did not start");
        helper.assertTrue(!chair.tryVoice(level), "the clip restarted inside its cooldown");

        VillageChairPools.attach(level.registryAccess());
        Registry<StructureTemplatePool> pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        StructureTemplatePool plains = pools.getValue(Identifier.parse("minecraft:village/plains/houses"));
        helper.assertTrue(plains != null, "plains houses pool is missing");
        boolean house = false;
        boolean farm = false;
        for (var pair : plains.getTemplates()) {
            if (!(pair.getFirst() instanceof SinglePoolElement single)) {
                continue;
            }
            boolean wired = false;
            for (StructureProcessor processor : single.processors.value().list()) {
                if (processor instanceof PleadingChairProcessor) {
                    wired = true;
                    break;
                }
            }
            String path = single.getTemplateLocation().getPath();
            if (path.endsWith("plains_small_house_1")) {
                house = wired;
            }
            if (path.contains("farm")) {
                farm |= wired;
            }
        }
        helper.assertTrue(house, "plains_small_house_1 has no chair processor");
        helper.assertTrue(!farm, "a farm piece was given the chair processor");
        helper.succeed();
    }

    /** A 7×5×7 plank shell. Without {@code ceiling} the floor is open to the sky. */
    private static List<StructureTemplate.StructureBlockInfo> room(BlockPos origin, boolean ceiling) {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        BlockState plank = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 7; z++) {
                    if (!ceiling && y == 4) {
                        continue;
                    }
                    boolean shell = x == 0 || x == 6 || z == 0 || z == 6 || y == 0 || y == 4;
                    blocks.add(new StructureTemplate.StructureBlockInfo(origin.offset(x, y, z), shell ? plank : air, null));
                }
            }
        }
        return blocks;
    }
}
