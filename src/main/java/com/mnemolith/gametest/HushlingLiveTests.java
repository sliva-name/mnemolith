package com.mnemolith.gametest;

import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Hush;
import com.mnemolith.entity.mob.Hushling;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A hushling with its AI on and a real server player (live site 20): it finds the player by itself and its hit hushes
 * them. FakePlayers are not targeted by mobs, so this needs the live player.
 */
final class HushlingLiveTests {
    private HushlingLiveTests() {}

    static void hushlingHushesPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 20);
        LivePlayers.force(level, site);
        ServerPlayer player = LivePlayers.join(helper, "LiveHushPrey", Vec3.atBottomCenterOf(site));
        Hushling[] mob = new Hushling[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    player.setGameMode(GameType.SURVIVAL);
                    player.removeAllEffects();
                    Hush.forget(player.getUUID());
                    for (Hushling old : level.getEntitiesOfClass(Hushling.class, new AABB(site).inflate(32.0D))) {
                        old.discard();
                    }
                    mob[0] = ModEntities.HUSHLING.get().create(level, EntitySpawnReason.COMMAND);
                    helper.assertTrue(mob[0] != null, "no hushling");
                    mob[0].snapTo(site.getX() + 2.5D, site.getY(), site.getZ() + 0.5D, 0.0F, 0.0F);
                    level.addFreshEntity(mob[0]);
                })
                .thenWaitUntil(() -> {
                    player.setHealth(player.getMaxHealth());
                    helper.assertTrue(Hush.hushed(level, player.getUUID()) && player.getLastHurtByMob() == mob[0],
                            "the hushling has not hushed the player yet: target=" + mob[0].getTarget());
                })
                .thenExecute(() -> {
                    mob[0].discard();
                    Hush.forget(player.getUUID());
                    player.removeAllEffects();
                })
                .thenSucceed();
    }
}
