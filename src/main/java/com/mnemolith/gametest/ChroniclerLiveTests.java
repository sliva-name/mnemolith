package com.mnemolith.gametest;

import com.mnemolith.content.villager.ChroniclerOffers;
import com.mnemolith.data.Transcript;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;

/**
 * The chronicler's copied notes with a real server player ({@link LivePlayers}) and a real chronicler villager in the
 * world: the journeyman offers the sheet (spawned again until it does), the trade is taken through the offer, and the
 * player reads the sheet through the real item use and learns the five body tags.
 */
final class ChroniclerLiveTests {
    private ChroniclerLiveTests() {}

    static void chroniclerSellsNotes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 9);
        LivePlayers.force(level, site);
        ServerPlayer buyer = LivePlayers.join(helper, "LiveChroniclerBuyer", Vec3.atBottomCenterOf(site));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    MerchantOffer offer = null;
                    Villager seller = null;
                    for (int i = 0; i < 30 && offer == null; i++) {
                        if (seller != null) {
                            seller.discard();
                        }
                        seller = ChroniclerOffers.chronicler(level, site.offset(2, 0, 0), 3, true);
                        offer = ChroniclerOffers.transcriptOffer(seller);
                    }
                    helper.assertTrue(offer != null, "no journeyman chronicler offered the copied notes in 30 tries");
                    buyer.setData(ModAttachments.DISCOVERY.get(), new Discovery());
                    buyer.getInventory().clearContent();
                    int usesBefore = offer.getUses();
                    seller.notifyTrade(offer);
                    buyer.setItemInHand(InteractionHand.MAIN_HAND, offer.assemble());
                    seller.discard();
                    helper.assertTrue(offer.getUses() == usesBefore + 1, "the trade did not count a use");
                    buyer.gameMode.useItem(buyer, level, buyer.getMainHandItem(), InteractionHand.MAIN_HAND);
                    Discovery learned = buyer.getData(ModAttachments.DISCOVERY.get());
                    helper.assertTrue(learned.tags() == Transcript.CHRONICLER_TAGS && learned.formulas() == 0,
                            "the buyer did not learn the five body tags: tags=" + learned.tags() + " formulas=" + learned.formulas());
                    helper.assertTrue(buyer.getMainHandItem().isEmpty(), "the read sheet was not spent");
                })
                .thenSucceed();
    }
}
