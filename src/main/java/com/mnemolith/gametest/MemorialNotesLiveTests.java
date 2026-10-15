package com.mnemolith.gametest;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.data.Transcript;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Memorial notes between two real server players ({@link LivePlayers}): the carver uses a written transcript on a
 * memorial stone (real block use) and it is spent; the reader touches the stone with an empty hand (real block use)
 * and learns the carved entries; the stone keeps them.
 */
final class MemorialNotesLiveTests {
    private MemorialNotesLiveTests() {}

    static void memorialSharesNotes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 13);
        LivePlayers.force(level, site);
        BlockPos stone = site.offset(2, 0, 0);
        ServerPlayer carver = LivePlayers.join(helper, "LiveMemorialCarver", Vec3.atBottomCenterOf(site));
        ServerPlayer reader = LivePlayers.join(helper, "LiveMemorialReader", Vec3.atBottomCenterOf(site.offset(0, 0, 2)));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var chunk = level.getChunkAt(stone);
                    LoadedChunkMemory.clear(chunk);
                    var memory = LoadedChunkMemory.getOrCreate(chunk);
                    level.setBlockAndUpdate(stone, ModBlocks.MEMORIAL_STONE.get().defaultBlockState());
                    memory.setScar(new ScarSite(1, level.getGameTime(), 3, level.getGameTime(), true));
                    reader.setData(ModAttachments.DISCOVERY.get(), new Discovery());
                    carver.getInventory().clearContent();
                    reader.getInventory().clearContent();
                    ItemStack sheet = new ItemStack(ModItems.CHRONICLE_TRANSCRIPT.get());
                    sheet.set(ModDataComponents.TRANSCRIPT.get(), new Transcript(1 | 4, 1, carver.getGameProfile().name()));
                    carver.setItemInHand(InteractionHand.MAIN_HAND, sheet);
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false);
                    carver.gameMode.useItemOn(carver, level, carver.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    boolean spent = carver.getMainHandItem().isEmpty();
                    reader.gameMode.useItemOn(reader, level, reader.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    Discovery learned = reader.getData(ModAttachments.DISCOVERY.get());
                    boolean kept = memory.scar() != null && memory.scar().inscription().isPresent();
                    level.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
                    LoadedChunkMemory.clear(chunk);
                    helper.assertTrue(spent, "carving did not spend the sheet");
                    helper.assertTrue(learned.tags() == 5 && learned.formulas() == 1,
                            "the reader did not learn the carved notes: tags=" + learned.tags() + " formulas=" + learned.formulas());
                    helper.assertTrue(kept, "the memorial lost its notes after a read");
                })
                .thenSucceed();
    }
}
