package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.recall.LifeMoment;
import com.mnemolith.recall.LifeMomentKind;
import com.mnemolith.recall.LifeMoments;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sleep consolidation against real server players at a real night ({@link LivePlayers.Night}): the bed chunk cools only
 * after a whole night. Lying down and pressing "Leave Bed" before morning (the client's real STOP_SLEEPING packet),
 * even after sleeping long enough, cools nothing and is no home; a night really skipped by everyone sleeping does both.
 */
final class SleepLiveTests {
    private SleepLiveTests() {}

    private static final int HEAT = 40;

    static void leaveBedDoesNotConsolidate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = RememberLiveTests.site(helper, 3);
        // The sleeper's bed in the site chunk, which has memory; the watcher's bed one chunk west, which has none.
        BlockPos head = site.offset(2, 0, 0);
        BlockPos watcherHead = site.offset(-10, 0, 4);
        placeBed(level, head);
        placeBed(level, watcherHead);
        ServerPlayer sleeper = LivePlayers.join(helper, "LiveSleeper", Vec3.atBottomCenterOf(site));
        // Awake, the watcher keeps the night from being skipped while the sleeper sleeps long enough.
        ServerPlayer watcher = LivePlayers.join(helper, "LiveSleepWatcher", Vec3.atBottomCenterOf(watcherHead.offset(0, 0, -2)));
        LifeMoments moments = LifeMoments.get(level.getServer());
        moments.forget(sleeper.getUUID());
        moments.forget(watcher.getUUID());
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(level.getChunkAt(head));
        int cool = CommonConfig.SLEEP_COOL_AMOUNT.get();
        int[] before = new int[1];
        // A failed check inside thenExecute marks the test failed but lets the next events run in the same tick; they
        // wait for this, so the first failure is the one reported.
        boolean[] nightReady = new boolean[1];
        // Nothing hostile may wake the sleepers or keep them out of bed.
        helper.onEachTick(() -> level.getEntitiesOfClass(Monster.class, new AABB(site).inflate(32.0D)).forEach(Monster::discard));
        helper.startSequence()
                .thenExecute(() -> LivePlayers.Night.midnight(level))
                .thenWaitUntil(() -> helper.assertTrue(level.isDarkOutside(), "it is not night yet"))
                .thenExecute(() -> {
                    helper.assertTrue(sleeper.startSleepInBed(head).right().isPresent(), "the sleeper could not lie down");
                    helper.assertTrue(sleeper.isSleeping(), "the sleeper is not in bed");
                })
                // Real ticks in the dark, until the vanilla five seconds a night skip needs.
                .thenWaitUntil(() -> helper.assertTrue(sleeper.isSleepingLongEnough(), "the sleeper has not slept long enough yet"))
                .thenExecute(() -> {
                    helper.assertTrue(level.isDarkOutside() && sleeper.isSleeping() && !watcher.isSleeping(), "the night ended early");
                    // "Leave Bed" in the middle of the night, after sleeping long enough.
                    heat(memory);
                    leaveBed(sleeper);
                    helper.assertTrue(!sleeper.isSleeping(), "Leave Bed did not wake the sleeper");
                    helper.assertTrue(memory.instability() == HEAT, "Leave Bed at night cooled the bed chunk: " + HEAT + " -> " + memory.instability());
                    helper.assertTrue(homes(moments, sleeper).isEmpty(), "Leave Bed at night was remembered as a home");
                    // A lie-down and straight back up.
                    helper.assertTrue(sleeper.startSleepInBed(head).right().isPresent(), "the sleeper could not lie down again");
                    heat(memory);
                    leaveBed(sleeper);
                    helper.assertTrue(memory.instability() == HEAT, "a lie-down at night cooled the bed chunk: " + HEAT + " -> " + memory.instability());
                    // Being shaken awake after a long sleep does not count either.
                    helper.assertTrue(sleeper.startSleepInBed(head).right().isPresent(), "the sleeper could not lie down a third time");
                    heat(memory);
                    sleeper.stopSleeping();
                    helper.assertTrue(memory.instability() == HEAT, "being shaken awake cooled the bed chunk: " + HEAT + " -> " + memory.instability());
                    // A whole night: both lie down, and the level skips the night once both have slept long enough.
                    helper.assertTrue(sleeper.startSleepInBed(head).right().isPresent(), "the sleeper could not lie down for the night");
                    helper.assertTrue(watcher.startSleepInBed(watcherHead).right().isPresent(), "the watcher could not lie down");
                    nightReady[0] = true;
                })
                .thenWaitUntil(() -> {
                    if (!nightReady[0]) {
                        return;
                    }
                    if (sleeper.isSleeping()) {
                        // Hot right up to the level tick that wakes everyone.
                        heat(memory);
                        before[0] = memory.instability();
                    }
                    helper.assertTrue(!sleeper.isSleeping(), "the night has not been skipped yet");
                })
                .thenExecute(() -> {
                    if (!nightReady[0]) {
                        return;
                    }
                    helper.assertTrue(!watcher.isSleeping() && level.isBrightOutside(), "the sleepers woke without the night being skipped");
                    // At most one natural decay pulse may fall in the same tick as the wake.
                    int drop = before[0] - memory.instability();
                    int decay = CommonConfig.INSTABILITY_DECAY.get();
                    helper.assertTrue(drop >= cool && drop <= cool + decay, "a night slept through did not cool the bed chunk by " + cool + ": dropped " + drop);
                    List<LifeMoment> homes = homes(moments, sleeper);
                    helper.assertTrue(homes.size() == 1 && homes.get(0).pos().equals(head), "a night slept through was not remembered as a home: " + homes);
                    moments.forget(sleeper.getUUID());
                    moments.forget(watcher.getUUID());
                })
                .thenSucceed();
    }

    private static void heat(ChunkMemory memory) {
        memory.coolInstability(memory.instability());
        memory.addInstability(HEAT, 100);
    }

    /** What the client sends when the player presses "Leave Bed". */
    private static void leaveBed(ServerPlayer player) {
        player.connection.handlePlayerCommand(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
    }

    private static void placeBed(ServerLevel level, BlockPos head) {
        BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(head.west(), bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
    }

    private static List<LifeMoment> homes(LifeMoments moments, ServerPlayer player) {
        return moments.of(player.getUUID()).stream().filter(m -> m.kind() == LifeMomentKind.HOME).toList();
    }
}
