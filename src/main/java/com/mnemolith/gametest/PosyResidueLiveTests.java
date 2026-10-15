package com.mnemolith.gametest;

import com.mnemolith.content.ModItems;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The remembrance posy on a residual echo with a real server player ({@link LivePlayers}): the player right-clicks an
 * unread residue with a posy through the real entity interaction; it is laid to rest and one posy is spent.
 */
final class PosyResidueLiveTests {
    private PosyResidueLiveTests() {}

    static void posyRestsResidue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 10);
        LivePlayers.force(level, site);
        ServerPlayer giver = LivePlayers.join(helper, "LivePosyResidue", Vec3.atBottomCenterOf(site));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    if (!Residues.enabled()) {
                        return;
                    }
                    ResidueEntity residue = Residues.spawn(level, site.offset(2, 0, 0), ImprintTag.FIRE, 3, false);
                    helper.assertTrue(residue != null, "no residue spawned");
                    residue.setNoAi(true);
                    giver.getInventory().clearContent();
                    giver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEMBRANCE_POSY.get(), 2));
                    giver.interactOn(residue, InteractionHand.MAIN_HAND, residue.position());
                    boolean gone = residue.isRemoved();
                    if (!gone) {
                        residue.discard();
                    }
                    helper.assertTrue(gone, "the posy did not lay the residue to rest");
                    helper.assertTrue(giver.getMainHandItem().getCount() == 1, "the posy was not spent: " + giver.getMainHandItem().getCount());
                })
                .thenSucceed();
    }
}
