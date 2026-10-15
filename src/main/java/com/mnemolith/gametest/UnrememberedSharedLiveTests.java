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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Unremembered shared by two real server players ({@link LivePlayers}): called with both near, it rises with the
 * two-player health; both strike it (real attacks); brought down, it leaves one Lethe tear for each of them.
 */
final class UnrememberedSharedLiveTests {
    private UnrememberedSharedLiveTests() {}

    static void scalesAndShares(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 16);
        LivePlayers.force(level, site);
        ServerPlayer first = LivePlayers.join(helper, "LiveSharedFirst", Vec3.atBottomCenterOf(site));
        ServerPlayer second = LivePlayers.join(helper, "LiveSharedSecond", Vec3.atBottomCenterOf(site.offset(0, 0, 4)));
        BlockPos ground = site.offset(2, -1, 2);
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
                                Optional.empty(), Imprint.contextHash(ImprintTag.FORGOTTEN, site, level.getGameTime() + i * 13L), level.getGameTime()), 64);
                    }
                    first.setGameMode(GameType.SURVIVAL);
                    second.setGameMode(GameType.SURVIVAL);
                    first.getInventory().clearContent();
                    second.getInventory().clearContent();
                    first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HOLLOW_EFFIGY.get()));
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false);
                    first.gameMode.useItemOn(first, level, first.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    var risen = level.getEntitiesOfClass(Unremembered.class, new AABB(ground).inflate(4.0D));
                    helper.assertTrue(risen.size() == 1, "expected one Unremembered, found " + risen.size());
                    boss[0] = risen.get(0);
                    boss[0].setNoAi(true);
                    helper.assertTrue(boss[0].scaledFor() == 2 && Math.abs(boss[0].getMaxHealth() - Unremembered.healthFor(2)) < 0.01F,
                            "two players should face two-player health: scaledFor=" + boss[0].scaledFor() + " max=" + boss[0].getMaxHealth());
                    first.attack(boss[0]);
                    second.resetAttackStrengthTicker();
                    second.attack(boss[0]);
                    helper.assertTrue(boss[0].fighters().size() == 2, "both strikes should count, fighters=" + boss[0].fighters().size());
                    boss[0].setHealth(1.0F);
                    first.resetAttackStrengthTicker();
                    boss[0].hurtServer(level, level.damageSources().playerAttack(first), 50.0F);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    Unremembered u = boss[0];
                    int tears = 0;
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(u.blockPosition()).inflate(6.0D))) {
                        tears += item.getItem().is(ModItems.LETHE_TEAR.get()) ? item.getItem().getCount() : 0;
                        item.discard();
                    }
                    boolean dead = u.isDeadOrDying() || u.isRemoved();
                    if (!u.isRemoved()) {
                        u.discard();
                    }
                    LoadedChunkMemory.clear(level.getChunkAt(ground.above()));
                    helper.assertTrue(dead, "the Unremembered should be down");
                    helper.assertTrue(tears == 2, "one tear for each fighter, found " + tears);
                })
                .thenSucceed();
    }
}
