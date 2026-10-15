package com.mnemolith.gametest;

import java.util.Optional;

import com.mnemolith.content.ModItems;
import com.mnemolith.entity.boss.Unremembered;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Unremembered between two real server players ({@link LivePlayers}): the caller uses a hollow effigy on the
 * ground of a prepared hollow (real block use); the boss rises, the effigy is spent, both players are on its boss bar,
 * and its forgetting pulse darkens both.
 */
final class UnrememberedLiveTests {
    private UnrememberedLiveTests() {}

    static void calledAndSeen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 14);
        LivePlayers.force(level, site);
        ServerPlayer caller = LivePlayers.join(helper, "LiveUnrememberedCaller", Vec3.atBottomCenterOf(site));
        ServerPlayer witness = LivePlayers.join(helper, "LiveUnrememberedWitness", Vec3.atBottomCenterOf(site.offset(0, 0, 3)));
        BlockPos ground = site.offset(3, -1, 0);
        Unremembered[] boss = new Unremembered[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var server = level.getServer();
                    var console = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
                    try {
                        server.getCommands().getDispatcher().execute(String.format("fillbiome %d %d %d %d %d %d mnemolith:memory_hollows",
                                site.getX() - 24, site.getY() - 4, site.getZ() - 24, site.getX() + 24, site.getY() + 4, site.getZ() + 24), console);
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        throw new IllegalStateException(e);
                    }
                    for (Unremembered old : level.getEntitiesOfClass(Unremembered.class, new AABB(site).inflate(80.0D))) {
                        old.discard();
                    }
                    var chunk = level.getChunkAt(ground.above());
                    LoadedChunkMemory.clear(chunk);
                    var memory = LoadedChunkMemory.getOrCreate(chunk);
                    for (int i = 0; i < 3; i++) {
                        memory.addImprint(new Imprint(ImprintTag.FORGOTTEN, ImprintWriter.intensityFor(ImprintTag.FORGOTTEN), site.offset(i, 0, 0),
                                Optional.empty(), Imprint.contextHash(ImprintTag.FORGOTTEN, site, level.getGameTime() + i * 17L), level.getGameTime()), 64);
                    }
                    caller.setGameMode(GameType.SURVIVAL);
                    witness.setGameMode(GameType.SURVIVAL);
                    caller.getInventory().clearContent();
                    caller.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HOLLOW_EFFIGY.get()));
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false);
                    caller.gameMode.useItemOn(caller, level, caller.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    var risen = level.getEntitiesOfClass(Unremembered.class, new AABB(ground).inflate(4.0D));
                    helper.assertTrue(caller.getMainHandItem().isEmpty(), "the effigy was not spent");
                    helper.assertTrue(risen.size() == 1, "expected one Unremembered, found " + risen.size());
                    boss[0] = risen.get(0);
                    boss[0].setNoAi(true);
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    Unremembered u = boss[0];
                    var bar = java.util.List.copyOf(u.bossEvent().getPlayers());
                    int touched = u.pulse(level);
                    boolean dark = caller.hasEffect(MobEffects.DARKNESS) && witness.hasEffect(MobEffects.DARKNESS);
                    u.discard();
                    LoadedChunkMemory.clear(level.getChunkAt(ground.above()));
                    helper.assertTrue(bar.contains(caller) && bar.contains(witness), "both players should see the boss bar, saw " + bar.size() + " removed=" + u.getRemovalReason() + " d=" + u.distanceTo(caller) + " diff=" + level.getDifficulty() + " ticks=" + u.tickCount);
                    helper.assertTrue(touched == 2 && dark, "the pulse should darken both players: touched=" + touched);
                })
                .thenSucceed();
    }
}
