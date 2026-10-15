package com.mnemolith.gametest;

import com.mnemolith.command.qa.LullQaAccess;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.echo.storm.StormData;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.pressure.PressureBand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The storm lull with a real server player ({@link LivePlayers}): a storm gathered over the player's fracture is spent,
 * and the residual shard the player then frees there through the real item use no longer calls a new storm.
 */
final class LullLiveTests {
    private LullLiveTests() {}

    static void stormLullRefusesShard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 8);
        LivePlayers.force(level, site);
        ServerPlayer player = LivePlayers.join(helper, "LiveLull", Vec3.atBottomCenterOf(site.offset(2, 0, 2)));
        ChunkPos chunk = ChunkPos.containing(site);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    if (!Storms.enabled() || Storms.maxPerDimension() <= 0) {
                        return;
                    }
                    LivePlayers.unmute(level, site);
                    LullQaAccess.resetArea(level, site);
                    StormData data = StormData.get(level.getServer());
                    data.removeLulls(lull -> Math.abs(lull.chunkX() - chunk.x()) <= 4 && Math.abs(lull.chunkZ() - chunk.z()) <= 4);
                    PressureBand band = LullQaAccess.loud(level, site, PressureBand.FRACTURE);
                    Storms.Gate open = Storms.gate(level, chunk);
                    helper.assertTrue(open == Storms.Gate.OK, "the fracture gate was not open: " + open + " band=" + band);
                    Storms.finish(level, Storms.start(level, chunk, "live"), Storms.End.SPENT);
                    helper.assertTrue(Storms.gate(level, chunk) == Storms.Gate.LULL, "no lull after the spent storm: " + Storms.gate(level, chunk));
                    player.setItemInHand(InteractionHand.MAIN_HAND, Residues.shard(ImprintTag.FALL, 4, site, level.getGameTime()));
                    BlockPos ground = site.below();
                    player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false)));
                    var storm = Storms.at(level, chunk);
                    if (storm != null) {
                        Storms.finish(level, storm, Storms.End.PASSED);
                    }
                    data.removeLulls(lull -> Math.abs(lull.chunkX() - chunk.x()) <= 4 && Math.abs(lull.chunkZ() - chunk.z()) <= 4);
                    LullQaAccess.resetArea(level, site);
                    helper.assertTrue(storm == null, "the freed shard called a storm during the lull");
                })
                .thenSucceed();
    }
}
