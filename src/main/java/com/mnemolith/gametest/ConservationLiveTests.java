package com.mnemolith.gametest;

import java.util.List;
import java.util.Random;

import com.mnemolith.command.qa.QaSupport;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.EchoHomeBlockEntity;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.SlotStack;
import com.mnemolith.echo.StoredEcho;
import com.mnemolith.echo.relay.EchoRelays;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Item conservation under random play. A survival player owns three echoes (two linked by a relay thread) and an echo
 * home, and carries emeralds in the player, in every echo and in the world. Each tick one random action runs
 * (possess, return, hop, house, wake, kill an echo, drop) and the emeralds counted in every place they can be (the
 * player's inventory and cursor, the stored real inventory of a possession, every echo, the housed echoes, dropped
 * items) must still add up to what the test started with: no swap, drop or death may copy or lose an item.
 */
final class ConservationLiveTests {
    private ConservationLiveTests() {}

    private static final int SEED = envInt("MNEMOLITH_CHAOS_SEED", 0x6d6e656d);
    private static final int STEPS = envInt("MNEMOLITH_CHAOS_STEPS", 400);

    /** A longer or differently seeded run for a hunt: {@code MNEMOLITH_CHAOS_STEPS}, {@code MNEMOLITH_CHAOS_SEED}. */
    static int envInt(String name, int fallback) {
        String value = System.getenv(name);
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean traced;

    /** Hunt aid: log who removes an item entity that still holds emeralds. */
    private static void trace() {
        if (traced || System.getenv("MNEMOLITH_CHAOS_TRACE") == null) {
            return;
        }
        traced = true;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) -> {
            if (event.getEntity() instanceof ItemEntity item && emeralds(item.getItem()) > 0 && item.isRemoved()) {
                com.mnemolith.Mnemolith.LOGGER.warn("Mnemolith chaos item left {} reason={} at {}", item.getItem(), item.getRemovalReason(), item.blockPosition().toShortString(), new Throwable("who"));
            }
        });
    }

    static void chaos(GameTestHelper helper) {
        trace();
        ServerLevel level = helper.getLevel();
        BlockPos site = RelayLiveTests.site(helper, 3);
        ServerPlayer player = LivePlayers.join(helper, "LiveChaos", Vec3.atBottomCenterOf(site));
        player.getAbilities().invulnerable = true;
        BlockPos homePos = site.offset(0, 0, 6);
        level.setBlock(homePos, ModBlocks.ECHO_HOME.get().defaultBlockState(), 3);
        EchoHomeBlockEntity home = (EchoHomeBlockEntity) level.getBlockEntity(homePos);
        EchoEntity a = RelayLiveTests.echo(helper, player, site.offset(-4, 0, 0));
        EchoEntity b = RelayLiveTests.echo(helper, player, site.offset(5, 0, 0));
        EchoEntity c = RelayLiveTests.echo(helper, player, site.offset(0, 0, -5));
        EchoRelays.link(level, a, b);
        stock(a.inventory(), 10, 0, 36);
        stock(b.inventory(), 20, 8, 39);
        stock(c.inventory(), 30, 1, 40);
        player.getInventory().setItem(2, new ItemStack(Items.EMERALD, 7));
        player.getInventory().setItem(20, new ItemStack(Items.EMERALD, 3));
        int[] expected = {10 + 20 + 30 + 7 + 3};
        Random random = new Random(SEED);
        int[] step = {0};
        String[] before = {""};
        java.util.Map<java.util.UUID, ItemEntity> seenItems = new java.util.HashMap<>();
        java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
        StringBuilder trail = new StringBuilder();
        helper.onEachTick(() -> {
            if (step[0] >= STEPS) {
                return;
            }
            step[0]++;
            calm(level, site);
            StringBuilder between = new StringBuilder();
            int idle = describe(level, player, home, site, between);
            String goneBetween = scan(level, site, seenItems);
            helper.assertTrue(idle == expected[0], "between steps " + (step[0] - 1) + " and " + step[0] + ": " + idle + " emeralds, expected " + expected[0] + " | now: " + between + " | before: " + before[0]
                    + " | empties: " + empties(level, site) + " | gone: " + goneBetween + " | last: " + trail);
            String op = act(helper, level, player, home, homePos, site, random, expected);
            tally.merge(op, 1, Integer::sum);
            trail.append(op).append(' ');
            if (trail.length() > 400) {
                trail.delete(0, trail.length() - 400);
            }
            StringBuilder where = new StringBuilder();
            int now = describe(level, player, home, site, where);
            String gone = scan(level, site, seenItems);
            helper.assertTrue(now == expected[0], "step " + step[0] + " (" + op + "): " + now + " emeralds, expected " + expected[0] + " | now: " + where + " | before: " + before[0] + " | goneItems: " + gone + " | last: " + trail);
            before[0] = where.toString();
            if (step[0] == STEPS) {
                com.mnemolith.Mnemolith.LOGGER.info("Mnemolith gametest conservation actions {}", tally);
                helper.succeed();
            }
        });
    }

    private static void stock(EchoInventory inventory, int emeralds, int first, int second) {
        int half = emeralds / 2;
        inventory.setItem(first, new ItemStack(Items.EMERALD, half));
        inventory.setItem(second, new ItemStack(Items.EMERALD, emeralds - half));
    }

    /** One random action. Returns its name (with the outcome) for the failure message. */
    private static String act(GameTestHelper helper, ServerLevel level, ServerPlayer player, EchoHomeBlockEntity home, BlockPos homePos, BlockPos site, Random random, int[] expected) {
        boolean possessing = EchoPossession.isPossessing(player);
        List<EchoEntity> echoes = level.getEntitiesOfClass(EchoEntity.class, area(site), e -> e.isAlive() && e.isOwnedBy(player));
        int pick = random.nextInt(18);
        switch (pick) {
            case 0, 1, 2 -> {
                if (possessing || echoes.isEmpty()) {
                    return "possess-skip";
                }
                EchoEntity target = echoes.get(random.nextInt(echoes.size()));
                target.stopReplayIfRunning();
                return "possess:" + EchoPossession.possess(player, target);
            }
            case 3, 4, 5 -> {
                if (!possessing) {
                    return "return-skip";
                }
                player.setShiftKeyDown(false);
                EchoRelays.returnKey(player);
                return "return";
            }
            case 6, 7 -> {
                if (!possessing) {
                    return "hop-skip";
                }
                player.setShiftKeyDown(true);
                EchoRelays.returnKey(player);
                player.setShiftKeyDown(false);
                return "hop";
            }
            case 8, 9 -> {
                if (possessing || echoes.isEmpty()) {
                    return "house-skip";
                }
                EchoEntity target = echoes.get(random.nextInt(echoes.size()));
                target.stopReplayIfRunning();
                return "house:" + EchoLife.house(player, target, home);
            }
            case 10, 11 -> {
                if (possessing) {
                    return "wake-skip";
                }
                return "wake:" + EchoLife.wake(player, home, homePos.above());
            }
            case 12, 13 -> {
                if (possessing || echoes.size() >= 4) {
                    return "spawn-skip";
                }
                EchoEntity fresh = RelayLiveTests.echo(helper, player, site.offset(random.nextInt(11) - 5, 0, random.nextInt(9) - 8));
                int emeralds = 1 + random.nextInt(9);
                fresh.inventory().setItem(random.nextInt(EchoInventory.SIZE), new ItemStack(Items.EMERALD, emeralds));
                expected[0] += emeralds;
                for (EchoEntity other : echoes) {
                    if (other.relay() == null) {
                        EchoRelays.link(level, fresh, other);
                        break;
                    }
                }
                return "spawn";
            }
            case 14 -> {
                if (echoes.isEmpty() || random.nextInt(3) != 0) {
                    return "kill-skip";
                }
                EchoEntity victim = echoes.get(random.nextInt(echoes.size()));
                victim.kill(level);
                return "kill";
            }
            case 15 -> {
                if (!possessing) {
                    return "die-skip";
                }
                // The possessed body takes a lethal hit: the death is refused and the player is handed back to the shell.
                player.getAbilities().invulnerable = false;
                player.invulnerableTime = 0;
                player.hurtServer(level, level.damageSources().generic(), 1000.0F);
                player.getAbilities().invulnerable = true;
                player.setHealth(Math.max(1.0F, player.getHealth()));
                return "die:" + (player.isAlive() ? "alive" : "DEAD");
            }
            default -> {
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area(site))) {
                    if (item.getItem().is(Items.EMERALD)) {
                        item.setNoPickUpDelay();
                        player.getInventory().add(item.getItem().split(item.getItem().getCount()));
                        item.discard();
                        return "pickup";
                    }
                }
                int slot = random.nextInt(EchoInventory.MAIN);
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty()) {
                    return "drop-skip";
                }
                ItemStack out = player.getInventory().removeItem(slot, 1 + random.nextInt(stack.getCount()));
                ItemEntity drop = new ItemEntity(level, player.getX(), player.getY() + 0.5D, player.getZ(), out);
                drop.setNeverPickUp();
                level.addFreshEntity(drop);
                return "drop";
            }
        }
    }

    /**
     * Kills and possessions write death imprints and spike the chunk, and a fractured chunk condenses residues that
     * act out with fire, which burns the dropped emeralds (vanilla fire, not a leak). Every tick the pad's memory is
     * wiped and what it condensed is removed, so only the swaps under test move items.
     */
    private static void calm(ServerLevel level, BlockPos site) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LoadedChunkMemory.clear(level.getChunk((site.getX() >> 4) + dx, (site.getZ() >> 4) + dz));
            }
        }
        QaSupport.discardResidues(level, site);
        QaSupport.discardReplicants(level, site);
        for (net.minecraft.world.entity.Entity entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area(site),
                e -> e instanceof net.minecraft.world.entity.monster.Monster || e instanceof net.minecraft.world.entity.projectile.Projectile)) {
            entity.discard();
        }
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos at = site.offset(dx, dy, dz);
                    if (level.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
                        level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
    }

    /** Item entities that were here a tick ago and are not now, with what became of them. */
    private static String scan(ServerLevel level, BlockPos site, java.util.Map<java.util.UUID, ItemEntity> seenItems) {
        java.util.Set<java.util.UUID> present = new java.util.HashSet<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area(site))) {
            present.add(item.getUUID());
            seenItems.put(item.getUUID(), item);
        }
        StringBuilder gone = new StringBuilder();
        for (var entry : new java.util.ArrayList<>(seenItems.entrySet())) {
            if (!present.contains(entry.getKey())) {
                ItemEntity lost = entry.getValue();
                gone.append(entry.getKey().toString(), 0, 4).append(":").append(lost.getItem()).append(" removed=").append(lost.getRemovalReason()).append(" at ")
                        .append(lost.blockPosition().toShortString()).append(" age=").append(lost.getAge()).append("; ");
                seenItems.remove(entry.getKey());
            }
        }
        return gone.toString();
    }

    /** Item entities holding no emeralds now: the stack was emptied by something that did not remove the entity. */
    private static String empties(ServerLevel level, BlockPos site) {
        StringBuilder out = new StringBuilder();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area(site))) {
            out.append(item.getUUID().toString(), 0, 4).append(' ').append(item.getItem()).append(" @").append(item.blockPosition().toShortString()).append(" age=").append(item.getAge())
                    .append(" delay=").append(item.hasPickUpDelay()).append("; ");
        }
        return out.toString();
    }

    private static AABB area(BlockPos site) {
        return new AABB(site).inflate(24.0D, 8.0D, 24.0D);
    }

    private static int emeralds(ItemStack stack) {
        return stack.is(Items.EMERALD) ? stack.getCount() : 0;
    }

    private static int count(ServerLevel level, ServerPlayer player, EchoHomeBlockEntity home, BlockPos site) {
        return describe(level, player, home, site, null);
    }

    /** Total emeralds; when {@code into} is set it also receives where they are. */
    private static int describe(ServerLevel level, ServerPlayer player, EchoHomeBlockEntity home, BlockPos site, StringBuilder into) {
        int total = 0;
        int inventory = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            inventory += emeralds(player.getInventory().getItem(i));
        }
        int cursor = emeralds(player.containerMenu.getCarried());
        int real = 0;
        PossessionState.Data data = EchoPossession.state(player).data();
        if (data != null) {
            for (SlotStack stack : data.real().items()) {
                real += emeralds(stack.stack());
            }
        }
        StringBuilder echoes = new StringBuilder();
        int echoTotal = 0;
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, area(site))) {
            int held = 0;
            for (int i = 0; i < EchoInventory.SIZE; i++) {
                held += emeralds(echo.inventory().getItem(i));
            }
            echoTotal += held;
            echoes.append(echo.getUUID().toString(), 0, 4).append(echo.isAlive() ? "" : "(dead)").append('=').append(held).append(' ');
        }
        int housed = 0;
        for (StoredEcho stored : home.housed()) {
            for (SlotStack stack : stored.inventory()) {
                housed += emeralds(stack.stack());
            }
        }
        int dropped = 0;
        StringBuilder items = new StringBuilder();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area(site))) {
            dropped += emeralds(item.getItem());
            if (emeralds(item.getItem()) > 0) {
                items.append(item.getItem().getCount()).append('@').append(item.blockPosition().toShortString()).append('#').append(item.getAge()).append(item.isRemoved() ? "R" : "").append(' ');
            }
        }
        total = inventory + cursor + real + echoTotal + housed + dropped;
        if (into != null) {
            into.append("player=").append(inventory).append(" cursor=").append(cursor).append(" real=").append(real).append(" echoes[").append(echoes).append("] housed=").append(housed)
                    .append(" dropped=").append(dropped).append("{").append(items).append("} possessing=").append(data != null)
                    .append(" player@").append(player.blockPosition().toShortString()).append(" site=").append(site.toShortString());
        }
        return total;
    }
}
