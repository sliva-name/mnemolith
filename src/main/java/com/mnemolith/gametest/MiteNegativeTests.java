package com.mnemolith.gametest;

import com.mnemolith.content.ModItems;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.LedgerMite;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A tame ledger mite whose owner is dead must leave shards on the ground: a dead player is still in the level until
 * they respawn, and what lands in that inventory is gone. A second mite with a living owner, in the same world at the
 * same moment, is the control that proves mites gather here at all.
 */
final class MiteNegativeTests {
    private MiteNegativeTests() {}

    static void deadOwnerGetsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, origin.getX() >> 4, (origin.getZ() >> 4) + 130);
        LivePlayers.force(level, site);
        LivePlayers.force(level, site.offset(16, 0, 0));
        BlockPos deadSpot = site;
        BlockPos aliveSpot = site.offset(14, 0, 0);
        ServerPlayer dead = LivePlayers.join(helper, "NegMiteDeadOwner", Vec3.atBottomCenterOf(deadSpot.offset(0, 0, 6)));
        ServerPlayer alive = LivePlayers.join(helper, "NegMiteLiveOwner", Vec3.atBottomCenterOf(aliveSpot.offset(0, 0, 6)));
        dead.getInventory().clearContent();
        alive.getInventory().clearContent();
        LedgerMite deadMite = mite(helper, level, deadSpot, dead);
        LedgerMite aliveMite = mite(helper, level, aliveSpot, alive);
        ItemEntity[] dropped = new ItemEntity[2];
        helper.startSequence()
                .thenExecute(() -> {
                    dead.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
                    helper.assertTrue(!dead.isAlive() && !dead.isRemoved(), "the owner should be dead but still in the level");
                    dead.getInventory().clearContent();
                    dropped[0] = shard(level, deadMite);
                    dropped[1] = shard(level, aliveMite);
                })
                .thenWaitUntil(() -> helper.assertTrue(alive.getInventory().countItem(ModItems.RESIDUAL_SHARD.get()) == 1,
                        "control: the living owner's mite did not gather its shard"))
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(dead.getInventory().countItem(ModItems.RESIDUAL_SHARD.get()) == 0,
                            "the mite put a shard into its dead owner's inventory, where it is lost");
                    helper.assertTrue(dropped[0].isAlive() && dropped[0].getItem().getCount() == 1,
                            "the shard beside the dead owner's mite left the ground");
                    deadMite.discard();
                    aliveMite.discard();
                    dropped[0].discard();
                })
                .thenSucceed();
    }

    private static LedgerMite mite(GameTestHelper helper, ServerLevel level, BlockPos at, ServerPlayer owner) {
        LedgerMite mite = ModEntities.LEDGER_MITE.get().create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(mite != null, "no ledger mite");
        mite.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        // Stays where it is put; its own server tick (and so its gathering) still runs.
        mite.setNoAi(true);
        mite.setPersistenceRequired();
        mite.tame(owner);
        helper.assertTrue(level.addFreshEntity(mite), "the mite was not added");
        return mite;
    }

    private static ItemEntity shard(ServerLevel level, LedgerMite mite) {
        ItemEntity item = new ItemEntity(level, mite.getX() + 1.0D, mite.getY(), mite.getZ(), new ItemStack(ModItems.RESIDUAL_SHARD.get()));
        item.setDeltaMovement(Vec3.ZERO);
        item.setPickUpDelay(40);
        level.addFreshEntity(item);
        return item;
    }
}
