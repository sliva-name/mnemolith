package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.entity.echo.PastSelf;
import com.mnemolith.recall.LifeMoment;
import com.mnemolith.recall.LifeMomentKind;
import com.mnemolith.recall.LifeMoments;
import com.mnemolith.recall.LivingMemory;
import com.mnemolith.recall.RememberYou;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "The world remembers you" against real server players ({@link LivePlayers}): real block placing, sleeping, a kill and
 * a death are recorded through the game's own events; a player who comes back to an old place after being away sees
 * their past self there, through the real player tick, and a second player standing beside them is not sent it.
 */
final class RememberLiveTests {
    private RememberLiveTests() {}

    static BlockPos site(GameTestHelper helper, int index) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos pos = LivePlayers.surface(level, (origin.getX() >> 4) + 6 * index, (origin.getZ() >> 4) + 90);
        LivePlayers.force(level, pos);
        ChunkPos center = ChunkPos.containing(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LoadedChunkMemory.clear(level.getChunk(center.x() + dx, center.z() + dz));
            }
        }
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                level.setBlock(pos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
                for (int dy = 0; dy <= 6; dy++) {
                    level.setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
        return pos;
    }

    /** Places 64 blocks, sleeps in a bed, kills a strong zombie and dies: one build, one home, one fight, one death. */
    static void recordsRealEvents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, 0);
        ServerPlayer player = LivePlayers.join(helper, "LiveRememberLife", Vec3.atBottomCenterOf(site.offset(0, 0, -10)));
        LifeMoments moments = LifeMoments.get(level.getServer());
        moments.forget(player.getUUID());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    // A floor of 8 by 8 cobblestone, block by block, the way a client's clicks place it.
                    int placed = 0;
                    for (int dx = 0; dx < 8; dx++) {
                        for (int dz = 0; dz < 8; dz++) {
                            BlockPos target = site.offset(dx - 4, 0, dz - 4);
                            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 64));
                            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target.below()).add(0.0D, 0.5D, 0.0D), Direction.UP, target.below(), false);
                            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                            if (level.getBlockState(target).is(Blocks.COBBLESTONE)) {
                                placed++;
                            }
                        }
                    }
                    helper.assertTrue(placed == 64, "placed " + placed + " of 64 blocks");
                    List<LifeMoment> builds = moments.of(player.getUUID()).stream().filter(m -> m.kind() == LifeMomentKind.BUILD).toList();
                    helper.assertTrue(builds.size() == 1 && builds.get(0).itemId().equals("minecraft:cobblestone") && builds.get(0).pos().distSqr(site) <= 4.0D,
                            "the floor was not remembered as one cobblestone build near the site: " + builds);

                    // A bed: lie down and get up.
                    BlockPos head = site.offset(8, 0, 6);
                    BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
                    level.setBlock(head.west(), bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
                    level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
                    player.startSleeping(head);
                    player.stopSleepInBed(false, true);
                    List<LifeMoment> homes = moments.of(player.getUUID()).stream().filter(m -> m.kind() == LifeMomentKind.HOME).toList();
                    helper.assertTrue(homes.size() == 1 && homes.get(0).pos().equals(head), "the bed was not remembered as a home: " + homes);

                    // A strong zombie, killed by the player. A plain one is not a fight.
                    Zombie plain = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                    Zombie strong = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                    helper.assertTrue(plain != null && strong != null, "no zombie");
                    strong.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80.0D);
                    strong.setHealth(80.0F);
                    plain.snapTo(Vec3.atBottomCenterOf(site.offset(-8, 0, 8)));
                    strong.snapTo(Vec3.atBottomCenterOf(site.offset(-8, 0, -8)));
                    level.addFreshEntity(plain);
                    level.addFreshEntity(strong);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                    plain.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
                    strong.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
                    List<LifeMoment> fights = moments.of(player.getUUID()).stream().filter(m -> m.kind() == LifeMomentKind.BATTLE).toList();
                    helper.assertTrue(fights.size() == 1 && fights.get(0).detail().equals("minecraft:zombie") && fights.get(0).itemId().equals("minecraft:iron_sword")
                            && fights.get(0).pos().distSqr(site.offset(-8, 0, -8)) <= 2.0D, "the fight was not remembered once, with the sword: " + fights);

                    // And a death.
                    BlockPos dying = player.blockPosition();
                    player.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
                    helper.assertTrue(player.isDeadOrDying(), "the player did not die");
                    List<LifeMoment> deaths = moments.of(player.getUUID()).stream().filter(m -> m.kind() == LifeMomentKind.DEATH).toList();
                    helper.assertTrue(deaths.size() == 1 && deaths.get(0).pos().equals(dying) && deaths.get(0).first(), "the death was not remembered: " + deaths);
                    moments.forget(player.getUUID());
                })
                .thenSucceed();
    }

    /**
     * A remembered death; the player was away long ago and now stands ten blocks from it, with a friend beside them.
     * Through the real player tick the past self appears for the owner only, plays the death without hurting anyone,
     * and is gone; the place counts one replay.
     */
    static void sceneOnReturn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, 1);
        ServerPlayer owner = LivePlayers.join(helper, "LiveRememberOwner", Vec3.atBottomCenterOf(site.offset(10, 0, 0)));
        ServerPlayer friend = LivePlayers.join(helper, "LiveRememberFriend", Vec3.atBottomCenterOf(site.offset(10, 0, 2)));
        LifeMoments moments = LifeMoments.get(level.getServer());
        moments.forget(owner.getUUID());
        moments.forget(friend.getUUID());
        // No wow moment before this one (the test world is young: game time may be under the stage-1 cooldown).
        LivingMemory.log(owner).markWow(-1_000_000L);
        LivingMemory.log(friend).markWow(-1_000_000L);
        RememberYou.record(owner, LifeMomentKind.DEATH, site, 90.0F, "minecraft:iron_sword", "minecraft:zombie");
        float health = owner.getHealth();
        PastSelf[] seen = new PastSelf[1];
        helper.onEachTick(() -> {
            if (seen[0] != null) {
                return;
            }
            // Keep the absence long ago: a failed roll counts as a visit, so put it back until the scene shows.
            for (LifeMoment moment : moments.of(owner.getUUID())) {
                if (moment.replays() == 0) {
                    long old = level.getGameTime() - 100_000L;
                    moments.replace(owner.getUUID(), moment, new LifeMoment(moment.kind(), moment.dimension(), moment.pos(), moment.yaw(),
                            moment.itemId(), moment.detail(), old, old, moment.count(), 0, moment.first()));
                }
            }
            List<PastSelf> bodies = level.getEntitiesOfClass(PastSelf.class, new AABB(site).inflate(6.0D));
            if (!bodies.isEmpty()) {
                seen[0] = bodies.get(0);
            }
        });
        helper.succeedWhen(() -> {
            PastSelf body = seen[0];
            helper.assertTrue(body != null, "no past self yet");
            helper.assertTrue(owner.getUUID().equals(body.ownerId()) && body.kind() == LifeMomentKind.DEATH, "the wrong scene: " + body.ownerId() + " " + body.kind());
            helper.assertTrue(body.broadcastToPlayer(owner) && !body.broadcastToPlayer(friend), "the scene is not owner-only");
            helper.assertTrue(body.getMainHandItem().is(Items.IRON_SWORD), "the past self does not hold the remembered sword");
            helper.assertTrue(body.isRemoved(), "the scene is still playing");
            helper.assertTrue(owner.getHealth() == health && friend.getHealth() == friend.getMaxHealth(), "someone was hurt by the scene");
            List<LifeMoment> after = moments.of(owner.getUUID());
            helper.assertTrue(after.size() == 1 && after.get(0).replays() == 1, "the place did not count one replay: " + after);
            helper.assertTrue(level.getEntitiesOfClass(PastSelf.class, new AABB(site).inflate(24.0D)).isEmpty(), "another past self appeared");
            moments.forget(owner.getUUID());
            moments.forget(friend.getUUID());
        });
    }
}
