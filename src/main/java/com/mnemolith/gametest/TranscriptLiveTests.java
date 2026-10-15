package com.mnemolith.gametest;

import com.mnemolith.content.ModItems;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The chronicle transcript between two real server players ({@link LivePlayers}): the author writes a blank sheet
 * through the real item use, hands it over, the reader uses it and learns the author's tags and formula; the sheet is
 * spent and the reader's discovery is the author's.
 */
final class TranscriptLiveTests {
    private TranscriptLiveTests() {}

    static void transcriptSharedBetweenPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 7);
        LivePlayers.force(level, site);
        ServerPlayer author = LivePlayers.join(helper, "LiveTranscriptAuthor", Vec3.atBottomCenterOf(site));
        ServerPlayer reader = LivePlayers.join(helper, "LiveTranscriptReader", Vec3.atBottomCenterOf(site.offset(2, 0, 0)));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Discovery known = new Discovery();
                    known.noteTag(ImprintTag.DEATH);
                    known.noteTag(ImprintTag.SCULK);
                    known.noteFormula(1);
                    author.setData(ModAttachments.DISCOVERY.get(), known);
                    reader.setData(ModAttachments.DISCOVERY.get(), new Discovery());
                    author.getInventory().clearContent();
                    reader.getInventory().clearContent();
                    author.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CHRONICLE_TRANSCRIPT.get()));
                    author.gameMode.useItem(author, level, author.getMainHandItem(), InteractionHand.MAIN_HAND);
                    ItemStack sheet = author.getMainHandItem();
                    helper.assertTrue(sheet.has(ModDataComponents.TRANSCRIPT.get()), "using a blank transcript wrote nothing");
                    reader.setItemInHand(InteractionHand.MAIN_HAND, sheet.copy());
                    author.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    reader.gameMode.useItem(reader, level, reader.getMainHandItem(), InteractionHand.MAIN_HAND);
                    Discovery learned = reader.getData(ModAttachments.DISCOVERY.get());
                    helper.assertTrue(learned.tags() == known.tags() && learned.formulas() == known.formulas(),
                            "the reader did not learn the author's notes: tags=" + learned.tags() + " formulas=" + learned.formulas());
                    helper.assertTrue(reader.getMainHandItem().isEmpty(), "the read transcript was not spent");
                })
                .thenSucceed();
    }
}
