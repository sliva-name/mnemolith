package com.mnemolith.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.EchoHomeBlockEntity;
import com.mnemolith.echo.CareLesson;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoRole;
import com.mnemolith.echo.FarmLesson;
import com.mnemolith.echo.LumberLesson;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.SlotStack;
import com.mnemolith.echo.StoredEcho;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.recall.LifeMoment;
import com.mnemolith.recall.LifeMomentKind;
import com.mnemolith.recall.LifeMoments;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Damaged and old saves: unknown enum names (a renamed job mode, a removed imprint tag or moment kind), items from a
 * removed mod, slots that do not exist, numbers out of range, missing optional tags. Each must load what can be read
 * and keep every item that can be read; one bad field must never throw away the rest (the player's real inventory,
 * a housed echo, a chunk's mute stones).
 */
final class SaveNegativeTests {
    private SaveNegativeTests() {}

    private static final int LANE = 64;
    private static final String GHOST_ITEM = "removedmod:ghost_item";
    private static final String GHOST_BLOCK = "removedmod:ghost_ore";

    /** The player's real inventory, held while possessing, survives any one broken part of the borrowed body. */
    static void possessionState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        EchoJob job = new EchoJob();
        PossessionState.Real real = new PossessionState.Real(List.of(new SlotStack(0, new ItemStack(Items.DIAMOND, 5)), new SlotStack(9, new ItemStack(Items.TORCH, 32))),
                0, 18.0F, 17, 2.0F, List.of(), 3, 0.5F, 40);
        PossessionState.Anchor anchor = new PossessionState.Anchor(level.dimension(), new Vec3(1.5D, 70.0D, 1.5D), 0.0F, 0.0F, UUID.randomUUID());
        PossessionState.Body body = new PossessionState.Body(UUID.randomUUID(), 20.0F, Optional.empty(), Optional.of(job.save()), Optional.empty(), false, Optional.empty());
        CompoundTag good = (CompoundTag) PossessionState.MAP_CODEC.codec().encodeStart(ops, new PossessionState(new PossessionState.Data(real, anchor, body))).getOrThrow();
        helper.assertTrue(readPossession(level, good).map(s -> realCount(s) == 37).orElse(false), "a sound possession state did not load (the test set-up is wrong)");

        String[] cases = {"unknown job mode", "unknown job status", "garbage recording", "broken graft", "bad relay id", "item from a removed mod", "unknown dimension"};
        for (String what : cases) {
            CompoundTag tag = good.copy();
            CompoundTag data = tag.getCompoundOrEmpty("possession");
            CompoundTag bodyTag = data.getCompoundOrEmpty("body");
            switch (what) {
                case "unknown job mode" -> bodyTag.getCompoundOrEmpty("job").putString("mode", "teleport");
                case "unknown job status" -> bodyTag.getCompoundOrEmpty("job").put("status", StringTag.valueOf("nonsense"));
                case "garbage recording" -> bodyTag.putString("recording", "garbage");
                case "broken graft" -> {
                    CompoundTag graft = new CompoundTag();
                    graft.putString("bogus", "x");
                    bodyTag.put("graft", graft);
                }
                case "bad relay id" -> bodyTag.putString("relay", "not-a-uuid");
                case "item from a removed mod" -> {
                    ListTag items = data.getCompoundOrEmpty("real").getListOrEmpty("items");
                    items.add(slot(3, GHOST_ITEM, 1));
                }
                case "unknown dimension" -> data.getCompoundOrEmpty("anchor").putString("dimension", "removedmod:dream");
                default -> throw new IllegalStateException(what);
            }
            Optional<PossessionState> loaded = readPossession(level, tag);
            helper.assertTrue(loaded.isPresent() && loaded.get().isActive(), "a possession state with " + what + " did not load: the player's real items would be lost");
            helper.assertTrue(realCount(loaded.get()) == 37, "a possession state with " + what + " kept " + realCount(loaded.get()) + " of 37 real items");
        }
        helper.succeed();
    }

    /** A housed echo with a lesson naming a removed block, or with an item from a removed mod, keeps its body and its other items. */
    static void housedEchoes(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(0);
            level.setBlock(at, ModBlocks.ECHO_HOME.get().defaultBlockState(), 3);
            EchoHomeBlockEntity home = (EchoHomeBlockEntity) level.getBlockEntity(at);
            try {
                UUID owner = UUID.randomUUID();
                EchoLesson mining = new EchoLesson(20, 3, 0, 0, List.of(new EchoLesson.MineTarget(Blocks.IRON_ORE, 3)), Optional.empty());
                StoredEcho miner = new StoredEcho(UUID.randomUUID(), owner, "Miner", Optional.empty(), EchoRole.HUNTER,
                        List.of(new SlotStack(0, new ItemStack(Items.IRON_PICKAXE)), new SlotStack(1, new ItemStack(Items.BREAD, 3))), Optional.empty(), mining,
                        FarmLesson.NONE, LumberLesson.NONE, CareLesson.NONE, 15.0F, 4.0D, Optional.empty(), false, 2L);
                StoredEcho carrier = EchoHomeNegativeTests.stored(owner, List.of(new SlotStack(2, new ItemStack(Items.GOLD_INGOT, 7))));
                home.offer(miner);
                home.offer(carrier);
                TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                home.saveWithFullMetadata(output);
                CompoundTag saved = output.buildResult();

                // The miner's lesson names a block from a removed mod; the carrier also holds an item from one; a third entry is garbage.
                CompoundTag broken = saved.copy();
                ListTag housed = broken.getListOrEmpty("housed");
                replaceStrings(housed.getCompoundOrEmpty(0).get("lesson"), "minecraft:iron_ore", GHOST_BLOCK);
                housed.getCompoundOrEmpty(1).getListOrEmpty("inventory").add(slot(5, GHOST_ITEM, 1));
                housed.add(StringTag.valueOf("garbage"));
                home.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), broken));
                helper.assertTrue(home.size() == 2, "a damaged pedestal save loaded " + home.size() + " of 2 echoes");
                StoredEcho back = home.housed().stream().filter(e -> e.echo().equals(miner.echo())).findFirst().orElse(null);
                helper.assertTrue(back != null, "the echo whose lesson names a removed block was thrown away with its items");
                helper.assertTrue(SlotStack.count(back.inventory()) == 4 && back.role() == EchoRole.HUNTER && back.health() == 15.0F,
                        "the echo with a broken lesson lost items or state: items=" + SlotStack.count(back.inventory()) + " role=" + back.role());
                StoredEcho gold = home.housed().stream().filter(e -> e.echo().equals(carrier.echo())).findFirst().orElse(null);
                helper.assertTrue(gold != null && SlotStack.count(gold.inventory()) == 7, "the echo holding a removed mod's item lost its other items");

                // An old save with only the two required ids.
                CompoundTag old = new CompoundTag();
                ListTag minimal = new ListTag();
                CompoundTag entry = (CompoundTag) StoredEcho.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                        EchoHomeNegativeTests.stored(owner, List.of())).getOrThrow();
                CompoundTag bare = new CompoundTag();
                bare.put("echo", entry.get("echo"));
                bare.put("owner", entry.get("owner"));
                minimal.add(bare);
                old.put("housed", minimal);
                home.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), old));
                helper.assertTrue(home.size() == 1 && home.housed().get(0).role() == EchoRole.NONE && home.housed().get(0).inventory().isEmpty()
                        && home.housed().get(0).health() == 20.0F, "an old pedestal save with only the ids did not load with defaults");
            } finally {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            helper.succeed();
        }
    }

    /** A live echo saved with bad slots, an unknown item, an unknown role and job mode, and numbers out of range. */
    static void echoEntity(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(1);
            var owner = support.player("NegSaveEcho", at.offset(2, 0, 0));
            EchoEntity echo = support.echo(owner, at, EchoLesson.NONE);
            echo.inventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            echo.inventory().setItem(1, new ItemStack(Items.BREAD, 3));
            echo.job().setRadius(8);
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            echo.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            echo.discard();

            ListTag main = tag.getListOrEmpty("echo_main");
            main.add(slot(3, GHOST_ITEM, 1));
            main.add(slot(99, "minecraft:diamond", 2));
            main.add(slot(-4, "minecraft:emerald", 5));
            main.add(slot(1, "minecraft:apple", 4));
            tag.putString("echo_role", "astronaut");
            tag.putInt("echo_selected", 999);
            tag.putDouble("echo_bonus_health", -50.0D);
            CompoundTag job = tag.getCompoundOrEmpty("echo_job");
            job.putString("mode", "teleport");
            tag.put("echo_job", job);

            EchoEntity loaded = ModEntities.ECHO.get().create(level, EntitySpawnReason.LOAD);
            helper.assertTrue(loaded != null, "no echo");
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            helper.assertTrue(loaded.inventory().getItem(0).is(Items.IRON_PICKAXE) && loaded.inventory().getItem(1).is(Items.BREAD)
                    && loaded.inventory().getItem(1).getCount() == 3, "a damaged echo save lost the items in its good slots");
            helper.assertTrue(count(loaded, Items.DIAMOND) == 2 && count(loaded, Items.EMERALD) == 5 && count(loaded, Items.APPLE) == 4,
                    "items saved under a slot the echo does not have (or twice under one slot) were lost: diamonds=" + count(loaded, Items.DIAMOND)
                            + " emeralds=" + count(loaded, Items.EMERALD) + " apples=" + count(loaded, Items.APPLE));
            helper.assertTrue(loaded.role() == EchoRole.NONE, "an unknown role loaded as " + loaded.role());
            helper.assertTrue(loaded.selectedSlot() >= 0 && loaded.selectedSlot() <= 8, "selected slot 999 loaded as " + loaded.selectedSlot());
            helper.assertTrue(loaded.getMaxHealth() == CommonConfig.ECHO_MAX_HEALTH.get().floatValue(), "negative bonus health changed max health to " + loaded.getMaxHealth());
            helper.assertTrue(loaded.job().mode() == EchoJob.Mode.IDLE, "an unknown job mode loaded as " + loaded.job().mode());
            loaded.discard();

            // A saved radius past the server limit is clamped on load, like the screen's.
            EchoJob fresh = new EchoJob();
            RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            CompoundTag saved = (CompoundTag) EchoJob.Saved.CODEC.encodeStart(ops, fresh.save()).getOrThrow();
            for (int[] r : new int[][] {{9999, CommonConfig.ECHO_MINE_MAX_RADIUS.get()}, {1, 2}}) {
                saved.put("radius", IntTag.valueOf(r[0]));
                fresh.load(EchoJob.Saved.CODEC.parse(ops, saved).getOrThrow());
                helper.assertTrue(fresh.radius() == r[1], "a saved radius of " + r[0] + " loaded as " + fresh.radius());
            }
            helper.succeed();
        }
    }

    /** A chunk memory with a removed imprint tag keeps its other imprints and its mute stones; a world's moments keep the readable ones. */
    static void memories(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        ChunkMemory memory = new ChunkMemory();
        memory.addImprint(new Imprint(ImprintTag.FIRE, 5, pos, Optional.empty(), 1, 10L), 8);
        memory.addImprint(new Imprint(ImprintTag.DEATH, 7, pos.east(), Optional.empty(), 2, 20L), 8);
        memory.addMuteStone(pos.north());
        memory.addInstability(12, 100);
        CompoundTag good = (CompoundTag) ChunkMemory.CODEC.codec().encodeStart(ops, memory).getOrThrow();

        CompoundTag renamed = good.copy();
        replaceStrings(renamed.get("imprints"), ImprintTag.FIRE.getSerializedName(), "forgotten_tag");
        Optional<ChunkMemory> loaded = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), renamed).read(ChunkMemory.CODEC);
        helper.assertTrue(loaded.isPresent(), "a chunk memory with a removed imprint tag did not load at all (its mute stones would be lost)");
        helper.assertTrue(loaded.get().muteStoneCount() == 1 && loaded.get().imprintCount() == 1 && loaded.get().imprintAt(0).tag() == ImprintTag.DEATH,
                "a chunk memory with a removed imprint tag kept " + loaded.get().muteStoneCount() + " mute stones and " + loaded.get().imprintCount() + " imprints");

        // The cached pressure, instability and flags are derived numbers: a save missing them keeps the mute stones.
        for (String key : new String[] {"pressure", "instability", "last_write", "fractured", "archival"}) {
            CompoundTag missing = good.copy();
            missing.remove(key);
            Optional<ChunkMemory> back = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), missing).read(ChunkMemory.CODEC);
            helper.assertTrue(back.isPresent() && back.get().muteStoneCount() == 1 && back.get().imprintCount() == 2,
                    "a chunk memory missing '" + key + "' lost its mute stones and imprints");
        }

        // Life moments: one of an unknown kind, one sound; the sound one and the scene time are kept.
        UUID player = UUID.randomUUID();
        LifeMoments moments = new LifeMoments();
        String dimension = level.dimension().identifier().toString();
        moments.add(player, new LifeMoment(LifeMomentKind.DEATH, dimension, pos, 0.0F, "", "", 100L, 100L, 1, 0, true), 24);
        moments.add(player, new LifeMoment(LifeMomentKind.HOME, dimension, pos.offset(40, 0, 0), 0.0F, "", "", 200L, 200L, 1, 0, true), 24);
        moments.markScene(player, 555L);
        Tag saved = LifeMoments.CODEC.encodeStart(ops, moments).getOrThrow();
        replaceStrings(saved, LifeMomentKind.HOME.getSerializedName(), "picnic");
        Optional<LifeMoments> back = LifeMoments.CODEC.parse(ops, saved).resultOrPartial();
        helper.assertTrue(back.isPresent() && back.get().of(player).size() == 1 && back.get().of(player).get(0).kind() == LifeMomentKind.DEATH
                && back.get().lastScene(player) == 555L, "moments with an unknown kind lost the readable ones");
        helper.succeed();
    }

    private static Optional<PossessionState> readPossession(ServerLevel level, CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag).read(PossessionState.MAP_CODEC);
    }

    private static int realCount(PossessionState state) {
        return state.data() == null ? 0 : SlotStack.count(state.data().real().items());
    }

    private static CompoundTag slot(int slot, String item, int count) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", item);
        stack.putInt("count", count);
        CompoundTag entry = new CompoundTag();
        entry.putInt("slot", slot);
        entry.put("item", stack);
        return entry;
    }

    private static int count(EchoEntity echo, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < echo.inventory().getContainerSize(); i++) {
            if (echo.inventory().getItem(i).is(item)) {
                total += echo.inventory().getItem(i).getCount();
            }
        }
        return total;
    }

    /** Replaces every string value equal to {@code from} inside {@code tag} (recursively) with {@code to}. */
    private static void replaceStrings(Tag tag, String from, String to) {
        if (tag instanceof CompoundTag compound) {
            for (String key : List.copyOf(compound.keySet())) {
                Tag value = compound.get(key);
                if (value instanceof StringTag(String text) && text.equals(from)) {
                    compound.putString(key, to);
                } else {
                    replaceStrings(value, from, to);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                Tag value = list.get(i);
                if (value instanceof StringTag(String text) && text.equals(from)) {
                    list.set(i, StringTag.valueOf(to));
                } else {
                    replaceStrings(value, from, to);
                }
            }
        }
    }
}
