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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/** Indoor floor, voice cooldown, village house pool wiring, and a real plains house placement. */
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
        BlockPos spot = ChairHouses.chooseSpot(room, RandomSource.create(1L));
        helper.assertTrue(spot != null, "a closed room had no floor");
        helper.assertTrue(ChairHouses.chooseSpot(yard, RandomSource.create(1L)) == null, "an open yard grew a chair");
        // Legacy village pieces delete air before the processor runs. A room that only keeps its shell must still have a floor.
        helper.assertTrue(ChairHouses.chooseSpot(withoutAir(room), RandomSource.create(1L)) != null, "a room with the air removed had no floor");

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

    /**
     * Places {@code plains_small_house_1} through the pool element worldgen uses, and requires a chair.
     * Runs in its own batch so the spawn does not share the suites' world random. The roll uses the piece
     * position only; placement uses a private {@link RandomSource}.
     */
    static void villageHouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageChairPools.attach(level.registryAccess());
        SinglePoolElement house = house(level, "minecraft:village/plains/houses/plains_small_house_1");
        helper.assertTrue(wired(house), "plains_small_house_1 has no chair processor");
        BlockPos origin = rolledOrigin(helper);
        helper.assertTrue(origin != null, "no piece position rolled a chair");
        helper.assertTrue(house != null, "plains_small_house_1 is missing");
        BoundingBox box = new BoundingBox(origin.getX() - 2, origin.getY() - 2, origin.getZ() - 2, origin.getX() + 24, origin.getY() + 24, origin.getZ() + 24);
        try {
            boolean placed = house.place(
                    level.getStructureManager(),
                    level,
                    level.structureManager(),
                    level.getChunkSource().getGenerator(),
                    origin,
                    origin,
                    Rotation.NONE,
                    box,
                    RandomSource.create(1L),
                    LiquidSettings.IGNORE_WATERLOGGING,
                    false);
            helper.assertTrue(placed, "plains_small_house_1 did not place");
            List<PleadingChair> chairs = level.getEntitiesOfClass(PleadingChair.class, new AABB(origin).inflate(24.0D), entity -> entity.isAlive());
            helper.assertTrue(!chairs.isEmpty(), "the wired village house placed no chair");
            BlockPos feet = chairs.getFirst().blockPosition();
            helper.assertTrue(level.getBlockState(feet.below()).blocksMotion(), "the chair is not standing on the house floor");
        } finally {
            for (PleadingChair chair : level.getEntitiesOfClass(PleadingChair.class, new AABB(origin).inflate(24.0D), entity -> true)) {
                chair.discard();
            }
            BlockPos.betweenClosed(origin.offset(-2, -2, -2), origin.offset(24, 24, 24)).forEach(pos -> {
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            });
        }
        helper.succeed();
    }

    /** A position inside the test chunk, high enough to miss the test structure, that the 1-in-8 roll accepts. */
    private static BlockPos rolledOrigin(GameTestHelper helper) {
        BlockPos test = helper.absolutePos(BlockPos.ZERO);
        int x = (test.getX() >> 4 << 4) + 2;
        int z = (test.getZ() >> 4 << 4) + 2;
        for (int i = 0; i < 64; i++) {
            BlockPos candidate = new BlockPos(x, 48 + i, z);
            if (ChairHouses.selected(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static SinglePoolElement house(ServerLevel level, String id) {
        Registry<StructureTemplatePool> pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        StructureTemplatePool plains = pools.getValue(Identifier.parse("minecraft:village/plains/houses"));
        if (plains == null) {
            return null;
        }
        Identifier location = Identifier.parse(id);
        for (var pair : plains.getTemplates()) {
            if (pair.getFirst() instanceof SinglePoolElement single && location.equals(single.getTemplateLocation())) {
                return single;
            }
        }
        return null;
    }

    private static boolean wired(SinglePoolElement element) {
        if (element == null) {
            return false;
        }
        for (StructureProcessor processor : element.processors.value().list()) {
            if (processor instanceof PleadingChairProcessor) {
                return true;
            }
        }
        return false;
    }

    private static List<StructureTemplate.StructureBlockInfo> withoutAir(List<StructureTemplate.StructureBlockInfo> blocks) {
        List<StructureTemplate.StructureBlockInfo> kept = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            if (!info.state().isAir()) {
                kept.add(info);
            }
        }
        return kept;
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
