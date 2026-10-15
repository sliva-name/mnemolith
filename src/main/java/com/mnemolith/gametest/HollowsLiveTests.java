package com.mnemolith.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.Hollows;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Memory flickers against real server players ({@link LivePlayers}), read off their in-memory connections: a player
 * standing in Memory Hollows is sent a flicker of the chunk's imprint, a friend nearby gets the same payload and a
 * player far off gets nothing; then the first player catches it with needle and lens through the real item use, gets
 * the slip, and both nearby players are told the flicker was caught.
 */
final class HollowsLiveTests {
    private HollowsLiveTests() {}

    static void flickerDeliveredAndCaught(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, (origin.getX() >> 4) - 40, (origin.getZ() >> 4) + 120);
        // Keep every chunk a flicker spot can land in (up to MAX_DISTANCE = 20 blocks, so two chunks out) loaded. Only
        // the site chunk was held before; the others could unload and come back from worldgen without the hollow
        // biome or the path imprints, and then no spot qualified.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                LivePlayers.force(level, site.offset(dx * 16, 0, dz * 16));
            }
        }
        BlockPos far = LivePlayers.surface(level, (site.getX() >> 4) + 9, site.getZ() >> 4);
        LivePlayers.force(level, far);
        CommandSourceStack console = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
        // Biome reads are fuzzed: level.getBiome blends the 4x4x4 cells around a position, so a player standing a block
        // lower than the site can read the cell under the filled range. Fill well above and below the surface (CI
        // failed with the seer one block under the site reading hollow=false).
        // fillbiome takes at most 32768 blocks per call, so fill in slabs of 8.
        for (int y0 = site.getY() - 12; y0 <= site.getY() + 12; y0 += 8) {
            String fill = String.format("fillbiome %d %d %d %d %d %d mnemolith:memory_hollows", site.getX() - 24, y0, site.getZ() - 24,
                    site.getX() + 24, Math.min(y0 + 7, site.getY() + 12), site.getZ() + 24);
            try {
                level.getServer().getCommands().getDispatcher().execute(fill, console);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                helper.fail("fillbiome failed: " + e.getMessage());
                return;
            }
        }
        helper.assertTrue(Hollows.is(level.getBiome(site)), "fillbiome did not make the site Memory Hollows");
        // Every chunk a flicker spot can fall in remembers a path.
        ChunkPos center = ChunkPos.containing(site);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                var chunk = level.getChunk(center.x() + dx, center.z() + dz);
                LoadedChunkMemory.clear(chunk);
                ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
                memory.addImprint(new Imprint(ImprintTag.PATH, 3, site, Optional.empty(), 900 + dx * 7 + dz, level.getGameTime()), 8);
            }
        }
        ServerPlayer seer = LivePlayers.join(helper, "LiveFlickerSeer", Vec3.atBottomCenterOf(site));
        ServerPlayer friend = LivePlayers.join(helper, "LiveFlickerFriend", Vec3.atBottomCenterOf(site.offset(4, 0, 4)));
        ServerPlayer stranger = LivePlayers.join(helper, "LiveFlickerStranger", Vec3.atBottomCenterOf(far));
        HollowFlickers.clear();
        HollowFlickerPayload[] shown = new HollowFlickerPayload[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    drain(seer);
                    drain(friend);
                    drain(stranger);
                    boolean sent = false;
                    long now = level.getGameTime();
                    // Density 4 makes the roll certain; a spot can still miss (a pond, the biome edge), so try a few times.
                    for (int i = 0; i < 40 && !sent; i++) {
                        sent = HollowFlickers.offer(level, seer, 4.0D, now + i * 100L);
                    }
                    helper.assertTrue(sent, "no flicker was offered to a player standing in Memory Hollows at " + seer.blockPosition().toShortString()
                            + " hollow=" + Hollows.is(level.getBiome(seer.blockPosition())));
                    List<HollowFlickerPayload> seen = flickers(seer);
                    helper.assertTrue(seen.size() == 1, "the player in the hollow got " + seen.size() + " flicker payloads, not 1; read " + LAST
                            + " open=" + channel(seer).isOpen() + " connected=" + seer.connection.getConnection().isConnected());
                    HollowFlickerPayload payload = seen.get(0);
                    double distance = Math.sqrt(payload.pos().distSqr(seer.blockPosition().atY(payload.pos().getY())));
                    helper.assertTrue(payload.tag() == ImprintTag.PATH.ordinal() && payload.scene() == HollowFlickers.WALK,
                            "the flicker is not the chunk's path imprint: tag=" + payload.tag() + " scene=" + payload.scene());
                    helper.assertTrue(distance >= HollowFlickers.MIN_DISTANCE - 1 && distance <= HollowFlickers.MAX_DISTANCE + 1,
                            "the flicker is " + distance + " blocks from the player");
                    List<HollowFlickerPayload> friendSaw = flickers(friend);
                    helper.assertTrue(friendSaw.size() == 1 && friendSaw.get(0).equals(payload), "the friend nearby did not get the same flicker: " + friendSaw);
                    helper.assertTrue(flickers(stranger).isEmpty(), "a player " + (int) Math.sqrt(far.distSqr(site)) + " blocks away was sent the flicker");
                    shown[0] = payload;
                    // Needle in the main hand, lens in the off hand, two blocks from where it stands.
                    BlockPos spot = payload.pos();
                    seer.teleportTo(level, spot.getX() + 2.5D, spot.getY(), spot.getZ() + 0.5D, java.util.Set.of(), 0.0F, 0.0F, true);
                    seer.getInventory().clearContent();
                    seer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EXTRACTION_NEEDLE.get()));
                    seer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.CHRONICLE_LENS.get()));
                    // Catch in the same tick: QA suites that run beside this test clear the shared flicker list.
                    drain(seer);
                    drain(friend);
                    ItemStack needle = seer.getMainHandItem();
                    seer.gameMode.useItem(seer, level, needle, InteractionHand.MAIN_HAND);
                    int slips = 0;
                    for (int i = 0; i < seer.getInventory().getContainerSize(); i++) {
                        ImprintCast cast = seer.getInventory().getItem(i).get(ModDataComponents.IMPRINT_CAST.get());
                        if (cast != null && cast.tag() == ImprintTag.PATH) {
                            slips += seer.getInventory().getItem(i).getCount();
                        }
                    }
                    helper.assertTrue(slips == 1, "catching the flicker gave " + slips + " path slips");
                    for (ServerPlayer watcher : List.of(seer, friend)) {
                        List<HollowFlickerPayload> told = flickers(watcher);
                        helper.assertTrue(told.size() == 1 && told.get(0).scene() == HollowFlickers.CAUGHT && told.get(0).pos().equals(shown[0].pos()),
                                watcher.getName().getString() + " was not told the flicker was caught: " + told);
                    }
                    // The server's own once-a-second roll can show these players another flicker meanwhile, so only
                    // the caught one has to be gone.
                    helper.assertTrue(HollowFlickers.active().stream().noneMatch(a -> a.pos().equals(shown[0].pos())),
                            "the caught flicker is still catchable: " + HollowFlickers.active());
                    var advancement = level.getServer().getAdvancements().get(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.mnemolith.Mnemolith.MOD_ID, "flicker_caught"));
                    helper.assertTrue(advancement != null && seer.getAdvancements().getOrStartProgress(advancement).isDone(),
                            "catching a flicker did not award flicker_caught");
                    HollowFlickers.clear();
                })
                .thenSucceed();
    }

    /** A patch of Memory Hollows around {@code site}: chunks held, biome filled in slabs (fillbiome caps volume). */
    private static boolean hollowSite(GameTestHelper helper, BlockPos site) {
        ServerLevel level = helper.getLevel();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LivePlayers.force(level, site.offset(dx * 16, 0, dz * 16));
            }
        }
        CommandSourceStack console = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
        for (int y0 = site.getY() - 12; y0 <= site.getY() + 12; y0 += 8) {
            String fill = String.format("fillbiome %d %d %d %d %d %d mnemolith:memory_hollows", site.getX() - 16, y0, site.getZ() - 16,
                    site.getX() + 16, Math.min(y0 + 7, site.getY() + 12), site.getZ() + 16);
            try {
                level.getServer().getCommands().getDispatcher().execute(fill, console);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                helper.fail("fillbiome failed: " + e.getMessage());
                return false;
            }
        }
        return Hollows.is(level.getBiome(site));
    }

    /**
     * Memory Hollows stage 4: a real player opens a sunken archive chest (the real block use, through the interaction
     * event); its keeper rises beside it, bound to that chest; the opened chest wakes nothing more; the player kills the
     * keeper and it drops a forgotten slip, and its death leaves a forgotten imprint in the hollow.
     */
    static void keeperRisesAtArchiveChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, (origin.getX() >> 4) - 60, (origin.getZ() >> 4) + 120);
        helper.assertTrue(hollowSite(helper, site), "the site is not Memory Hollows");
        BlockPos chest = site.offset(0, 0, 3);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -1; dz <= 7; dz++) {
                level.setBlockAndUpdate(site.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlockAndUpdate(site.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity box) {
            box.setLootTable(com.mnemolith.worldgen.hollows.ArchiveKeepers.ARCHIVE_LOOT, 7L);
        }
        LoadedChunkMemory.clear(level.getChunkAt(chest));
        ServerPlayer opener = LivePlayers.join(helper, "LiveKeeperOpener", Vec3.atBottomCenterOf(site));
        net.minecraft.world.phys.BlockHitResult face = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(chest).add(0.0D, 0.0D, -0.5D),
                net.minecraft.core.Direction.NORTH, chest, false);
        com.mnemolith.entity.mob.Faded[] keeper = new com.mnemolith.entity.mob.Faded[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    opener.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    opener.gameMode.useItemOn(opener, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, face);
                    List<com.mnemolith.entity.mob.Faded> keepers = level.getEntitiesOfClass(com.mnemolith.entity.mob.Faded.class,
                            new net.minecraft.world.phys.AABB(chest).inflate(8.0D), f -> f.isAlive() && f.keeper());
                    helper.assertTrue(keepers.size() == 1, "opening the archive chest woke " + keepers.size() + " keepers");
                    keeper[0] = keepers.get(0);
                    helper.assertTrue(keeper[0].hasHome() && keeper[0].getHomePosition().equals(chest), "the keeper is not bound to its chest");
                    helper.assertTrue(level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity box
                            && box.getLootTable() == null, "the chest's loot was not rolled on opening");
                    opener.closeContainer();
                    opener.gameMode.useItemOn(opener, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, face);
                    opener.closeContainer();
                    int after = level.getEntitiesOfClass(com.mnemolith.entity.mob.Faded.class, new net.minecraft.world.phys.AABB(chest).inflate(16.0D),
                            f -> f.isAlive() && f.keeper()).size();
                    helper.assertTrue(after == 1, "opening the chest again woke another keeper: " + after);
                    keeper[0].hurtServer(level, level.damageSources().playerAttack(opener), 1000.0F);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(!keeper[0].isAlive(), "the keeper survived");
                    int slips = 0;
                    for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(chest).inflate(8.0D))) {
                        ImprintCast cast = item.getItem().get(ModDataComponents.IMPRINT_CAST.get());
                        if (cast != null && cast.tag() == ImprintTag.FORGOTTEN) {
                            slips += item.getItem().getCount();
                        }
                        item.discard();
                    }
                    helper.assertTrue(slips >= 1, "the keeper dropped no forgotten slip");
                    ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(keeper[0].blockPosition()));
                    boolean forgotten = false;
                    for (int i = 0; memory != null && i < memory.imprintCount(); i++) {
                        forgotten |= memory.imprintAt(i).tag() == ImprintTag.FORGOTTEN;
                    }
                    helper.assertTrue(forgotten, "the keeper's death left no forgotten imprint in the hollow");
                })
                .thenSucceed();
    }

    /**
     * Memory Hollows stage 4: a forgotten flicker reaches a real player in the hollow as the fade scene, and needle and
     * lens catch it into a forgotten slip through the real item use.
     */
    static void forgottenFlickerCaught(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, (origin.getX() >> 4) - 80, (origin.getZ() >> 4) + 120);
        helper.assertTrue(hollowSite(helper, site), "the site is not Memory Hollows");
        BlockPos spot = site.offset(3, 0, 0);
        var chunk = level.getChunkAt(spot);
        LoadedChunkMemory.clear(chunk);
        LoadedChunkMemory.getOrCreate(chunk).addImprint(new Imprint(ImprintTag.FORGOTTEN, 3, spot, Optional.empty(), 4242, level.getGameTime()), 8);
        ServerPlayer seer = LivePlayers.join(helper, "LiveForgottenSeer", Vec3.atBottomCenterOf(site));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    drain(seer);
                    HollowFlickers.send(level, new HollowFlickerPayload(spot, 0.0F, HollowFlickers.scene(ImprintTag.FORGOTTEN), ImprintTag.FORGOTTEN.ordinal()));
                    List<HollowFlickerPayload> seen = flickers(seer);
                    helper.assertTrue(seen.size() == 1 && seen.get(0).scene() == HollowFlickers.FADE && seen.get(0).tag() == ImprintTag.FORGOTTEN.ordinal(),
                            "the player did not see a forgotten flicker fade: " + seen);
                    seer.teleportTo(level, spot.getX() + 2.5D, spot.getY(), spot.getZ() + 0.5D, java.util.Set.of(), 0.0F, 0.0F, true);
                    seer.getInventory().clearContent();
                    seer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EXTRACTION_NEEDLE.get()));
                    seer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.CHRONICLE_LENS.get()));
                    // catch in the same tick: QA suites running beside this test clear the shared flicker list
                    seer.gameMode.useItem(seer, level, seer.getMainHandItem(), InteractionHand.MAIN_HAND);
                    int slips = 0;
                    for (int i = 0; i < seer.getInventory().getContainerSize(); i++) {
                        ImprintCast cast = seer.getInventory().getItem(i).get(ModDataComponents.IMPRINT_CAST.get());
                        if (cast != null && cast.tag() == ImprintTag.FORGOTTEN) {
                            slips += seer.getInventory().getItem(i).getCount();
                        }
                    }
                    helper.assertTrue(slips == 1, "catching the forgotten flicker gave " + slips + " forgotten slips");
                    HollowFlickers.clear();
                })
                .thenSucceed();
    }

    /**
     * The recollite pickaxe: a real player in Memory Hollows mines iron ore in a chunk whose loudest memory is fire,
     * through the real block break. The raw iron comes out smelted, the pickaxe wears, and the fire memory surfaces as a
     * flicker sent to the player.
     */
    static void pickaxeReadsFireInHollow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, (origin.getX() >> 4) - 100, (origin.getZ() >> 4) + 120);
        helper.assertTrue(hollowSite(helper, site), "the site is not Memory Hollows");
        BlockPos ore = site.offset(2, 0, 0);
        level.setBlockAndUpdate(ore.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(ore, net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState());
        var chunk = level.getChunkAt(ore);
        LoadedChunkMemory.clear(chunk);
        LoadedChunkMemory.getOrCreate(chunk).addImprint(new Imprint(ImprintTag.FIRE, 3, ore, Optional.empty(), 5151, level.getGameTime()), 8);
        ServerPlayer miner = LivePlayers.join(helper, "LivePickaxeMiner", Vec3.atBottomCenterOf(site));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    com.mnemolith.content.item.PickaxeReading.resetCooldown(miner.getUUID());
                    miner.getInventory().clearContent();
                    miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RECOLLITE_PICKAXE.get()));
                    drain(miner);
                    // break and read in the same tick: QA suites running beside this test clear the shared flicker list
                    boolean broke = miner.gameMode.destroyBlock(ore);
                    helper.assertTrue(broke && level.getBlockState(ore).isAir(), "the pickaxe did not break the ore");
                    int ingots = 0;
                    int raw = 0;
                    for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(ore).inflate(3.0D))) {
                        ingots += item.getItem().is(net.minecraft.world.item.Items.IRON_INGOT) ? item.getItem().getCount() : 0;
                        raw += item.getItem().is(net.minecraft.world.item.Items.RAW_IRON) ? item.getItem().getCount() : 0;
                        item.discard();
                    }
                    helper.assertTrue(ingots >= 1 && raw == 0, "fire did not smelt the drop: ingots=" + ingots + " raw=" + raw);
                    helper.assertTrue(miner.getMainHandItem().getDamageValue() == 1, "the pickaxe did not wear: " + miner.getMainHandItem().getDamageValue());
                    List<HollowFlickerPayload> seen = flickers(miner);
                    helper.assertTrue(seen.stream().anyMatch(f -> f.pos().equals(ore) && f.tag() == ImprintTag.FIRE.ordinal()
                            && f.scene() == HollowFlickers.scene(ImprintTag.FIRE)), "the fire memory did not surface as a flicker: " + seen);
                    HollowFlickers.clear();
                })
                .thenSucceed();
    }

    /**
     * The recollite pickaxe in a chunk whose loudest memory is silence: breaking stone there (the real block break)
     * writes no build imprint, while the same pickaxe in a chunk that remembers a path writes one as usual.
     */
    static void pickaxeQuietInSilentChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos quietSite = LivePlayers.surface(level, (origin.getX() >> 4) - 120, (origin.getZ() >> 4) + 120);
        BlockPos loudSite = LivePlayers.surface(level, (origin.getX() >> 4) - 118, (origin.getZ() >> 4) + 120);
        LivePlayers.force(level, quietSite);
        LivePlayers.force(level, loudSite);
        BlockPos quietStone = quietSite.offset(1, 0, 0);
        BlockPos loudStone = loudSite.offset(1, 0, 0);
        for (BlockPos stone : List.of(quietStone, loudStone)) {
            level.setBlockAndUpdate(stone.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        var quietChunk = level.getChunkAt(quietStone);
        var loudChunk = level.getChunkAt(loudStone);
        LoadedChunkMemory.clear(quietChunk);
        LoadedChunkMemory.clear(loudChunk);
        LoadedChunkMemory.getOrCreate(quietChunk).addImprint(new Imprint(ImprintTag.SILENCE, 4, quietStone, Optional.empty(), 6161, level.getGameTime()), 8);
        LoadedChunkMemory.getOrCreate(loudChunk).addImprint(new Imprint(ImprintTag.PATH, 3, loudStone, Optional.empty(), 6162, level.getGameTime()), 8);
        ServerPlayer miner = LivePlayers.join(helper, "LivePickaxeQuiet", Vec3.atBottomCenterOf(quietSite));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    miner.getInventory().clearContent();
                    miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RECOLLITE_PICKAXE.get()));
                    helper.assertTrue(miner.gameMode.destroyBlock(quietStone), "the pickaxe did not break the stone in the silent chunk");
                    helper.assertTrue(count(quietChunk, ImprintTag.BUILD) == 0, "mining in the silent chunk wrote a build imprint");
                    miner.teleportTo(level, loudSite.getX() + 0.5D, loudSite.getY(), loudSite.getZ() + 0.5D, java.util.Set.of(), 0.0F, 0.0F, true);
                    helper.assertTrue(miner.gameMode.destroyBlock(loudStone), "the pickaxe did not break the stone in the path chunk");
                    helper.assertTrue(count(loudChunk, ImprintTag.BUILD) == 1, "mining in the path chunk wrote " + count(loudChunk, ImprintTag.BUILD)
                            + " build imprints, not 1");
                    for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(quietStone).inflate(40.0D))) {
                        item.discard();
                    }
                })
                .thenSucceed();
    }

    /**
     * The recollite lantern: a real player places one in a hollow (the real block use); the chunk beside it does not
     * forget its path memory; the player mines the lantern with a pickaxe, gets it back, and the hollow forgets again.
     */
    static void lanternKeepsHollowMemory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, (origin.getX() >> 4) - 140, (origin.getZ() >> 4) + 120);
        helper.assertTrue(hollowSite(helper, site), "the site is not Memory Hollows");
        BlockPos floor = site.offset(2, -1, 0);
        level.setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(floor.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(floor.above(2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        BlockPos lamp = floor.above();
        BlockPos memoryAt = site.offset(-3, 0, 2);
        var chunk = level.getChunkAt(memoryAt);
        ServerPlayer keeper = LivePlayers.join(helper, "LiveLanternKeeper", Vec3.atBottomCenterOf(site));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    keeper.getInventory().clearContent();
                    ItemStack lantern = new ItemStack(ModItems.RECOLLITE_LANTERN.get());
                    keeper.setItemInHand(InteractionHand.MAIN_HAND, lantern);
                    net.minecraft.world.phys.BlockHitResult top = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor).add(0.0D, 0.5D, 0.0D),
                            net.minecraft.core.Direction.UP, floor, false);
                    keeper.gameMode.useItemOn(keeper, level, keeper.getMainHandItem(), InteractionHand.MAIN_HAND, top);
                    helper.assertTrue(level.getBlockState(lamp).is(com.mnemolith.content.ModBlocks.RECOLLITE_LANTERN.get()),
                            "the lantern was not placed: " + level.getBlockState(lamp));
                    LoadedChunkMemory.clear(chunk);
                    LoadedChunkMemory.getOrCreate(chunk).addImprint(new Imprint(ImprintTag.PATH, 3, memoryAt, Optional.empty(), 7171, level.getGameTime()), 8);
                    boolean forgot = com.mnemolith.worldgen.hollows.HollowForgetting.forget(level, memoryAt, ImprintTag.PATH);
                    helper.assertTrue(!forgot && count(chunk, ImprintTag.PATH) == 1 && count(chunk, ImprintTag.FORGOTTEN) == 0,
                            "the hollow forgot beside a lantern: forgot=" + forgot + " path=" + count(chunk, ImprintTag.PATH));
                    keeper.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
                    helper.assertTrue(keeper.gameMode.destroyBlock(lamp), "the lantern could not be mined");
                    int dropped = 0;
                    for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(lamp).inflate(3.0D))) {
                        dropped += item.getItem().is(ModItems.RECOLLITE_LANTERN.get()) ? item.getItem().getCount() : 0;
                        item.discard();
                    }
                    helper.assertTrue(dropped == 1, "mining the lantern dropped " + dropped + " lanterns");
                    boolean forgotAfter = com.mnemolith.worldgen.hollows.HollowForgetting.forget(level, memoryAt, ImprintTag.PATH);
                    helper.assertTrue(forgotAfter && count(chunk, ImprintTag.PATH) == 0 && count(chunk, ImprintTag.FORGOTTEN) == 1,
                            "with the lantern gone the hollow did not forget: forgot=" + forgotAfter);
                })
                .thenSucceed();
    }

    /**
     * The remembrance posy: a real player in a hollow right-clicks a faded that copied a fall with a posy (the real
     * entity interaction). The faded is gone without dying, the hollow keeps the fall (nothing forgotten), a recollite
     * shard lies where it stood, and one posy is spent.
     */
    static void posyLaysFadedToRest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, (origin.getX() >> 4) - 160, (origin.getZ() >> 4) + 120);
        helper.assertTrue(hollowSite(helper, site), "the site is not Memory Hollows");
        BlockPos stand = site.offset(2, 0, 0);
        var chunk = level.getChunkAt(stand);
        LoadedChunkMemory.clear(chunk);
        com.mnemolith.entity.mob.Faded faded = com.mnemolith.entity.ModEntities.FADED.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        helper.assertTrue(faded != null, "no faded");
        faded.snapTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 0.0F, 0.0F);
        faded.setNoAi(true);
        level.addFreshEntity(faded);
        faded.copy(stand, ImprintTag.FALL);
        ServerPlayer giver = LivePlayers.join(helper, "LivePosyGiver", Vec3.atBottomCenterOf(site));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    giver.getInventory().clearContent();
                    giver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEMBRANCE_POSY.get(), 2));
                    net.minecraft.world.InteractionResult result = giver.interactOn(faded, InteractionHand.MAIN_HAND, faded.position().add(0.0D, 1.0D, 0.0D));
                    helper.assertTrue(faded.isRemoved() && !faded.isDeadOrDying(), "the faded was not laid to rest: result=" + result
                            + " removed=" + faded.isRemoved());
                    helper.assertTrue(giver.getMainHandItem().getCount() == 1, "the posy was not spent: " + giver.getMainHandItem().getCount());
                    helper.assertTrue(count(chunk, ImprintTag.FALL) == 1 && count(chunk, ImprintTag.FORGOTTEN) == 0,
                            "the memory did not go back: fall=" + count(chunk, ImprintTag.FALL) + " forgotten=" + count(chunk, ImprintTag.FORGOTTEN));
                    int shards = 0;
                    for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(stand).inflate(3.0D))) {
                        shards += item.getItem().is(ModItems.RECOLLITE_SHARD.get()) ? item.getItem().getCount() : 0;
                        item.discard();
                    }
                    helper.assertTrue(shards == 1, "laying it to rest left " + shards + " shards");
                })
                .thenSucceed();
    }

    private static int count(net.minecraft.world.level.chunk.LevelChunk chunk, ImprintTag tag) {
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        int n = 0;
        for (int i = 0; memory != null && i < memory.imprintCount(); i++) {
            n += memory.imprintAt(i).tag() == tag ? 1 : 0;
        }
        return n;
    }

    private static EmbeddedChannel channel(ServerPlayer player) {
        return (EmbeddedChannel) player.connection.getConnection().channel();
    }

    private static void drain(ServerPlayer player) {
        EmbeddedChannel channel = channel(player);
        channel.runPendingTasks();
        // The game writes packets during the tick and flushes at its end: flush to see this tick's.
        channel.flushOutbound();
        while (channel.readOutbound() != null) {
            // discard
        }
    }

    /** Flicker payloads sent to {@code player} since the last read, in order. */
    private static List<HollowFlickerPayload> flickers(ServerPlayer player) {
        EmbeddedChannel channel = channel(player);
        channel.runPendingTasks();
        // The game writes packets during the tick and flushes at its end: flush to see this tick's.
        channel.flushOutbound();
        List<HollowFlickerPayload> out = new ArrayList<>();
        LAST.clear();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            collect(message, out);
        }
        return out;
    }

    /** Classes of the last messages read, for a failure message. */
    private static final List<String> LAST = new ArrayList<>();

    private static void collect(Object message, List<HollowFlickerPayload> out) {
        if (LAST.size() < 12) {
            LAST.add(message.getClass().getSimpleName() + (message instanceof ClientboundCustomPayloadPacket c ? "(" + c.payload().type().id() + ")" : ""));
        }
        if (message instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof HollowFlickerPayload flicker) {
            out.add(flicker);
        } else if (message instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> inner : bundle.subPackets()) {
                collect(inner, out);
            }
        }
    }
}
