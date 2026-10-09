package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.worldgen.feature.HollowGroundFeature;
import com.mnemolith.worldgen.feature.HollowRemnantFeature;
import com.mnemolith.worldgen.feature.HollowSinkFeature;
import com.mnemolith.worldgen.hollows.BiomeRegion;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.HollowRegions;
import com.mnemolith.worldgen.hollows.Hollows;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith hollowsqa}. Memory Hollows, stage 1: the biome and its region, the two biome source hooks on a
 * fresh overworld and nether source, the climate window, the ground pass, remnant and sink features on a test pad, the
 * turf and ore drops, flicker scene choice and the density switch, recipes, advancements, the memory crystal tag and
 * the guide page. Works on the flat game test world (the hooks are checked on sources built here).
 */
public final class HollowsQa {
    private static final String[] NAMES = {"biome", "region", "hosts", "possibleOverworld", "possibleNether", "windowReplaces", "outsideStays",
            "nonHostStays", "ground", "ore", "remnant", "sink", "turfHoldsFlower", "oreDrops", "turfDrops", "flickerScenes", "flickerPick",
            "densityOff", "recipes", "advancements", "crystalTag", "guidePage"};
    private static final UUID PLAYER = UUID.fromString("44444444-1111-2222-3333-555555555555");
    private static final TagKey<net.minecraft.world.item.Item> MEMORY_CRYSTALS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "memory_crystals"));
    private static int salt;

    private HollowsQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Optional<Holder.Reference<Biome>> hollows = biomes.get(Hollows.MEMORY_HOLLOWS);
        ok[0] = hollows.isPresent();
        BiomeRegion region = HollowRegions.active().stream().filter(r -> r.biome().is(Hollows.MEMORY_HOLLOWS)).findFirst().orElse(null);
        ok[1] = region != null;
        int hostCount = 0;
        for (Holder<Biome> ignored : biomes.getTagOrEmpty(Hollows.HOSTS)) {
            hostCount++;
        }
        Holder<Biome> plains = biomes.getOrThrow(Biomes.PLAINS);
        Holder<Biome> desert = biomes.getOrThrow(Biomes.DESERT);
        ok[2] = hostCount >= 8 && plains.is(Hollows.HOSTS) && !desert.is(Hollows.HOSTS);
        notes.add("regions=" + HollowRegions.active().size() + " hosts=" + hostCount);
        if (!ok[0] || region == null) {
            return new QaReport("hollowsqa", NAMES, ok, notes);
        }

        // ---------- the two hooks, on sources built here (the game test world is flat) ----------
        HolderLookup.RegistryLookup<net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList> presets =
                level.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        MultiNoiseBiomeSource overworld = MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        MultiNoiseBiomeSource nether = MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
        ok[3] = overworld.possibleBiomes().contains(hollows.get());
        ok[4] = !nether.possibleBiomes().contains(hollows.get()) && !nether.possibleBiomes().isEmpty();
        float weird = mid(region.weirdness());
        float erosion = mid(region.erosion());
        float outside = Climate.unquantizeCoord(region.weirdness().max()) + 0.15F;
        int inside = 0;
        int turned = 0;
        int leaked = 0;
        for (float t = -0.4F; t <= 0.5F; t += 0.15F) {
            for (float h = -0.3F; h <= 0.3F; h += 0.15F) {
                for (float c = 0.05F; c <= 0.85F; c += 0.2F) {
                    Holder<Biome> in = overworld.getNoiseBiome(Climate.target(t, h, c, erosion, 0.0F, weird));
                    if (Hollows.is(in)) {
                        turned++;
                    }
                    inside++;
                    if (Hollows.is(overworld.getNoiseBiome(Climate.target(t, h, c, erosion, 0.0F, outside)))) {
                        leaked++;
                    }
                }
            }
        }
        ok[5] = turned > 0 && HollowRegions.replace(plains, Climate.target(0.0F, 0.0F, 0.3F, erosion, 0.0F, weird)) != null;
        ok[6] = leaked == 0 && HollowRegions.replace(plains, Climate.target(0.0F, 0.0F, 0.3F, erosion, 0.0F, outside)) == null;
        ok[7] = HollowRegions.replace(desert, Climate.target(0.0F, 0.0F, 0.3F, erosion, 0.0F, weird)) == null;
        notes.add("window samples=" + inside + " turned=" + turned + " leakedOutside=" + leaked);

        // ---------- features on a pad ----------
        BlockPos a = column(level, (spawn.getX() >> 4) + 70 + salt * 3, (spawn.getZ() >> 4) - 58);
        tickColumn(level, a);
        try {
            int y0 = a.getY();
            fill(level, a, 9, y0, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
            HollowGroundFeature.Result result = HollowGroundFeature.convert(level, a.getX() - 2, a.getZ() - 2, 4, RandomSource.create(41L + salt), true);
            BlockPos top = new BlockPos(a.getX(), y0 - 1, a.getZ());
            ok[8] = result.turf() == 16 && level.getBlockState(top).is(ModBlocks.HOLLOW_TURF.get())
                    && level.getBlockState(top.below()).is(Blocks.DIRT)
                    && level.getBlockState(top.below(6)).is(ModBlocks.HOLLOWSTONE.get())
                    && level.getBlockState(top.below(HollowGroundFeature.LAYER_BOTTOM + 2)).is(Blocks.STONE)
                    && level.getBlockState(top.offset(3, 0, 3)).is(Blocks.GRASS_BLOCK);
            ok[9] = result.ore() > 0 && result.ore() == count(level, a.offset(-2, -20, -2), a.offset(1, 0, 1), ModBlocks.RECOLLITE_ORE.get());
            notes.add("ground columns=" + result.columns() + " turf=" + result.turf() + " stone=" + result.stone() + " ore=" + result.ore());

            BlockPos pillar = a.offset(-6, 0, 6);
            int remnant = HollowRemnantFeature.build(level, pillar, RandomSource.create(7L));
            ok[10] = remnant >= 3 && (level.getBlockState(pillar).is(ModBlocks.HOLLOWSTONE_BRICKS.get()) || level.getBlockState(pillar).is(ModBlocks.HOLLOWSTONE.get())
                    || level.getBlockState(pillar).is(ModBlocks.RECOLLITE_ORE.get()));
            BlockPos water = a.offset(6, 0, 6);
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
            boolean refusesWater = HollowRemnantFeature.build(level, water, RandomSource.create(7L)) == 0;
            ok[10] &= refusesWater;
            notes.add("remnant blocks=" + remnant + " refusesWater=" + refusesWater);

            BlockPos dish = a.offset(-5, 0, -5);
            int removed = HollowSinkFeature.press(level, dish, 3, 2);
            ok[11] = removed > 0 && level.getBlockState(dish.below()).isAir() && level.getBlockState(dish.below(3)).is(Blocks.GRASS_BLOCK);
            notes.add("sink removed=" + removed);

            BlockPos turf = a.offset(5, 0, -5);
            level.setBlock(turf.below(), ModBlocks.HOLLOW_TURF.get().defaultBlockState(), 2);
            ok[12] = ModBlocks.FORGET_ME_NOT.get().defaultBlockState().canSurvive(level, turf);

            BlockState ore = ModBlocks.RECOLLITE_ORE.get().defaultBlockState();
            List<ItemStack> plain = Block.getDrops(ore, level, turf, null, null, new ItemStack(Items.STONE_PICKAXE));
            ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
            silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
            List<ItemStack> silked = Block.getDrops(ore, level, turf, null, null, silk);
            int shards = plain.stream().filter(s -> s.is(ModItems.RECOLLITE_SHARD.get())).mapToInt(ItemStack::getCount).sum();
            ok[13] = shards >= 1 && shards <= 2 && silked.size() == 1 && silked.get(0).is(ModItems.RECOLLITE_ORE.get());
            List<ItemStack> turfPlain = Block.getDrops(ModBlocks.HOLLOW_TURF.get().defaultBlockState(), level, turf, null, null, ItemStack.EMPTY);
            List<ItemStack> turfSilk = Block.getDrops(ModBlocks.HOLLOW_TURF.get().defaultBlockState(), level, turf, null, null, silk);
            ok[14] = turfPlain.size() == 1 && turfPlain.get(0).is(Items.DIRT) && turfSilk.size() == 1 && turfSilk.get(0).is(ModItems.HOLLOW_TURF.get());
            notes.add("ore shards=" + shards + " silk=" + silked.size());

            // ---------- flickers ----------
            ChunkMemory fire = new ChunkMemory();
            fire.addImprint(new Imprint(ImprintTag.FIRE, 5, a, Optional.empty(), 1, 0L), 8);
            RandomSource random = RandomSource.create(3L);
            ok[15] = HollowFlickers.scene(ImprintTag.FALL) == HollowFlickers.FALL && HollowFlickers.scene(ImprintTag.DEATH) == HollowFlickers.KNEEL
                    && HollowFlickers.scene(ImprintTag.FIRE) == HollowFlickers.FLARE && HollowFlickers.scene(ImprintTag.BUILD) == HollowFlickers.WORK
                    && HollowFlickers.scene(null) == HollowFlickers.WALK && HollowFlickers.imprintAt(fire, random) == ImprintTag.FIRE
                    && HollowFlickers.imprintAt(new ChunkMemory(), random) == null;
            boolean here = Hollows.is(level.getBiome(a));
            int picked = 0;
            for (int i = 0; i < 20; i++) {
                if (HollowFlickers.pick(level, a, random) != null) {
                    picked++;
                }
            }
            ok[16] = here ? picked > 0 : picked == 0;
            notes.add("flicker inBiome=" + here + " picked=" + picked + "/20");
            FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "HollowsQa"));
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            long sent = HollowFlickers.sentCount();
            boolean any = false;
            for (int i = 0; i < 10; i++) {
                any |= HollowFlickers.offer(level, player, 0.0D, level.getGameTime() + i * 1000L);
            }
            ok[17] = !any && HollowFlickers.sentCount() == sent;
        } finally {
            releaseColumn(level, ChunkPos.containing(a));
        }

        // ---------- data ----------
        String[] recipes = {"hollowstone_bricks", "hollowstone_brick_stairs", "hollowstone_brick_slab_from_hollowstone_stonecutting", "recollite_block",
                "recollite_shard_from_smelting", "recollite_shard_from_blasting", "light_blue_dye_from_forget_me_not"};
        int found = 0;
        for (String id : recipes) {
            if (level.getServer().getRecipeManager().byKey(recipe(id)).isPresent()) {
                found++;
            }
        }
        ok[18] = found == recipes.length;
        ok[19] = level.getServer().getAdvancements().get(id("hollows_found")) != null && level.getServer().getAdvancements().get(id("recollite")) != null
                && level.getServer().getAdvancements().get(id("recipes/recollite_block")) != null;
        boolean guide = false;
        Optional<RecipeHolder<?>> book = level.getServer().getRecipeManager().byKey(recipe("field_guide"));
        if (book.isPresent() && book.get().value() instanceof CraftingRecipe crafting) {
            guide = crafting.matches(CraftingInput.of(1, 2, List.of(new ItemStack(Items.BOOK), new ItemStack(ModItems.RECOLLITE_SHARD.get()))), level)
                    && crafting.matches(CraftingInput.of(1, 2, List.of(new ItemStack(Items.BOOK), new ItemStack(Items.AMETHYST_SHARD))), level);
        }
        // Every Mnemolith ingredient that takes an amethyst shard must take a recollite shard too (the guide promises it).
        ItemStack amethyst = new ItemStack(Items.AMETHYST_SHARD);
        ItemStack shard = new ItemStack(ModItems.RECOLLITE_SHARD.get());
        int amethystSlots = 0;
        List<String> strict = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
            // Crafting and cooking only: a smithing trim takes #minecraft:trim_materials, which the shard is not.
            if (!holder.id().identifier().getNamespace().equals(Mnemolith.MOD_ID) || holder.value() instanceof net.minecraft.world.item.crafting.SmithingRecipe
                    || holder.value().placementInfo().isImpossibleToPlace()) {
                continue;
            }
            for (net.minecraft.world.item.crafting.Ingredient ingredient : holder.value().placementInfo().ingredients()) {
                if (ingredient.test(amethyst)) {
                    amethystSlots++;
                    if (!ingredient.test(shard)) {
                        strict.add(holder.id().identifier().getPath());
                    }
                }
            }
        }
        ok[20] = shard.is(MEMORY_CRYSTALS) && guide && amethystSlots > 0 && strict.isEmpty();
        notes.add("crystalTag amethystSlots=" + amethystSlots + " strict=" + strict);
        boolean page = false;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            page |= GuideBook.pageId(i).equals("hollows");
        }
        ok[21] = page && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        notes.add("recipes=" + found + "/" + recipes.length + " guideRecipeTakesShard=" + guide);
        return new QaReport("hollowsqa", NAMES, ok, notes);
    }

    private static float mid(Climate.Parameter range) {
        return (Climate.unquantizeCoord(range.min()) + Climate.unquantizeCoord(range.max())) / 2.0F;
    }

    /** A square of side 2r+1 at the pad: {@code top} at y0-1, three of {@code under}, stone down to y0-24, air above. */
    private static void fill(ServerLevel level, BlockPos center, int r, int y0, BlockState top, BlockState under) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int y = y0 - 24; y < y0 + 10; y++) {
                    BlockState state = y >= y0 ? Blocks.AIR.defaultBlockState()
                            : y == y0 - 1 ? top : y >= y0 - 4 ? under : Blocks.STONE.defaultBlockState();
                    level.setBlock(new BlockPos(center.getX() + dx, y, center.getZ() + dz), state, 2 | 16);
                }
            }
        }
    }

    private static int count(ServerLevel level, BlockPos from, BlockPos to, Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (level.getBlockState(pos).is(block)) {
                n++;
            }
        }
        return n;
    }

    private static ResourceKey<Recipe<?>> recipe(String path) {
        return ResourceKey.create(Registries.RECIPE, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
