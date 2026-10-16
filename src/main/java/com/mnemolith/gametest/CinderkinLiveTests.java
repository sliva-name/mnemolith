package com.mnemolith.gametest;

import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Cinderkin;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A cinderkin with its AI on and a real server player ({@link LivePlayers}) beside it: it finds the player on its own,
 * closes in and its hit sets the player on fire. FakePlayers are not targeted by mobs, so this needs the live player.
 */
final class CinderkinLiveTests {
    private CinderkinLiveTests() {}

    static void cinderkinBurnsPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 17);
        LivePlayers.force(level, site);
        ServerPlayer player = LivePlayers.join(helper, "LiveCinderkinPrey", Vec3.atBottomCenterOf(site));
        Cinderkin[] mob = new Cinderkin[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    player.setGameMode(GameType.SURVIVAL);
                    player.setHealth(player.getMaxHealth());
                    player.clearFire();
                    for (Cinderkin old : level.getEntitiesOfClass(Cinderkin.class, new AABB(site).inflate(32.0D))) {
                        old.discard();
                    }
                    mob[0] = ModEntities.CINDERKIN.get().create(level, EntitySpawnReason.COMMAND);
                    helper.assertTrue(mob[0] != null, "no cinderkin");
                    mob[0].snapTo(site.getX() + 2.5D, site.getY(), site.getZ() + 0.5D, 0.0F, 0.0F);
                    level.addFreshEntity(mob[0]);
                })
                .thenWaitUntil(() -> {
                    player.setHealth(player.getMaxHealth()); // keep the prey alive while it burns
                    helper.assertTrue(player.getRemainingFireTicks() > 0 && player.getLastHurtByMob() == mob[0],
                            "the cinderkin has not set the player alight yet: target=" + mob[0].getTarget());
                })
                .thenExecute(() -> {
                    mob[0].discard();
                    player.clearFire();
                })
                .thenSucceed();
    }

    /**
     * The ember brazier with a real player: an ember fed by hand ({@code gameMode.useItemOn}) lights it and is spent,
     * and the brazier's own scheduled ticks burn the explosion and the build written into its chunk, one charge each.
     */
    static void brazierBurnsDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 18);
        LivePlayers.force(level, site);
        BlockPos pos = site.offset(1, 0, 0);
        ServerPlayer player = LivePlayers.join(helper, "LiveBrazierKeeper", Vec3.atBottomCenterOf(site.offset(0, 0, 2)));
        net.minecraft.world.phys.BlockHitResult top = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).add(0.0D, 0.5D, 0.0D),
                net.minecraft.core.Direction.UP, pos, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    LivePlayers.unmute(level, pos);
                    player.setGameMode(GameType.SURVIVAL);
                    level.setBlock(pos, com.mnemolith.content.ModBlocks.EMBER_BRAZIER.get().defaultBlockState(), 3);
                    com.mnemolith.world.LoadedChunkMemory.clear(level.getChunkAt(pos));
                    com.mnemolith.imprint.ImprintWriter.write(level, pos, java.util.List.of(com.mnemolith.imprint.ImprintTag.EXPLOSION), null, false);
                    com.mnemolith.imprint.ImprintWriter.write(level, pos, java.util.List.of(com.mnemolith.imprint.ImprintTag.BUILD), null, false);
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.item.ItemStack(com.mnemolith.content.ModItems.MEMORY_EMBER.get(), 2));
                    player.gameMode.useItemOn(player, level, player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, top);
                    var state = level.getBlockState(pos);
                    helper.assertTrue(state.getValue(com.mnemolith.content.block.EmberBrazierBlock.LIT)
                            && state.getValue(com.mnemolith.content.block.EmberBrazierBlock.CHARGE) == 4
                            && player.getMainHandItem().getCount() == 1, "feeding by hand: " + state + " left=" + player.getMainHandItem().getCount());
                })
                .thenWaitUntil(() -> {
                    var memory = com.mnemolith.world.LoadedChunkMemory.existing(level.getChunkAt(pos));
                    boolean gone = memory == null || memory.imprintsCopy().stream().noneMatch(i -> i.tag() == com.mnemolith.imprint.ImprintTag.EXPLOSION
                            || i.tag() == com.mnemolith.imprint.ImprintTag.BUILD);
                    int charge = level.getBlockState(pos).getValue(com.mnemolith.content.block.EmberBrazierBlock.CHARGE);
                    helper.assertTrue(gone && charge <= 2, "the brazier has not burned both yet: charge=" + charge);
                })
                .thenExecute(() -> {
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    com.mnemolith.world.LoadedChunkMemory.clear(level.getChunkAt(pos));
                })
                .thenSucceed();
    }
}
