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
}
