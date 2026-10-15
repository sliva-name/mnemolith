package com.mnemolith.gametest;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The memorial's calm with a real server player ({@link LivePlayers}): a fire residue stands right next to the player
 * beside a memorial stone for two seconds of real ticks and never lashes (the player is not set alight).
 */
final class MemorialLiveTests {
    private MemorialLiveTests() {}

    static void memorialCalmsResidue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 12);
        LivePlayers.force(level, site);
        BlockPos stone = site.offset(3, 0, 0);
        ServerPlayer visitor = LivePlayers.join(helper, "LiveMemorialVisitor", Vec3.atBottomCenterOf(site));
        ResidueEntity[] residue = new ResidueEntity[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    if (!Residues.enabled()) {
                        return;
                    }
                    var chunk = level.getChunkAt(stone);
                    LoadedChunkMemory.clear(chunk);
                    level.setBlockAndUpdate(stone, ModBlocks.MEMORIAL_STONE.get().defaultBlockState());
                    LoadedChunkMemory.getOrCreate(chunk).setScar(new ScarSite(1, level.getGameTime(), 3, level.getGameTime(), true));
                    visitor.clearFire();
                    residue[0] = Residues.spawn(level, site.offset(1, 0, 0), ImprintTag.FIRE, 3, false);
                    helper.assertTrue(residue[0] != null, "no residue spawned");
                    residue[0].setNoAi(true);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    if (residue[0] == null) {
                        return;
                    }
                    boolean calmed = residue[0].calmed();
                    boolean burning = visitor.isOnFire();
                    residue[0].discard();
                    level.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
                    LoadedChunkMemory.clear(level.getChunkAt(stone));
                    visitor.clearFire();
                    helper.assertTrue(calmed, "the residue beside the memorial was not calmed");
                    helper.assertTrue(!burning, "the fire residue lashed the player beside a memorial");
                })
                .thenSucceed();
    }
}
