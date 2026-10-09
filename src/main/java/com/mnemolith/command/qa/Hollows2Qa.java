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
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.Hollows;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith hollows2qa}. Memory Hollows, stage 2: the sunken archive (registered, hollows-only, spacing,
 * template chests, loot), catching flickers with lens and needle (the needle's own use, once per flicker, tagless,
 * gone, reach with each lens, no lens), the recollite lens recipe, the early amethyst path, the biome's music and
 * ambience, advancements and the guide page. Works on the flat game test world.
 */
public final class Hollows2Qa {
    private static final String[] NAMES = {"structure", "hollowsOnly", "spacing", "template", "loot", "needleCatch", "catchOnce", "faint", "gone",
            "reachLens", "reachRecollite", "noLens", "lensRecipe", "lensIsLens", "earlyAmethyst", "audio", "advancements", "guidePage"};
    private static final UUID PLAYER = UUID.fromString("44444444-1111-2222-3333-666666666666");
    private static int salt;

    private Hollows2Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        // ---------- sunken archive ----------
        Optional<Holder.Reference<Structure>> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(
                ResourceKey.create(Registries.STRUCTURE, id("sunken_archive")));
        ok[0] = structure.isPresent();
        if (structure.isPresent()) {
            HolderSet<Biome> biomes = structure.get().value().biomes();
            ok[1] = biomes.size() == 1 && Hollows.is(biomes.get(0));
            notes.add("archiveBiomes=" + biomes.size());
        }
        Optional<Holder.Reference<StructureSet>> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(
                ResourceKey.create(Registries.STRUCTURE_SET, id("sunken_archive")));
        if (set.isPresent() && set.get().value().placement() instanceof RandomSpreadStructurePlacement spread) {
            ok[2] = spread.spacing() == 8 && spread.separation() == 3 && set.get().value().structures().size() == 1;
            notes.add("spacing=" + spread.spacing() + " separation=" + spread.separation());
        }
        Optional<StructureTemplate> template = level.getStructureManager().get(id("sunken_archive"));
        if (template.isPresent()) {
            int chests = template.get().filterBlocks(BlockPos.ZERO, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                    Blocks.CHEST).size();
            // ore features run after surface_structures and replace anything in these tags
            int oreBait = 0;
            for (var tag : java.util.List.of(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD, net.minecraft.tags.BlockTags.STONE_ORE_REPLACEABLES)) {
                for (var holder : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                    oreBait += template.get().filterBlocks(BlockPos.ZERO,
                            new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), holder.value()).size();
                }
            }
            ok[3] = chests == 2 && template.get().getSize().getY() == 14 && oreBait == 0;
            notes.add("templateChests=" + chests + " size=" + template.get().getSize().toShortString() + " oreReplaceable=" + oreBait);
        }
        LootTable loot = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id("chests/sunken_archive")));
        int rolls = 0;
        int withShard = 0;
        int empty = 0;
        if (loot != LootTable.EMPTY) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(spawn)).create(LootContextParamSets.CHEST);
            for (long seed = 1; seed <= 30; seed++) {
                List<ItemStack> items = loot.getRandomItems(params, seed);
                rolls++;
                if (items.isEmpty()) {
                    empty++;
                }
                if (items.stream().anyMatch(stack -> stack.is(ModItems.RECOLLITE_SHARD.get()))) {
                    withShard++;
                }
            }
        }
        ok[4] = rolls == 30 && empty == 0 && withShard >= 10;
        notes.add("loot rolls=" + rolls + " withShard=" + withShard + " empty=" + empty);

        // ---------- catching flickers ----------
        BlockPos a = column(level, (spawn.getX() >> 4) - 64 - salt * 3, (spawn.getZ() >> 4) + 61);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Hollows2Qa"));
        try {
            HollowFlickers.clear();
            LevelChunk chunk = level.getChunkAt(a);
            ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
            memory.addImprint(new Imprint(ImprintTag.PATH, 3, a, Optional.empty(), 11, level.getGameTime()), 8);
            memory.addImprint(new Imprint(ImprintTag.TRADE, 2, a, Optional.empty(), 12, level.getGameTime()), 8);
            player.getInventory().clearContent();
            player.snapTo(a.getX() + 3.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EXTRACTION_NEEDLE.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.CHRONICLE_LENS.get()));
            ItemStack needle = player.getMainHandItem();
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WALK, ImprintTag.PATH.ordinal()));
            int before = count(memory, ImprintTag.PATH);
            InteractionResult used = needle.use(level, player, InteractionHand.MAIN_HAND);
            int slips = slips(player, ImprintTag.PATH);
            ok[5] = used.consumesAction() && slips == 1 && count(memory, ImprintTag.PATH) == before - 1 && count(memory, ImprintTag.TRADE) == 1
                    && HollowFlickers.caughtCount() == 1;
            // The advancement is checked on a real player in the live test: a fake player's advancements are not kept.
            notes.add("needleUse=" + used.consumesAction() + " pathSlips=" + slips + " pathLeft=" + count(memory, ImprintTag.PATH));
            ok[6] = HollowFlickers.tryCatch(level, player).kind() == HollowFlickers.Catch.NONE && HollowFlickers.active().isEmpty();

            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WALK, -1));
            ok[7] = HollowFlickers.tryCatch(level, player).kind() == HollowFlickers.Catch.FAINT;
            HollowFlickers.clear();

            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.KNEEL, ImprintTag.DEATH.ordinal()));
            ok[8] = HollowFlickers.tryCatch(level, player).kind() == HollowFlickers.Catch.GONE && slips(player, ImprintTag.DEATH) == 0;
            HollowFlickers.clear();

            // 7 blocks away: out of a chronicle lens's reach (5), inside a recollite lens's (9).
            player.snapTo(a.getX() + 7.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WORK, ImprintTag.TRADE.ordinal()));
            ok[9] = HollowFlickers.tryCatch(level, player).kind() == HollowFlickers.Catch.NONE;
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.RECOLLITE_LENS.get()));
            HollowFlickers.CatchResult far = HollowFlickers.tryCatch(level, player);
            ok[10] = far.kind() == HollowFlickers.Catch.CAUGHT && far.tag() == ImprintTag.TRADE && slips(player, ImprintTag.TRADE) == 1
                    && count(memory, ImprintTag.TRADE) == 0;
            notes.add("recollite far=" + far.kind());
            HollowFlickers.clear();

            memory.addImprint(new Imprint(ImprintTag.PATH, 3, a, Optional.empty(), 13, level.getGameTime()), 8);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.snapTo(a.getX() + 2.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WALK, ImprintTag.PATH.ordinal()));
            ok[11] = HollowFlickers.tryCatch(level, player).kind() == HollowFlickers.Catch.NONE && count(memory, ImprintTag.PATH) == 1;
            LoadedChunkMemory.clear(chunk);
        } finally {
            HollowFlickers.clear();
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            releaseColumn(level, ChunkPos.containing(a));
        }

        // ---------- recipes and progression ----------
        Optional<RecipeHolder<?>> lens = server.getRecipeManager().byKey(recipe("recollite_lens"));
        if (lens.isPresent() && lens.get().value() instanceof CraftingRecipe crafting) {
            ItemStack r = new ItemStack(ModItems.RECOLLITE_SHARD.get());
            CraftingInput input = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, r.copy(), ItemStack.EMPTY,
                    r.copy(), new ItemStack(ModItems.CHRONICLE_LENS.get()), r.copy(),
                    ItemStack.EMPTY, new ItemStack(ModItems.RECOLLITE_BLOCK.get()), ItemStack.EMPTY));
            ok[12] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.RECOLLITE_LENS.get());
        }
        FakePlayer holder = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("44444444-1111-2222-3333-666666666667"), "Hollows2QaLens"));
        holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RECOLLITE_LENS.get()));
        ok[13] = ModItems.RECOLLITE_LENS.get() instanceof ChronicleLensItem && ChronicleLensItem.isHeld(holder);
        holder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // Nothing before the hollows may need recollite: the first lens and needle (and the field guide) take amethyst.
        List<String> early = new ArrayList<>();
        for (String id : new String[] {"chronicle_lens", "extraction_needle", "field_guide"}) {
            Optional<RecipeHolder<?>> found = server.getRecipeManager().byKey(recipe(id));
            boolean takesAmethyst = found.isPresent() && takes(found.get().value(), Items.AMETHYST_SHARD);
            boolean needsRecollite = found.isPresent() && needsOnly(found.get().value(), ModItems.RECOLLITE_SHARD.get());
            if (!takesAmethyst || needsRecollite) {
                early.add(id);
            }
        }
        ok[14] = early.isEmpty();
        notes.add("earlyRecipesWithoutAmethyst=" + early);

        // ---------- audio ----------
        int sounds = 0;
        for (var holderSound : List.of(ModSounds.MUSIC_MEMORY_HOLLOWS, ModSounds.AMBIENT_HOLLOWS_ADDITIONS, ModSounds.AMBIENT_HOLLOWS_MOOD, ModSounds.FLICKER_CATCH)) {
            if (BuiltInRegistries.SOUND_EVENT.containsKey(holderSound.getId())) {
                sounds++;
            }
        }
        Holder<Biome> hollows = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Hollows.MEMORY_HOLLOWS);
        BackgroundMusic music = hollows.value().getAttributes().applyModifier(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.EMPTY);
        boolean ownMusic = music.defaultMusic().isPresent() && music.defaultMusic().get().sound().is(ModSounds.MUSIC_MEMORY_HOLLOWS.getId());
        boolean ambience = hollows.value().getAttributes().contains(EnvironmentAttributes.AMBIENT_SOUNDS);
        ok[15] = sounds == 4 && ownMusic && ambience;
        notes.add("sounds=" + sounds + " music=" + ownMusic + " ambience=" + ambience);

        // ---------- advancements and guide ----------
        ok[16] = server.getAdvancements().get(id("sunken_archive")) != null && server.getAdvancements().get(id("flicker_caught")) != null
                && server.getAdvancements().get(id("recollite_lens")) != null && server.getAdvancements().get(id("recipes/recollite_lens")) != null;
        int hollowsPage = -1;
        int sunkenPage = -1;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (GuideBook.pageId(i).equals("hollows")) {
                hollowsPage = i;
            } else if (GuideBook.pageId(i).equals("sunken")) {
                sunkenPage = i;
            }
        }
        ok[17] = hollowsPage >= 0 && sunkenPage == hollowsPage + 1 && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("hollows2qa", NAMES, ok, notes);
    }

    private static int count(ChunkMemory memory, ImprintTag tag) {
        int n = 0;
        for (int i = 0; i < memory.imprintCount(); i++) {
            if (memory.imprintAt(i).tag() == tag) {
                n++;
            }
        }
        return n;
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

    /** Some ingredient of the recipe takes {@code item}. */
    private static boolean takes(Recipe<?> recipe, Item item) {
        ItemStack stack = new ItemStack(item);
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Some ingredient of the recipe takes {@code item} and nothing in the memory crystal family besides it. */
    private static boolean needsOnly(Recipe<?> recipe, Item item) {
        ItemStack stack = new ItemStack(item);
        ItemStack amethyst = new ItemStack(Items.AMETHYST_SHARD);
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.test(stack) && !ingredient.test(amethyst)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceKey<Recipe<?>> recipe(String path) {
        return ResourceKey.create(Registries.RECIPE, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
