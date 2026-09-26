package com.mnemolith.gametest;

import java.util.List;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Residual echoes against real server players ({@link LivePlayers}). Setup writes chunk memory and places residues
 * directly (that is scenery), but everything under test runs on its own: the player tick's pulse condenses the
 * residue, the residue's own tick lashes and is read through the lens the player raised with a real item use, the
 * needle goes through {@link ServerPlayer#interactOn}, and festers come from the residue's own timer.
 */
final class ResidueLiveTests {
    private ResidueLiveTests() {}

    /** Every live test gets its own chunk, 6 chunks apart, south of the grid and away from the suites' chunks. */
    private static int nextSite;

    private static BlockPos site(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int index = nextSite++;
        BlockPos pos = LivePlayers.surface(level, (origin.getX() >> 4) + 6 * index, (origin.getZ() >> 4) - 14);
        LivePlayers.force(level, pos);
        LoadedChunkMemory.clear(level.getChunkAt(pos));
        return pos;
    }

    // ---------- formation ----------

    /**
     * A player stands in a fractured chunk holding a death and a fire memory. Within a few pulses (every 10 s, 1 in 2
     * in a fracture) the player tick condenses the loudest graftable one, death, into a residue, and the imprint
     * leaves the chunk. The chunk is kept fractured while waiting (instability cools with a player in it).
     */
    static void formsWherePlayerStands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper);
        ImprintWriter.write(level, site.offset(-4, 0, -4), List.of(ImprintTag.DEATH), null, false);
        ImprintWriter.write(level, site.offset(4, 0, 4), List.of(ImprintTag.FIRE), null, false);
        keepLoud(level, site, PressureBand.FRACTURE);
        ServerPlayer player = LivePlayers.join(helper, "LiveFormer", Vec3.atBottomCenterOf(site.offset(0, 0, 6)));
        // The fracture raises a replicant; its hits must not end the wait early.
        player.getAbilities().invulnerable = true;
        helper.onEachTick(() -> {
            if (residues(level, site).isEmpty()) {
                keepLoud(level, site, PressureBand.FRACTURE);
            }
        });
        helper.succeedWhen(() -> {
            List<ResidueEntity> formed = residues(level, site);
            helper.assertTrue(!formed.isEmpty(), "no residue condensed yet (band " + band(level, site) + ")");
            ResidueEntity residue = formed.getFirst();
            helper.assertTrue(formed.size() == 1, "more than one residue in one chunk: " + formed.size());
            helper.assertTrue(residue.tag() == ImprintTag.DEATH, "condensed " + residue.tag() + ", not the loudest memory (death)");
            helper.assertTrue(!hasTag(level, site, ImprintTag.DEATH), "the death imprint is still in the chunk");
            helper.assertTrue(hasTag(level, site, ImprintTag.FIRE), "the quieter fire imprint left the chunk too");
        });
    }

    // ---------- lash ----------

    /** A survival player walking beside an unread death residue (2 blocks) is lashed: hurt and withered. */
    static void lashesPlayerBeside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper);
        ResidueEntity residue = spawned(helper, level, site, ImprintTag.DEATH, 3);
        ServerPlayer player = LivePlayers.join(helper, "LiveLashed", beside(residue, 2.0D));
        float health = player.getHealth();
        helper.onEachTick(() -> walkBeside(player, residue, 2.0D));
        helper.succeedWhen(() -> {
            helper.assertTrue(player.hasEffect(MobEffects.WITHER), "not lashed yet (no wither)");
            helper.assertTrue(player.getHealth() < health, "lashed without damage: health " + player.getHealth());
        });
    }

    /**
     * Sneaking halves the lash reach (3 to 1.5). A sneaking player 2.2 blocks away is spared for 3 seconds; the moment
     * they stand up, the residue lashes.
     */
    static void sneakingHalvesReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper);
        ResidueEntity residue = spawned(helper, level, site, ImprintTag.DEATH, 3);
        ServerPlayer player = LivePlayers.join(helper, "LiveSneaker", beside(residue, 2.2D));
        player.setShiftKeyDown(true);
        float health = player.getHealth();
        helper.onEachTick(() -> walkBeside(player, residue, 2.2D));
        helper.startSequence()
                .thenExecuteFor(60, () -> helper.assertTrue(!player.hasEffect(MobEffects.WITHER) && player.getHealth() >= health,
                        "a sneaking player 2.2 blocks away was lashed"))
                .thenExecute(() -> player.setShiftKeyDown(false))
                .thenWaitUntil(() -> helper.assertTrue(player.hasEffect(MobEffects.WITHER), "standing up at 2.2 blocks was not lashed"))
                .thenSucceed();
    }

    // ---------- observe and capture ----------

    /**
     * The needle on an unread residue slips: no shard, the residue stays, and it lashes the player (fire).
     */
    static void needleSlipsWhenUnread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper);
        ResidueEntity residue = spawned(helper, level, site, ImprintTag.FIRE, 3);
        ServerPlayer player = LivePlayers.join(helper, "LiveSlipper", beside(residue, 2.0D));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EXTRACTION_NEEDLE.get()));
        player.interactOn(residue, InteractionHand.MAIN_HAND, residue.position());
        helper.assertTrue(shards(player) == 0, "an unread residue gave a shard");
        helper.assertTrue(residue.isAlive(), "an unread residue was taken");
        helper.assertTrue(player.isOnFire(), "the slip did not lash (fire residue, player not burning)");
        helper.succeed();
    }

    /**
     * A player 8 blocks away raises the chronicle lens (a real item use) and keeps it on the residue. After 3 seconds
     * it is read and pinned, its tag is noted, and it never lashed. Then the needle takes it as a shard that holds
     * its tag and strength.
     */
    static void lensReadsThenNeedleCaptures(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper);
        ResidueEntity residue = spawned(helper, level, site, ImprintTag.FIRE, 4);
        ServerPlayer player = LivePlayers.join(helper, "LiveReader", beside(residue, 8.0D));
        ItemStack lens = new ItemStack(ModItems.CHRONICLE_LENS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, lens);
        player.gameMode.useItem(player, level, lens, InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            if (residue.isAlive() && player.getMainHandItem().is(ModItems.CHRONICLE_LENS.get())) {
                walkBeside(player, residue, 8.0D);
                player.lookAt(EntityAnchorArgument.Anchor.EYES, residue.getBoundingBox().getCenter());
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(residue.isPinned(),
                        "not read yet: progress " + residue.readProgress() + ", focusing " + player.isUsingItem()))
                .thenExecute(() -> {
                    helper.assertTrue(player.getData(ModAttachments.DISCOVERY.get()).hasTag(ImprintTag.FIRE), "reading did not note the fire tag");
                    helper.assertTrue(!player.isOnFire(), "the residue lashed a reader 8 blocks away");
                    player.stopUsingItem();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EXTRACTION_NEEDLE.get()));
                    player.snapTo(beside(residue, 2.0D));
                    player.interactOn(residue, InteractionHand.MAIN_HAND, residue.position());
                    helper.assertTrue(shards(player) == 1, "the needle on a read residue gave " + shards(player) + " shards");
                    ImprintCast cast = shard(player).get(ModDataComponents.IMPRINT_CAST.get());
                    helper.assertTrue(cast != null && cast.tag() == ImprintTag.FIRE && cast.intensity() == 4,
                            "the shard holds " + (cast == null ? "nothing" : cast.tag() + "/" + cast.intensity()) + ", not fire/4");
                    helper.assertTrue(residue.isRemoved(), "the captured residue is still there");
                })
                .thenSucceed();
    }

    // ---------- festers on their own timer ----------

    /** Left alone in a loud (overloaded) chunk, a residue festers after 60 s and writes its memory back. */
    static void festerWritesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper);
        keepLoud(level, site, PressureBand.OVERLOADED);
        ResidueEntity residue = spawned(helper, level, site, ImprintTag.FALL, 3);
        helper.onEachTick(() -> keepLoud(level, site, PressureBand.OVERLOADED));
        helper.succeedWhen(() -> {
            helper.assertTrue(hasTag(level, site, ImprintTag.FALL), "no fall memory written back yet (fester in " + residue.festerTicks() + ")");
            helper.assertTrue(residue.isAlive() && residue.strength() == 3, "a writing fester changed the residue: strength " + residue.strength());
        });
    }

    /** A mute stone in its chunk starves it: after one fester it is one weaker and wrote nothing. */
    static void muteStoneStarves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper);
        keepLoud(level, site, PressureBand.OVERLOADED);
        level.setBlock(site.offset(-5, 0, -5), ModBlocks.MUTE_STONE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(LoadedChunkMemory.isMuted(level, site), "placing a mute stone did not mute the chunk");
        ResidueEntity residue = spawned(helper, level, site, ImprintTag.FALL, 3);
        helper.onEachTick(() -> keepLoud(level, site, PressureBand.OVERLOADED));
        helper.succeedWhen(() -> {
            helper.assertTrue(residue.strength() == 2, "not starved yet: strength " + residue.strength() + " (fester in " + residue.festerTicks() + ")");
            helper.assertTrue(!hasTag(level, site, ImprintTag.FALL), "a muted residue wrote its memory");
        });
    }

    // ---------- exploring: the observatory's old residue ----------

    /**
     * The observatory template is placed with its worldgen processor list, which marks the reel's chunk. A player
     * walking into that chunk makes the next pulse seed one old residue (strength 5) by the reel; staying there for
     * two more pulses seeds nothing more.
     */
    static void observatorySeedsOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper);
        BlockPos reel = placeObservatory(helper, level, site);
        LivePlayers.force(level, reel);
        ServerPlayer player = LivePlayers.join(helper, "LiveExplorer", Vec3.atBottomCenterOf(reel.offset(1, 1, 1)));
        // An unread old residue lashes whoever stands by it; the wait is about seeding, not surviving.
        player.getAbilities().invulnerable = true;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<ResidueEntity> seeded = residues(level, reel);
                    helper.assertTrue(seeded.size() == 1, "no old residue seeded yet (" + seeded.size() + ")");
                    ResidueEntity old = seeded.getFirst();
                    helper.assertTrue(old.isOld() && old.strength() == Residues.OLD_STRENGTH, "seeded " + old.strength() + (old.isOld() ? " old" : " young"));
                    helper.assertTrue(old.distanceToSqr(Vec3.atCenterOf(reel)) < 16.0D * 16.0D, "seeded far from the reel");
                })
                .thenIdle(2 * Residues.PULSE_TICKS + 20)
                .thenExecute(() -> {
                    helper.assertTrue(residues(level, reel).size() == 1, "the observatory seeded again: " + residues(level, reel).size());
                    ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(reel));
                    helper.assertTrue(memory != null && memory.observatory() && memory.residueSeeded(), "the chunk is not flagged as seeded");
                })
                .thenSucceed();
    }

    /** Places the observatory like worldgen does (with its processor list) and returns the reel's position. */
    private static BlockPos placeObservatory(GameTestHelper helper, ServerLevel level, BlockPos site) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(com.mnemolith.Mnemolith.MOD_ID, "chronicle_observatory");
        var template = level.getStructureManager().get(id).orElseThrow(() -> helper.assertionException(
                net.minecraft.network.chat.Component.literal("no observatory template")));
        var processors = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PROCESSOR_LIST)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PROCESSOR_LIST,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(com.mnemolith.Mnemolith.MOD_ID, "observatory")));
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        processors.value().list().forEach(settings::addProcessor);
        BlockPos corner = site.offset(-8, 0, -8);
        var size = template.getSize();
        for (int dx = 0; dx < size.getX(); dx += 16) {
            for (int dz = 0; dz < size.getZ(); dz += 16) {
                LivePlayers.force(level, corner.offset(dx, 0, dz));
            }
        }
        template.placeInWorld(level, corner, corner, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            if (level.getBlockState(pos).is(ModBlocks.COMPOSITION_REEL.get())) {
                return pos.immutable();
            }
        }
        throw helper.assertionException(net.minecraft.network.chat.Component.literal("the observatory placed no reel"));
    }

    // ---------- helpers ----------

    private static ResidueEntity spawned(GameTestHelper helper, ServerLevel level, BlockPos site, ImprintTag tag, int strength) {
        ResidueEntity residue = Residues.spawn(level, site.above(), tag, strength, false);
        if (residue == null) {
            throw helper.assertionException(net.minecraft.network.chat.Component.literal("could not place a " + tag + " residue"));
        }
        return residue;
    }

    /** Raises instability a few points at a time until the chunk reaches {@code target}, without overshooting a band. */
    private static void keepLoud(ServerLevel level, BlockPos pos, PressureBand target) {
        for (int i = 0; i < 60 && band(level, pos).ordinal() < target.ordinal(); i++) {
            ImprintWriter.spike(level, pos, 3);
        }
    }

    private static PressureBand band(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? PressureBand.CALM : MemoryPressure.band(memory.cachedPressure());
    }

    private static boolean hasTag(ServerLevel level, BlockPos pos, ImprintTag tag) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory != null && memory.tags().contains(tag);
    }

    private static List<ResidueEntity> residues(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = ChunkPos.containing(pos);
        AABB column = new AABB(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX() + 1.0D, level.getMaxY(), chunk.getMaxBlockZ() + 1.0D);
        return level.getEntitiesOfClass(ResidueEntity.class, column, ResidueEntity::isAlive);
    }

    /** A point {@code distance} blocks east of the residue, level with it. */
    private static Vec3 beside(ResidueEntity residue, double distance) {
        return residue.position().add(distance, 0.0D, 0.0D);
    }

    /** Keeps the player {@code distance} blocks from the drifting residue, as a player walking alongside it would. */
    private static void walkBeside(ServerPlayer player, ResidueEntity residue, double distance) {
        if (!residue.isAlive()) {
            return;
        }
        player.snapTo(beside(residue, distance));
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static int shards(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory()) {
            if (Residues.isShard(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ItemStack shard(ServerPlayer player) {
        for (ItemStack stack : player.getInventory()) {
            if (Residues.isShard(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
