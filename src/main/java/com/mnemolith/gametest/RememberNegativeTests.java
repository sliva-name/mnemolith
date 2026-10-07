package com.mnemolith.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.storm.RecollectionStorm;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.entity.echo.PastSelf;
import com.mnemolith.recall.LifeMoment;
import com.mnemolith.recall.LifeMomentKind;
import com.mnemolith.recall.LifeMoments;
import com.mnemolith.recall.LivingMemory;
import com.mnemolith.recall.RememberYou;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * "The world remembers you" must stay quiet when the player cannot or should not see a scene: creative, spectator,
 * a menu open, a storm overhead, a scene just played, the shared wow cooldown, dead, the feature off, and never for a
 * fake player through the real tick. The roll is forced to 100% so only the guard can hold a scene back, and one
 * unguarded return plays it (the positive control).
 */
final class RememberNegativeTests {
    private RememberNegativeTests() {}

    private static final int LANE = 65;

    static void blocked(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(0);
            FakePlayer player = support.player("NegRemember", at.offset(10, 0, 0));
            LifeMoments moments = LifeMoments.get(level.getServer());
            String dimension = level.dimension().identifier().toString();
            double chance = CommonConfig.REMEMBER_SCENE_CHANCE.get();
            boolean skipCreative = CommonConfig.RECALL_SKIP_CREATIVE.get();
            boolean skipStorm = CommonConfig.RECALL_SKIP_STORM.get();
            try {
                CommonConfig.REMEMBER_SCENE_CHANCE.set(1.0D);
                CommonConfig.RECALL_SKIP_CREATIVE.set(true);
                CommonConfig.RECALL_SKIP_STORM.set(true);
                List<Case> cases = List.of(
                        new Case("creative", p -> p.setGameMode(GameType.CREATIVE), p -> p.setGameMode(GameType.SURVIVAL)),
                        new Case("spectator", p -> p.setGameMode(GameType.SPECTATOR), p -> p.setGameMode(GameType.SURVIVAL)),
                        new Case("a menu open", p -> p.containerMenu = ChestMenu.threeRows(9, p.getInventory()), p -> p.containerMenu = p.inventoryMenu),
                        new Case("a scene just played", p -> moments.markScene(p.getUUID(), level.getGameTime()), p -> {}),
                        new Case("the wow cooldown", p -> LivingMemory.log(p).markWow(level.getGameTime()), p -> {}));
                for (Case c : cases) {
                    reset(player, moments, dimension, at, level);
                    c.apply.accept(player);
                    try {
                        assertQuiet(helper, level, player, moments, at, c.name);
                    } finally {
                        c.undo.accept(player);
                    }
                }

                // Dead (not respawned yet): no scene, no replay counted.
                reset(player, moments, dimension, at, level);
                player.setHealth(0.0F);
                try {
                    helper.assertTrue(RememberYou.consider(player).isEmpty() && scenes(level, at) == 0 && moments.of(player.getUUID()).get(0).replays() == 0,
                            "a scene started for a dead player");
                } finally {
                    player.setHealth(player.getMaxHealth());
                }

                // A storm over the player's chunk.
                reset(player, moments, dimension, at, level);
                RecollectionStorm storm = Storms.start(level, ChunkPos.containing(player.blockPosition()), "negative-test");
                try {
                    helper.assertTrue(Storms.at(level, ChunkPos.containing(player.blockPosition())) != null, "the test storm does not cover the player");
                    assertQuiet(helper, level, player, moments, at, "a storm overhead");
                } finally {
                    Storms.finish(level, storm, Storms.End.DISABLED);
                }

                // The feature switched off.
                reset(player, moments, dimension, at, level);
                boolean enabled = CommonConfig.REMEMBER_ENABLED.get();
                try {
                    CommonConfig.REMEMBER_ENABLED.set(false);
                    helper.assertTrue(RememberYou.consider(player).isEmpty() && scenes(level, at) == 0, "a scene played with the feature off");
                } finally {
                    CommonConfig.REMEMBER_ENABLED.set(enabled);
                }

                // The real per-player tick ignores fake players entirely (no scene, no visit marked).
                reset(player, moments, dimension, at, level);
                long lastNear = moments.of(player.getUUID()).get(0).lastNear();
                for (int i = 0; i < 80; i++) {
                    player.tickCount = i;
                    RememberYou.tick(player);
                }
                helper.assertTrue(scenes(level, at) == 0 && moments.of(player.getUUID()).get(0).lastNear() == lastNear, "the real tick ran a fake player's return check");

                // Positive control: nothing in the way, and the same return plays its scene.
                reset(player, moments, dimension, at, level);
                var body = RememberYou.consider(player);
                helper.assertTrue(body.isPresent(), "an unguarded return did not play its scene (the test set-up is wrong)");
                body.get().discard();
            } finally {
                CommonConfig.REMEMBER_SCENE_CHANCE.set(chance);
                CommonConfig.RECALL_SKIP_CREATIVE.set(skipCreative);
                CommonConfig.RECALL_SKIP_STORM.set(skipStorm);
                moments.forget(player.getUUID());
                RememberYou.forgetSession(player.getUUID());
                for (PastSelf scene : level.getEntitiesOfClass(PastSelf.class, new AABB(at).inflate(32.0D))) {
                    scene.discard();
                }
            }
            helper.succeed();
        }
    }

    private record Case(String name, Consumer<FakePlayer> apply, Consumer<FakePlayer> undo) {}

    /** One old death at {@code at}, the player ten blocks away, every cooldown long past, no scene running. */
    private static void reset(FakePlayer player, LifeMoments moments, String dimension, BlockPos at, ServerLevel level) {
        moments.forget(player.getUUID());
        RememberYou.forgetSession(player.getUUID());
        LivingMemory.log(player).markWow(-1_000_000L);
        long now = level.getGameTime();
        long old = now - CommonConfig.REMEMBER_AWAY_TICKS.get() - 400L;
        moments.add(player.getUUID(), new LifeMoment(LifeMomentKind.DEATH, dimension, at, 0.0F, "minecraft:iron_sword", "", old, old, 1, 0, true), 24);
        player.setPos(at.getX() + 10.5D, at.getY(), at.getZ() + 0.5D);
    }

    private static void assertQuiet(GameTestHelper helper, ServerLevel level, FakePlayer player, LifeMoments moments, BlockPos at, String why) {
        helper.assertTrue(RememberYou.candidate(player, level.getGameTime()).isPresent(), "no candidate moment for the '" + why + "' case (the test set-up is wrong)");
        helper.assertTrue(!RememberYou.ready(player, level.getGameTime()), "a scene may start with " + why);
        helper.assertTrue(RememberYou.consider(player).isEmpty(), "a scene started with " + why);
        helper.assertTrue(scenes(level, at) == 0 && moments.of(player.getUUID()).get(0).replays() == 0, "a past self appeared, or a replay was counted, with " + why);
    }

    private static int scenes(ServerLevel level, BlockPos at) {
        return level.getEntitiesOfClass(PastSelf.class, new AABB(at).inflate(32.0D), e -> !e.isRemoved()).size();
    }
}
