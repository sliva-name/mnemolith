package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.content.item.ExtractionNeedleItem;
import com.mnemolith.content.item.RecolliteNeedleItem;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.mob.Faded;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.Hollows;
import com.mnemolith.worldgen.hollows.LecternReplay;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith hollows3qa}. Memory Hollows, stage 3: the faded (registered monster, loot, hollows-only spawn spot
 * with a local cap, copying flickers, provoked by a catch, damage halved unseen and raised when revealed, seen through
 * a raised lens, slip drop of what it copied), lectern replay (plays, cools down, keeps the slip, cannot be caught),
 * the recollite needle (catches without a lens, its reach, recipe), sounds, advancements and the guide page. Works on
 * the flat game test world.
 */
public final class Hollows3Qa {
    private static final String[] NAMES = {"fadedType", "fadedLoot", "spawnHollowsOnly", "spawnCap", "mimic", "provoke", "damageScale",
            "lensReveal", "slipDrop", "lecternPlays", "lecternCooldown", "lecternHoldings", "needleUnaided", "needleReach", "needleRecipe",
            "needleItem", "audio", "advancements", "guidePage"};
    private static final UUID PLAYER = UUID.fromString("44444444-1111-2222-3333-777777777777");
    private static int salt;

    private Hollows3Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        // ---------- the faded: data ----------
        var type = ModEntities.FADED.get();
        ok[0] = BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(id("faded")) && type.getCategory() == MobCategory.MONSTER
                && !type.isAllowedInPeaceful();
        LootTable loot = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id("entities/faded")));
        ok[1] = loot != LootTable.EMPTY;

        BlockPos a = column(level, (spawn.getX() >> 4) + 70 + salt * 3, (spawn.getZ() >> 4) - 66);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Hollows3Qa"));
        List<Faded> spawned = new ArrayList<>();
        BlockPos lectern = a.offset(-4, 0, -4);
        try {
            discardFaded(level, a);
            HollowFlickers.clear();
            LecternReplay.clear();
            player.getInventory().clearContent();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

            // ---------- spawn spot ----------
            boolean wasHollows = Hollows.is(level.getBiome(a));
            boolean before = MobSpawns.fadedSpotOk(level, a);
            CommandSourceStack console = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
            try {
                server.getCommands().getDispatcher().execute(String.format("fillbiome %d %d %d %d %d %d mnemolith:memory_hollows",
                        a.getX() - 20, a.getY() - 2, a.getZ() - 20, a.getX() + 20, a.getY() + 6, a.getZ() + 20), console);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                notes.add("fillbiome failed: " + e.getMessage());
            }
            boolean after = MobSpawns.fadedSpotOk(level, a);
            ok[2] = (wasHollows || !before) && after;
            notes.add("spot plain=" + before + " hollows=" + after);
            for (int i = 0; i < MobSpawns.FADED_LOCAL_CAP; i++) {
                spawned.add(faded(level, a.offset(6 + i, 0, 6)));
            }
            boolean capped = !MobSpawns.fadedSpotOk(level, a);
            int present = level.getEntitiesOfClass(Faded.class, new AABB(a).inflate(MobSpawns.FADED_CAP_RADIUS)).size();
            for (Faded f : spawned) {
                f.discard();
            }
            spawned.clear();
            boolean reopened = MobSpawns.fadedSpotOk(level, a);
            ok[3] = capped && reopened;
            if (!ok[3]) {
                notes.add(0, "cap capped=" + capped + " present=" + present + " reopened=" + reopened);
            }

            // ---------- mimic and provoke ----------
            Faded mimic = faded(level, a.offset(5, 0, 0));
            spawned.add(mimic);
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WORK, ImprintTag.TRADE.ordinal()));
            ok[4] = mimic.copied() == ImprintTag.TRADE && a.equals(mimic.mimicAt());
            notes.add("mimic copied=" + mimic.copied() + " at=" + mimic.mimicAt());
            HollowFlickers.clear();
            player.snapTo(a.getX() + 2.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            Faded.onCatch(level, a, player);
            // a peaceful world (the game test server) refuses player targets in Mob.setTarget; the copy is still dropped
            boolean canTarget = mimic.canAttack(player);
            ok[5] = mimic.getTarget() == (canTarget ? player : null) && mimic.mimicAt() == null;
            notes.add("provoke canTarget=" + canTarget + " difficulty=" + level.getDifficulty());
            if (!ok[5]) {
                notes.add(0, "provoke target=" + mimic.getTarget() + " mimicAt=" + mimic.mimicAt() + " creative=" + player.isCreative());
            }

            // ---------- damage, lens ----------
            Faded target = faded(level, a.offset(0, 0, 3));
            spawned.add(target);
            float h0 = target.getHealth();
            target.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
            float unseen = h0 - target.getHealth();
            target.invulnerableTime = 0;
            target.reveal(null);
            float h1 = target.getHealth();
            target.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
            float seen = h1 - target.getHealth();
            ok[6] = Math.abs(unseen - 2.0F) < 0.01F && Math.abs(seen - 6.0F) < 0.01F && target.revealed();
            notes.add("damage unseen=" + unseen + " revealed=" + seen);

            Faded watched = faded(level, a.offset(0, 0, 6));
            spawned.add(watched);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F); // yaw 0 looks south (+z)
            player.setYHeadRot(0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CHRONICLE_LENS.get()));
            boolean idle = watched.seenThroughLens(player);
            player.startUsingItem(InteractionHand.MAIN_HAND);
            boolean raised = watched.seenThroughLens(player);
            player.setYRot(180.0F);
            player.setYHeadRot(180.0F);
            boolean away = watched.seenThroughLens(player);
            player.stopUsingItem();
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            ok[7] = !idle && raised && !away;
            notes.add("lens idle=" + idle + " raised=" + raised + " away=" + away);

            // ---------- slip drop: 12 kills, P(no slip) = 1/4096 ----------
            int slipDrops = 0;
            int wrongSlips = 0;
            for (int i = 0; i < 12; i++) {
                Faded victim = faded(level, a.offset(-6, 0, 2));
                spawned.add(victim);
                victim.copy(a, ImprintTag.FIRE);
                victim.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
                for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(a.offset(-6, 0, 2)).inflate(3.0D))) {
                    ImprintCast cast = drop.getItem().get(ModDataComponents.IMPRINT_CAST.get());
                    if (ImprintSlips.isSlip(drop.getItem())) {
                        if (cast != null && cast.tag() == ImprintTag.FIRE) {
                            slipDrops++;
                        } else {
                            wrongSlips++;
                        }
                    }
                    drop.discard();
                }
            }
            ok[8] = slipDrops >= 1 && slipDrops < 12 && wrongSlips == 0;
            notes.add("slipDrops=" + slipDrops + "/12");

            // ---------- lectern ----------
            LevelChunk chunk = level.getChunkAt(lectern);
            LoadedChunkMemory.clear(chunk); // the kills above left death imprints here
            ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
            memory.addImprint(new Imprint(ImprintTag.PATH, 3, lectern, Optional.empty(), 31, level.getGameTime()), 8);
            memory.addImprint(new Imprint(ImprintTag.PATH, 3, lectern, Optional.empty(), 32, level.getGameTime()), 8);
            memory.addImprint(new Imprint(ImprintTag.TRADE, 2, lectern, Optional.empty(), 33, level.getGameTime()), 8);
            level.setBlock(lectern, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, ImprintSlips.of(ImprintTag.DEATH, lectern));
            ItemStack slip = player.getMainHandItem();
            long replays = HollowFlickers.replayCount();
            long caught = HollowFlickers.caughtCount();
            LecternReplay.Result first = LecternReplay.use(level, lectern, player, slip);
            ok[9] = first == LecternReplay.Result.PLAYED && HollowFlickers.replayCount() == replays + 1 && HollowFlickers.active().isEmpty()
                    && slip.getCount() == 1 && player.getMainHandItem() == slip;
            // nothing to catch: a replay is a reading, not a memory
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.RECOLLITE_LENS.get()));
            ok[9] &= HollowFlickers.tryCatch(level, player).kind() == HollowFlickers.Catch.NONE && HollowFlickers.caughtCount() == caught;
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            LecternReplay.Result second = LecternReplay.use(level, lectern, player, slip);
            ok[10] = second == LecternReplay.Result.COOLDOWN && HollowFlickers.replayCount() == replays + 1
                    && LecternReplay.use(level, lectern.above(), player, slip) == LecternReplay.Result.NONE
                    && LecternReplay.use(level, lectern, player, new ItemStack(ModItems.CHRONICLE_LENS.get())) == LecternReplay.Result.NONE;
            notes.add("lectern first=" + first + " second=" + second);
            String holdings = LecternReplay.holdings(memory).getString();
            ok[11] = holdings.contains("×2") && holdings.contains(", ") && !LecternReplay.holdings(null).getString().isEmpty();
            notes.add(0, "holdings=" + holdings + " nothing=" + LecternReplay.holdings(null).getString());
            LoadedChunkMemory.clear(chunk);
            level.setBlock(lectern, Blocks.AIR.defaultBlockState(), 3);

            // ---------- recollite needle ----------
            LevelChunk home = level.getChunkAt(a);
            ChunkMemory here = LoadedChunkMemory.getOrCreate(home);
            here.addImprint(new Imprint(ImprintTag.PATH, 3, a, Optional.empty(), 41, level.getGameTime()), 8);
            here.addImprint(new Imprint(ImprintTag.TRADE, 2, a, Optional.empty(), 42, level.getGameTime()), 8);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RECOLLITE_NEEDLE.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.snapTo(a.getX() + 6.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WALK, ImprintTag.PATH.ordinal()));
            InteractionResult used = player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
            ok[12] = used.consumesAction() && slips(player, ImprintTag.PATH) == 1 && HollowFlickers.reach(player) == HollowFlickers.NEEDLE_REACH;
            notes.add("needle unaided=" + used.consumesAction() + " reach=" + HollowFlickers.reach(player));
            HollowFlickers.clear();
            // 8 blocks: past the needle's own reach (7), inside a recollite lens's (9)
            player.snapTo(a.getX() + 8.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WORK, ImprintTag.TRADE.ordinal()));
            boolean farMiss = HollowFlickers.tryCatch(level, player).kind() == HollowFlickers.Catch.NONE;
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.RECOLLITE_LENS.get()));
            HollowFlickers.CatchResult far = HollowFlickers.tryCatch(level, player);
            ok[13] = farMiss && far.kind() == HollowFlickers.Catch.CAUGHT && far.tag() == ImprintTag.TRADE;
            // the plain needle still needs a lens
            HollowFlickers.clear();
            here.addImprint(new Imprint(ImprintTag.PATH, 3, a, Optional.empty(), 43, level.getGameTime()), 8);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EXTRACTION_NEEDLE.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.snapTo(a.getX() + 2.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WALK, ImprintTag.PATH.ordinal()));
            ok[13] &= HollowFlickers.reach(player) == 0 && HollowFlickers.tryCatch(level, player).kind() == HollowFlickers.Catch.NONE;
            notes.add("needle far=" + farMiss + "/" + far.kind());
            LoadedChunkMemory.clear(home);
        } finally {
            HollowFlickers.clear();
            LecternReplay.clear();
            for (Faded f : spawned) {
                f.discard();
            }
            discardFaded(level, a);
            if (level.getBlockState(lectern).is(Blocks.LECTERN)) {
                level.setBlock(lectern, Blocks.AIR.defaultBlockState(), 3);
            }
            player.stopUsingItem();
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            releaseColumn(level, ChunkPos.containing(a));
        }

        // ---------- recipe and item ----------
        Optional<RecipeHolder<?>> needle = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("recollite_needle")));
        if (needle.isPresent() && needle.get().value() instanceof CraftingRecipe crafting) {
            ItemStack r = new ItemStack(ModItems.RECOLLITE_SHARD.get());
            CraftingInput input = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, r.copy(), ItemStack.EMPTY,
                    r.copy(), new ItemStack(ModItems.EXTRACTION_NEEDLE.get()), r.copy(), ItemStack.EMPTY, r.copy(), ItemStack.EMPTY));
            ok[14] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.RECOLLITE_NEEDLE.get());
        }
        ItemStack recollite = new ItemStack(ModItems.RECOLLITE_NEEDLE.get());
        ItemStack plain = new ItemStack(ModItems.EXTRACTION_NEEDLE.get());
        ok[15] = ModItems.RECOLLITE_NEEDLE.get() instanceof RecolliteNeedleItem rn && rn.catchesUnaided()
                && plain.getItem() instanceof ExtractionNeedleItem en && !en.catchesUnaided()
                && recollite.getMaxDamage() == plain.getMaxDamage() * 2;

        // ---------- audio, advancements, guide ----------
        int sounds = 0;
        for (var holder : List.of(ModSounds.FADED_AMBIENT, ModSounds.FADED_HURT, ModSounds.FADED_DEATH, ModSounds.LECTERN_REPLAY)) {
            if (BuiltInRegistries.SOUND_EVENT.containsKey(holder.getId())) {
                sounds++;
            }
        }
        ok[16] = sounds == 4;
        ok[17] = server.getAdvancements().get(Faded.REVEALED_ADVANCEMENT) != null && server.getAdvancements().get(LecternReplay.ADVANCEMENT) != null
                && server.getAdvancements().get(id("recollite_needle")) != null && server.getAdvancements().get(id("recipes/recollite_needle")) != null;
        int sunkenPage = -1;
        int fadedPage = -1;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (GuideBook.pageId(i).equals("sunken")) {
                sunkenPage = i;
            } else if (GuideBook.pageId(i).equals("faded")) {
                fadedPage = i;
            }
        }
        ok[18] = sunkenPage >= 0 && fadedPage == sunkenPage + 1 && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("hollows3qa", NAMES, ok, notes);
    }

    private static Faded faded(ServerLevel level, BlockPos pos) {
        Faded faded = ModEntities.FADED.get().create(level, EntitySpawnReason.COMMAND);
        if (faded == null) {
            throw new IllegalStateException("faded did not create");
        }
        faded.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        faded.setNoAi(true);
        level.addFreshEntity(faded);
        return faded;
    }

    private static void discardFaded(ServerLevel level, BlockPos pos) {
        for (Faded faded : level.getEntitiesOfClass(Faded.class, new AABB(pos).inflate(40.0D))) {
            faded.discard();
        }
    }

    private static int slips(FakePlayer player, ImprintTag tag) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
            if (ImprintSlips.isSlip(stack) && cast != null && cast.tag() == tag) {
                n += stack.getCount();
            }
        }
        return n;
    }

    @SuppressWarnings("unused")
    private static ResourceKey<Recipe<?>> recipe(String path) {
        return ResourceKey.create(Registries.RECIPE, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
