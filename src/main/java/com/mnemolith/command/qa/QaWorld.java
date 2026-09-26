package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.*;
import java.util.Optional;
import com.mojang.datafixers.util.Pair;
import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.CompositionFormula;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.content.item.CatalogFragmentItem;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.network.OpenCatalogPayload;
import com.mnemolith.network.PressureSync;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.ModFeatures;
import com.mnemolith.worldgen.WorldgenTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Mute, lens, catalog, recipes, the guide, and worldgen checks. */
public final class QaWorld {
    private QaWorld() {}

    private static final Identifier OBSERVATORY_ID = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chronicle_observatory");
    private static final Identifier LOOT_ID = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chests/chronicle_observatory");

    static boolean mute(ServerLevel level, BlockPos pos) {
        clear(level, pos);
        level.setBlock(pos, ModBlocks.MUTE_STONE.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        boolean silence = hasTag(level, pos, ImprintTag.SILENCE);
        boolean blocked = LoadedChunkMemory.isMuted(level, pos) && !ImprintWriter.tryWrite(level, pos, ImprintTag.BUILD, null, false);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        return silence && blocked;
    }

    static boolean lens(ServerLevel level, BlockPos pos) {
        clear(level, pos);
        if (!ImprintWriter.tryWrite(level, pos, ImprintTag.EXPLOSION, null, false)) {
            return false;
        }
        ChunkMemory memory = memory(level, pos);
        if (memory == null) {
            return false;
        }
        int serverBand = MemoryPressure.band(memory.cachedPressure()).ordinal();
        int lensBand = PressureSync.originBand(level, pos);
        int walked = PressureSync.timedPoll(level, pos);
        boolean stamp = PressureSync.perfStampMatches(level, pos);
        int skipped = PressureSync.timedPoll(level, pos);
        return lensBand == serverBand
                && serverBand == PressureBand.SATURATED.ordinal()
                && walked == 1
                && stamp
                && skipped == 0;
    }

    static boolean catalog(ServerLevel level, FakePlayer player, BlockPos pos) {
        reset(player);
        OpenCatalogPayload empty = CatalogFragmentItem.payloadFor(player);
        if (empty.tags() != 0 || empty.formulas() != 0) {
            return false;
        }
        clear(level, pos);
        if (!ImprintWriter.tryWrite(level, pos, ImprintTag.EXPLOSION, null, false)) {
            return false;
        }
        if (ImprintWriter.extract(level, pos, player).isEmpty()) {
            return false;
        }
        OpenCatalogPayload opened = CatalogFragmentItem.payloadFor(player);
        int explosion = 1 << ImprintTag.EXPLOSION.ordinal();
        if (opened.tags() != explosion || opened.formulas() != 0) {
            return false;
        }
        ComposeResult composed = compose(level, pos, player, ImprintTag.DEATH, ImprintTag.SILENCE);
        OpenCatalogPayload after = CatalogFragmentItem.payloadFor(player);
        int death = 1 << ImprintTag.DEATH.ordinal();
        int silence = 1 << ImprintTag.SILENCE.ordinal();
        int fire = 1 << ImprintTag.FIRE.ordinal();
        boolean tags = (after.tags() & explosion) != 0
                && (after.tags() & death) != 0
                && (after.tags() & silence) != 0
                && (after.tags() & fire) == 0;
        return composed.success()
                && after.formulas() == (1 << CompositionFormula.UNRECORDED.ordinal())
                && tags;
    }

    static boolean recipe(ServerLevel level) {
        Identifier id = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "catalog_fragment");
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
        boolean loaded = level.getServer().getRecipeManager().byKey(key).isPresent();
        return loaded && resourceContains(level, "recipe/catalog_fragment.json", "\"id\": \"mnemolith:catalog_fragment\"");
    }

    static boolean guide(ServerLevel level) {
        if (GuideBook.pageCount() != GuideBook.PAGE_COUNT || !GuideBook.ITEM_ID.equals("field_guide")) {
            return false;
        }
        Identifier id = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, GuideBook.ITEM_ID);
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
        boolean loaded = level.getServer().getRecipeManager().byKey(key).isPresent();
        boolean recipeJson = resourceContains(level, "recipe/field_guide.json", "\"id\": \"mnemolith:field_guide\"");
        boolean lootJson = resourceContains(level, "loot_table/chests/chronicle_observatory.json", "\"name\": \"mnemolith:field_guide\"");
        boolean pages = true;
        for (int index = 0; index < GuideBook.pageCount(); index++) {
            pages &= GuideBook.pageId(index) != null
                    && GuideBook.titleKey(index).startsWith("mnemolith.guide.")
                    && GuideBook.texture(index).getPath().endsWith(GuideBook.pageId(index) + ".png");
        }
        // Language files and page art are client assets. A dedicated resource manager does not serve them.
        return loaded && recipeJson && lootJson && pages && ModItems.FIELD_GUIDE.get() != null;
    }

    static boolean vein(ServerLevel level, BlockPos pos) {
        BlockPos origin = pos.below(8);
        // The feature replaces stone. A repeat pass, or dirt, would place nothing, so lay stone on both axes first.
        int length = WorldgenTuning.veinSize();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int step = 0; step < length; step++) {
            for (int axis = 0; axis < 2; axis++) {
                int x = origin.getX() + (axis == 0 ? step - length / 2 : 0);
                int z = origin.getZ() + (axis == 0 ? 0 : step - length / 2);
                for (int dy = 0; dy >= -1; dy--) {
                    cursor.set(x, origin.getY() + dy, z);
                    if (level.getBlockState(cursor).getBlock() != Blocks.BEDROCK) {
                        level.setBlock(cursor, Blocks.STONE.defaultBlockState(), 2);
                    }
                }
            }
        }
        clear(level, origin);
        boolean placed = ModFeatures.ARCHIVAL_VEIN.get().placeVein(level, origin, level.getRandom(), true);
        ChunkMemory memory = memory(level, origin);
        int strata = memory == null ? 0 : memory.strataCount();
        if (!placed || strata <= 0) {
            Mnemolith.LOGGER.info("Mnemolith qa vein detail placed={} strata={}", placed, strata);
        }
        return placed && strata > 0;
    }

    static boolean pocket(ServerLevel level, BlockPos pos) {
        BlockPos floor = pos.below(4);
        if (floor.getY() <= level.getMinY()) {
            return false;
        }
        // The feature only replaces stone or air. A surface column is dirt, so the check lays a stone volume first.
        int half = WorldgenTuning.POCKET_HALF;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    cursor.set(floor.getX() + dx, floor.getY() + dy, floor.getZ() + dz);
                    level.setBlock(cursor, Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        boolean placed = ModFeatures.MUTE_POCKET.get().placePocket(level, pos, level.getRandom(), true);
        boolean muted = LoadedChunkMemory.isMuted(level, floor);
        if (!placed || !muted) {
            Mnemolith.LOGGER.info(
                    "Mnemolith qa pocket detail placed={} muted={} floor={},{},{}",
                    placed,
                    muted,
                    floor.getX(),
                    floor.getY(),
                    floor.getZ());
        }
        return placed && muted;
    }

    /** The observatory template places, and its loot chest holds the lens or the needle. */
    static boolean observatory(ServerLevel level, BlockPos placeAt) {
        boolean chest = placeTemplate(level, placeAt);
        Mnemolith.LOGGER.info("Mnemolith qa observatory chest={}", chest);
        return chest;
    }

    /**
     * The structure is registered and worldgen can locate one near {@code searchFrom}. Needs a world with structure
     * generation on: the game test server's world has it off, so the game test waives this one check.
     */
    static boolean locateObservatory(ServerLevel level, BlockPos searchFrom) {
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, OBSERVATORY_ID);
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Optional<Holder.Reference<Structure>> holder = structures.get(key);
        if (holder.isEmpty()) {
            Mnemolith.LOGGER.info("Mnemolith qa observatory registered=false");
            return false;
        }
        BlockPos located = locate(level, searchFrom, holder.get());
        Mnemolith.LOGGER.info("Mnemolith qa observatory registered=true located={}", located);
        return located != null;
    }

    static boolean placeTemplate(ServerLevel level, BlockPos pos) {
        Optional<StructureTemplate> template = level.getStructureManager().get(OBSERVATORY_ID);
        if (template.isEmpty()) {
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings();
        if (!template.get().placeInWorld(level, pos, pos, settings, level.getRandom(), 2)) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 16; dx++) {
            for (int dy = -2; dy <= 10; dy++) {
                for (int dz = -4; dz <= 16; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockEntity entity = level.getBlockEntity(cursor);
                    if (!(entity instanceof RandomizableContainerBlockEntity chest)) {
                        continue;
                    }
                    ResourceKey<LootTable> loot = chest.getLootTable();
                    if (loot != null && LOOT_ID.equals(loot.identifier())) {
                        chest.unpackLootTable(null);
                        return containsLensOrNeedle(chest);
                    }
                }
            }
        }
        return false;
    }

    static BlockPos locate(ServerLevel level, BlockPos pos, Holder<Structure> structure) {
        try {
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level,
                    HolderSet.direct(structure),
                    pos,
                    4,
                    false);
            return found == null ? null : found.getFirst();
        } catch (RuntimeException ex) {
            Mnemolith.LOGGER.error("Mnemolith qa locate failed", ex);
            return null;
        }
    }

    static boolean loot(ServerLevel level) {
        return resourceContains(level, "loot_table/chests/chronicle_observatory.json", "\"name\": \"mnemolith:catalog_fragment\"");
    }

    static boolean containsLensOrNeedle(Container chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(ModItems.CHRONICLE_LENS.get()) || stack.is(ModItems.EXTRACTION_NEEDLE.get())) {
                return true;
            }
        }
        return false;
    }
}
