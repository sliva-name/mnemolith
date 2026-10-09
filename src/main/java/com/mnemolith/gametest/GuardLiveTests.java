package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.echo.CareLesson;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.FarmLesson;
import com.mnemolith.echo.GuardLesson;
import com.mnemolith.echo.LumberLesson;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** The echo guard on the real server tick: it walks to a hostile mob near its post and kills it, sparing bystanders. */
final class GuardLiveTests {
    private GuardLiveTests() {}

    /**
     * A guard with an iron sword and a husk (no sunburn) four blocks from its post, a villager and a cow beside it. The
     * husk dies by the echo's hand, the bystanders keep full health, the sword lost durability, and the echo is back on
     * guard.
     */
    static void guardKillsHuskSparesBystanders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, (origin.getX() >> 4) - 8, (origin.getZ() >> 4) + 14);
        // The fight drifts (knockback, a husk that walks): keep the 3x3 chunks around the post entity-ticking.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LivePlayers.force(level, site.offset(dx * 16, 0, dz * 16));
            }
        }
        LoadedChunkMemory.clear(level.getChunkAt(site));
        ServerPlayer owner = LivePlayers.join(helper, "LiveGuardOwner", Vec3.atBottomCenterOf(site.offset(-5, 0, -5)));
        owner.setGameMode(GameType.CREATIVE);
        Vec3 at = Vec3.atBottomCenterOf(site);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, at, at.x, at.y, at.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        EchoRecording recording = new EchoRecording(owner.getUUID(), owner.getGameProfile().name(), level.dimension(), at, buffer.array(), List.of());
        EchoEntity echo = EchoLife.spawn(level, owner, recording, EchoLesson.NONE, FarmLesson.NONE, LumberLesson.NONE, CareLesson.NONE,
                new GuardLesson(List.of(EntityTypes.ZOMBIE), 3, 0));
        helper.assertTrue(echo != null, "no echo");
        echo.stopReplay();
        echo.snapTo(at, 0.0F, 0.0F);
        echo.inventory().insert(new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(echo.job().startGuarding(echo), "guard did not start: " + echo.job().status().kind().getSerializedName());
        Mob husk = mob(level, EntityTypes.HUSK, site.offset(4, 0, 0));
        Mob villager = mob(level, EntityTypes.VILLAGER, site.offset(-2, 0, 2));
        Mob cow = mob(level, EntityTypes.COW, site.offset(-2, 0, -2));
        float villagerHp = villager.getHealth();
        float cowHp = cow.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "husk still alive, health " + husk.getHealth() + " status " + echo.job().status().kind().getSerializedName()
                    + " husk at " + husk.position() + " echo at " + echo.position() + " echoTicks " + echo.tickCount + " huskTicks " + husk.tickCount + " huskInvul " + husk.invulnerableTime + " target "
                    + (echo.job().guardTarget() == husk) + " difficulty " + level.getDifficulty() + " " + echo.job().describe());
            helper.assertTrue(echo.job().defeated() >= 1, "the husk died, but not by the guard");
            helper.assertTrue(villager.isAlive() && villager.getHealth() >= villagerHp, "the villager was hurt");
            helper.assertTrue(cow.isAlive() && cow.getHealth() >= cowHp, "the cow was hurt");
            ItemStack sword = echo.inventory().getItem(echo.selectedSlot());
            helper.assertTrue(sword.is(Items.IRON_SWORD) && sword.getDamageValue() > 0, "the sword lost no durability");
            helper.assertTrue(echo.job().mode() == EchoJob.Mode.GUARD, "the echo left its post: " + echo.job().mode());
            husk.discard();
            villager.discard();
            cow.discard();
            echo.discardSilently();
        });
    }

    /**
     * A pure archer (a bow and 16 arrows, no melee weapon) and a husk seven blocks west, with a villager standing in
     * the line of fire. While the villager is there no arrow flies; once it steps aside the archer shoots the husk
     * dead. The villager is never hurt, and arrows were spent from the echo's inventory.
     */
    static void guardArcherHoldsFireThenShoots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, -12);
        ServerPlayer owner = LivePlayers.join(helper, "LiveArcherOwner", Vec3.atBottomCenterOf(site.offset(3, 0, 5)));
        owner.setGameMode(GameType.CREATIVE);
        EchoEntity echo = guard(helper, level, owner, site);
        echo.inventory().insert(new ItemStack(Items.BOW));
        echo.inventory().insert(new ItemStack(Items.ARROW, 16));
        helper.assertTrue(echo.job().startGuarding(echo), "archer did not start: " + echo.job().status().kind().getSerializedName());
        Mob husk = mob(level, EntityTypes.HUSK, site.offset(-7, 0, 0));
        husk.setNoAi(true);
        Mob villager = mob(level, EntityTypes.VILLAGER, site.offset(-3, 0, 0));
        villager.setNoAi(true);
        float villagerHp = villager.getHealth();
        int[] shotsWhileBlocked = {-1};
        helper.runAfterDelay(80, () -> {
            shotsWhileBlocked[0] = echo.job().guardShots();
            villager.snapTo(Vec3.atBottomCenterOf(site.offset(-3, 0, 4)), 0.0F, 0.0F);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shotsWhileBlocked[0] == 0, "shots while the villager stood in the line: " + shotsWhileBlocked[0]);
            helper.assertTrue(!husk.isAlive(), "husk still alive, health " + husk.getHealth() + " shots " + echo.job().guardShots() + " " + echo.job().describe());
            helper.assertTrue(echo.job().defeated() >= 1, "the husk died, but not by the archer");
            helper.assertTrue(villager.isAlive() && villager.getHealth() >= villagerHp, "the villager was hurt");
            int arrows = 0;
            for (int i = 0; i < echo.inventory().getContainerSize(); i++) {
                if (echo.inventory().getItem(i).is(Items.ARROW)) {
                    arrows += echo.inventory().getItem(i).getCount();
                }
            }
            helper.assertTrue(arrows == 16 - echo.job().guardShots() && echo.job().guardShots() > 0, "arrows " + arrows + " shots " + echo.job().guardShots());
            husk.discard();
            villager.discard();
            echo.discardSilently();
        });
    }

    /**
     * A guard with a sword and a shield walks toward a zombie: the shield goes up, and a frontal hit while it is up
     * does no damage and wears the shield.
     */
    static void guardShieldBlocksFrontalHit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, -16);
        ServerPlayer owner = LivePlayers.join(helper, "LiveShieldOwner", Vec3.atBottomCenterOf(site.offset(-5, 0, -5)));
        owner.setGameMode(GameType.CREATIVE);
        EchoEntity echo = guard(helper, level, owner, site);
        echo.inventory().insert(new ItemStack(Items.IRON_SWORD));
        echo.inventory().insert(new ItemStack(Items.SHIELD));
        helper.assertTrue(echo.job().startGuarding(echo), "guard did not start");
        Mob zombie = mob(level, EntityTypes.ZOMBIE, site.offset(6, 0, 0));
        zombie.setNoAi(true);
        float[] healthAfter = {-1.0F};
        helper.succeedWhen(() -> {
            if (healthAfter[0] < 0.0F && echo.isBlocking()) {
                echo.invulnerableTime = 0;
                float before = echo.getHealth();
                echo.hurtServer(level, echo.damageSources().mobAttack(zombie), 4.0F);
                healthAfter[0] = echo.getHealth() - before;
            }
            helper.assertTrue(healthAfter[0] == 0.0F, "no blocked hit yet: blocking " + echo.isBlocking() + " using " + echo.isUsingItem()
                    + " offhand " + echo.getOffhandItem() + " delta " + healthAfter[0] + " " + echo.job().describe());
            helper.assertTrue(echo.job().guardBlocked() >= 1, "the block was not counted");
            helper.assertTrue(echo.getOffhandItem().is(Items.SHIELD) && echo.getOffhandItem().getDamageValue() > 0, "the shield did not wear: " + echo.getOffhandItem());
            zombie.discard();
            echo.discardSilently();
        });
    }

    private static BlockPos site(GameTestHelper helper, int chunkOffsetX) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, (origin.getX() >> 4) + chunkOffsetX, (origin.getZ() >> 4) + 14);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LivePlayers.force(level, site.offset(dx * 16, 0, dz * 16));
            }
        }
        LoadedChunkMemory.clear(level.getChunkAt(site));
        return site;
    }

    private static EchoEntity guard(GameTestHelper helper, ServerLevel level, ServerPlayer owner, BlockPos site) {
        Vec3 at = Vec3.atBottomCenterOf(site);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, at, at.x, at.y, at.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        EchoRecording recording = new EchoRecording(owner.getUUID(), owner.getGameProfile().name(), level.dimension(), at, buffer.array(), List.of());
        EchoEntity echo = EchoLife.spawn(level, owner, recording, EchoLesson.NONE, FarmLesson.NONE, LumberLesson.NONE, CareLesson.NONE,
                new GuardLesson(List.of(EntityTypes.ZOMBIE), 3, 0));
        helper.assertTrue(echo != null, "no echo");
        echo.stopReplay();
        echo.snapTo(at, 0.0F, 0.0F);
        return echo;
    }

    private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, BlockPos at) {
        Mob mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException("could not create " + type);
        }
        mob.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }
}
