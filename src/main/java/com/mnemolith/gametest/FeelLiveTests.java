package com.mnemolith.gametest;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.MemoryFeel;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A real server player (live site 26) threads a hush loom by hand: the shared KEPT cue is sent once, at the loom.
 * The particles and sound themselves are client presentation (by-hand check); this proves the real interaction path
 * reaches the shared language.
 */
final class FeelLiveTests {
    private FeelLiveTests() {}

    static void loomCueReachesPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 26);
        LivePlayers.force(level, site);
        ServerPlayer player = LivePlayers.join(helper, "LiveFeel", Vec3.atBottomCenterOf(site));
        BlockPos loom = site.east(2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    player.setGameMode(GameType.SURVIVAL);
                    level.setBlock(loom, ModBlocks.HUSH_LOOM.get().defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(loom));
                    ImprintWriter.write(level, loom, java.util.List.of(ImprintTag.DEATH), null, false);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HUSH_FILAMENT.get(), 1));
                    int kept = MemoryFeel.sent(MemoryFeel.Kind.KEPT);
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(loom).add(0.0D, 0.5D, 0.0D), Direction.UP, loom, false);
                    player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    int after = MemoryFeel.sent(MemoryFeel.Kind.KEPT);
                    BlockPos at = MemoryFeel.lastPos();
                    level.setBlock(loom, Blocks.AIR.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(loom));
                    player.getInventory().clearContent();
                    helper.assertTrue(after == kept + 1 && at.closerThan(loom, 2.0D), "kept cue: " + kept + " -> " + after + " at " + at);
                })
                .thenSucceed();
    }

    /** Stage 2: a real player (live site 27) places a block by hand; the build imprint is written with the shared WRITTEN cue. */
    static void writeCueReachesPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 27);
        LivePlayers.force(level, site);
        ServerPlayer player = LivePlayers.join(helper, "LiveFeel2", Vec3.atBottomCenterOf(site));
        BlockPos floor = site.east(2).below();
        BlockPos placed = floor.above();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    player.setGameMode(GameType.SURVIVAL);
                    level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(placed, Blocks.AIR.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(placed));
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 4));
                    int written = MemoryFeel.sent(MemoryFeel.Kind.WRITTEN);
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0.0D, 0.5D, 0.0D), Direction.UP, floor, false);
                    player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    int after = MemoryFeel.sent(MemoryFeel.Kind.WRITTEN);
                    BlockPos at = MemoryFeel.lastPos();
                    boolean isPlaced = level.getBlockState(placed).is(Blocks.COBBLESTONE);
                    level.setBlock(placed, Blocks.AIR.defaultBlockState(), 3);
                    LoadedChunkMemory.clear(level.getChunkAt(placed));
                    player.getInventory().clearContent();
                    helper.assertTrue(isPlaced && after > written && at.closerThan(placed, 3.0D), "written cue: placed=" + isPlaced + " " + written + " -> " + after + " at " + at);
                })
                .thenSucceed();
    }

    /**
     * Stage 3: a real player (live site 28) stands in a saturated chunk: the server's own once-a-second scan sends the
     * strong-memory hint once and, after standing still, after-images.
     */
    static void strongChunkHintsPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 28);
        LivePlayers.force(level, site);
        ServerPlayer[] player = new ServerPlayer[1];
        helper.startSequence()
                .thenExecute(() -> {
                    LoadedChunkMemory.clear(level.getChunkAt(site));
                    com.mnemolith.command.qa.Feel3Qa.saturate(LoadedChunkMemory.getOrCreate(level.getChunkAt(site)), site, 4, level.getGameTime());
                    player[0] = LivePlayers.join(helper, "LiveFeel3", Vec3.atBottomCenterOf(site));
                    com.mnemolith.particle.AfterImages.forget(player[0].getUUID());
                })
                .thenIdle(140)
                .thenExecute(() -> {
                    int[] sent = com.mnemolith.particle.AfterImages.sentTo(player[0].getUUID());
                    LoadedChunkMemory.clear(level.getChunkAt(site));
                    helper.assertTrue(sent[0] == 1 && sent[1] >= 1, "hints=" + sent[0] + " images=" + sent[1]);
                })
                .thenSucceed();
    }

    /**
     * Stage 4: a real player (live site 29) wearing a remembering circlet stands in a chunk whose loudest memory is a
     * death under newer paths: the lens snapshot the server would send names death for that chunk, and the circlet tint
     * applies; taking the circlet off ends it.
     */
    static void lensReadsLoudestCircletTints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 29);
        LivePlayers.force(level, site);
        ServerPlayer player = LivePlayers.join(helper, "LiveFeel4", Vec3.atBottomCenterOf(site));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    player.setGameMode(GameType.SURVIVAL);
                    LoadedChunkMemory.clear(level.getChunkAt(site));
                    ImprintWriter.write(level, site, java.util.List.of(ImprintTag.DEATH), null, false);
                    var memory = LoadedChunkMemory.getOrCreate(level.getChunkAt(site));
                    for (int i = 0; i < 3; i++) {
                        memory.addImprint(new com.mnemolith.imprint.Imprint(ImprintTag.PATH, 1, site.east(i), java.util.Optional.empty(), 40 + i,
                                level.getGameTime(), false), 64);
                    }
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.REMEMBERING_CIRCLET.get()));
                    int cx = site.getX() >> 4;
                    int cz = site.getZ() >> 4;
                    var here = com.mnemolith.network.PressureCollector.snapshotFor(player).stream()
                            .filter(c -> c.chunkX() == cx && c.chunkZ() == cz).findFirst().orElse(null);
                    boolean tinted = com.mnemolith.common.MemoryGlyphs.circletTint(player);
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
                    boolean after = com.mnemolith.common.MemoryGlyphs.circletTint(player);
                    LoadedChunkMemory.clear(level.getChunkAt(site));
                    helper.assertTrue(here != null && here.loudest() == ImprintTag.DEATH.ordinal(), "loudest in snapshot: " + here);
                    helper.assertTrue(tinted && !after, "circlet tint: worn=" + tinted + " off=" + after);
                })
                .thenSucceed();
    }
}
