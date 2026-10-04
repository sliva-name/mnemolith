package com.mnemolith.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.command.qa.QaSupport;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.ArchiveShrineBlock;
import com.mnemolith.content.block.EchoHomeBlockEntity;
import com.mnemolith.echo.CareLesson;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoRole;
import com.mnemolith.echo.FarmLesson;
import com.mnemolith.echo.LumberLesson;
import com.mnemolith.echo.SlotStack;
import com.mnemolith.echo.StoredEcho;
import com.mnemolith.echo.storm.ArchiveShrines;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Regressions found by the QA sweep: a mute stone removed without the neighbour update flag (what {@code /setblock},
 * {@code /fill} and editing tools use) no longer leaves its chunk muted, a chunk that seeded its structure imprints
 * keeps that fact when the imprints are gone, a broken echo home whose owner is away gives the stored items back,
 * and a claimed archive shrine does not pay its chest out a second time. Synchronous: everything runs in the first tick.
 */
final class RegressionTests {
    private RegressionTests() {}

    /** Own lane, well clear of the suites' (4096 blocks each). */
    private static final int LANE = 60;

    private static BlockPos pad(GameTestHelper helper, int chunkOffset) {
        ServerLevel level = helper.getLevel();
        BlockPos test = helper.absolutePos(BlockPos.ZERO).offset(0, 0, LANE * SuiteTests.LANE_BLOCKS + chunkOffset * 64);
        BlockPos origin = QaSupport.column(level, test.getX() >> 4, test.getZ() >> 4);
        // Entity ticking, or an item or a guardian added here is hidden from the queries that follow.
        QaSupport.tickColumn(level, origin);
        FORCED.add(ChunkPos.containing(origin));
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                level.setBlock(origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
                for (int dy = 0; dy <= 5; dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
        LoadedChunkMemory.clear(level.getChunkAt(origin));
        return origin;
    }

    private static final List<ChunkPos> FORCED = new ArrayList<>();

    static void run(GameTestHelper helper) {
        try {
            staleMuteIsPruned(helper);
            structureSeedFlagPersists(helper);
            brokenHomeGivesItemsBack(helper);
            homeWakesOwnEcho(helper);
            claimedShrinePaysOnce(helper);
            helper.succeed();
        } finally {
            for (ChunkPos chunk : FORCED) {
                QaSupport.releaseColumn(helper.getLevel(), chunk);
            }
            FORCED.clear();
        }
    }

    private static void staleMuteIsPruned(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = pad(helper, 0);
        BlockPos stone = at.offset(2, 0, 2);
        QaSupport.muteStone(level, stone);
        helper.assertTrue(LoadedChunkMemory.isMuted(level, at), "a placed mute stone does not mute its chunk");
        // /setblock: flags 2 | 256, no neighbour update, so the removal hook never runs.
        level.setBlock(stone, Blocks.AIR.defaultBlockState(), 2 | 256);
        helper.assertTrue(!LoadedChunkMemory.isMuted(level, at), "a mute stone removed by a command still mutes the chunk");
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(at));
        helper.assertTrue(memory == null || memory.muteStoneCount() == 0, "the stale mute mark stays in the chunk");

        // A stone that is still there is not pruned.
        QaSupport.muteStone(level, stone);
        helper.assertTrue(LoadedChunkMemory.isMuted(level, at) && LoadedChunkMemory.isMuted(level, at), "a standing mute stone was pruned");
        level.setBlock(stone, Blocks.AIR.defaultBlockState(), 2 | 256);
        LoadedChunkMemory.pruneStale(level.getChunkAt(at), LoadedChunkMemory.getOrCreate(level.getChunkAt(at)));
        helper.assertTrue(!LoadedChunkMemory.isMuted(level, at), "the chunk-load prune left a stale mute mark");
    }

    private static void structureSeedFlagPersists(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = pad(helper, 1);
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(level.getChunkAt(at));
        memory.setStructureSeeded(true);
        helper.assertTrue(!memory.isEmpty(), "a chunk that seeded its imprints reads as empty, so the flag would not be saved");
        var json = ChunkMemory.CODEC.codec().encodeStart(JsonOps.INSTANCE, memory).getOrThrow();
        ChunkMemory back = ChunkMemory.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        helper.assertTrue(back.structureSeeded(), "the structure seed flag did not survive the codec");
        var plain = ChunkMemory.CODEC.codec().encodeStart(JsonOps.INSTANCE, new ChunkMemory()).getOrThrow();
        helper.assertTrue(!ChunkMemory.CODEC.codec().parse(JsonOps.INSTANCE, plain).getOrThrow().structureSeeded(), "the flag is set on a fresh chunk");
    }

    private static void brokenHomeGivesItemsBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = pad(helper, 2);
        BlockPos pedestal = at.offset(0, 0, 0);
        level.setBlock(pedestal, ModBlocks.ECHO_HOME.get().defaultBlockState(), 3);
        helper.assertTrue(level.getBlockEntity(pedestal) instanceof EchoHomeBlockEntity, "the echo home has no block entity");
        EchoHomeBlockEntity home = (EchoHomeBlockEntity) level.getBlockEntity(pedestal);
        UUID away = UUID.randomUUID();
        StoredEcho stored = new StoredEcho(UUID.randomUUID(), away, "Away", Optional.empty(), EchoRole.NONE,
                List.of(new SlotStack(3, new ItemStack(Items.DIAMOND, 5))), Optional.empty(), EchoLesson.NONE, FarmLesson.NONE,
                LumberLesson.NONE, CareLesson.NONE, 20.0F, 0.0D, Optional.empty(), false, 0L);
        helper.assertTrue(home.offer(stored), "the home refused an echo");
        level.setBlock(pedestal, Blocks.AIR.defaultBlockState(), 3);
        int diamonds = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pedestal).inflate(3.0D))) {
            if (item.getItem().is(Items.DIAMOND)) {
                diamonds += item.getItem().getCount();
                item.discard();
            }
        }
        helper.assertTrue(home.isEmpty(), "the pedestal still holds the echo after it was broken");
        helper.assertTrue(diamonds == 5, "the housed echo's items were lost with its pedestal (found " + diamonds + " of 5)");
    }

    /** A stranger's echo in front of yours does not hide it: waking takes the caller's own first echo. */
    private static void homeWakesOwnEcho(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = pad(helper, 4);
        level.setBlock(at, ModBlocks.ECHO_HOME.get().defaultBlockState(), 3);
        EchoHomeBlockEntity home = (EchoHomeBlockEntity) level.getBlockEntity(at);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        StoredEcho a = new StoredEcho(UUID.randomUUID(), first, "First", Optional.empty(), EchoRole.NONE, List.of(), Optional.empty(), EchoLesson.NONE,
                FarmLesson.NONE, LumberLesson.NONE, CareLesson.NONE, 20.0F, 0.0D, Optional.empty(), false, 0L);
        StoredEcho b = new StoredEcho(UUID.randomUUID(), second, "Second", Optional.empty(), EchoRole.NONE, List.of(), Optional.empty(), EchoLesson.NONE,
                FarmLesson.NONE, LumberLesson.NONE, CareLesson.NONE, 20.0F, 0.0D, Optional.empty(), false, 0L);
        home.offer(a);
        home.offer(b);
        helper.assertTrue(home.takeFirstOwnedBy(UUID.randomUUID()).isEmpty(), "a stranger took an echo from the pedestal");
        var taken = home.takeFirstOwnedBy(second);
        helper.assertTrue(taken.isPresent() && taken.get().echo().equals(b.echo()), "the second owner did not get their own echo");
        helper.assertTrue(home.size() == 1 && home.housed().get(0).echo().equals(a.echo()), "the first owner's echo did not stay");
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
    }

    private static void claimedShrinePaysOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = pad(helper, 3);
        BlockPos shrine = at;
        level.setBlock(shrine, ModBlocks.ARCHIVE_SHRINE.get().defaultBlockState()
                .setValue(ArchiveShrineBlock.CHALLENGED, true).setValue(ArchiveShrineBlock.CLAIMED, false), Block.UPDATE_ALL);
        ScarEntity first = guardian(level, shrine);
        ArchiveShrines.onGuardianDefeated(level, first, null);
        int chests = chests(level, shrine);
        helper.assertTrue(chests == 1, "the first guardian left " + chests + " reward chests");
        // A second guardian at the same, now claimed, shrine (it wandered off or unloaded, then the shrine was challenged again).
        ScarEntity second = guardian(level, shrine);
        ArchiveShrines.onGuardianDefeated(level, second, null);
        helper.assertTrue(chests(level, shrine) == 1, "a claimed shrine paid a second reward chest");
        first.discard();
        second.discard();
    }

    private static ScarEntity guardian(ServerLevel level, BlockPos shrine) {
        ScarEntity guardian = ModEntities.ARCHIVE_GUARDIAN.get().create(level, EntitySpawnReason.COMMAND);
        if (guardian == null) {
            throw new IllegalStateException("no archive guardian");
        }
        guardian.snapTo(shrine.getX() + 0.5D, shrine.getY() + 1.0D, shrine.getZ() + 0.5D, 0.0F, 0.0F);
        guardian.setup(0, ArchiveShrines.GUARDIAN_MERGED, shrine);
        return guardian;
    }

    private static int chests(ServerLevel level, BlockPos shrine) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    if (level.getBlockState(shrine.offset(dx, dy, dz)).is(Blocks.CHEST)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
