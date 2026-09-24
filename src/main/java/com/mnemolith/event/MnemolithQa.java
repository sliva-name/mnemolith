package com.mnemolith.event;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.Composition;
import com.mnemolith.content.composition.CompositionFormula;
import com.mnemolith.content.item.CatalogFragmentItem;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.ai.CopiedActionKind;
import com.mnemolith.entity.ai.PathLedger;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.OpenCatalogPayload;
import com.mnemolith.network.PressureSync;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.ModFeatures;
import com.mnemolith.worldgen.WorldgenTuning;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith qa}. One dedicated-server pass over the survival loop.
 * It stays off the chunks owned by {@code smoke}, {@code perf}, and {@code mpsmoke}.
 */
public final class MnemolithQa {
    private static final Identifier OBSERVATORY_ID = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chronicle_observatory");
    private static final Identifier LOOT_ID = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chests/chronicle_observatory");
    private static final int CHECKS = 17;
    private static int salt = 1;

    private MnemolithQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        salt++;
        BlockPos origin = BlockPos.containing(source.getPosition());
        int chunkX = (origin.getX() >> 4) + 12 + salt * 8;
        int chunkZ = (origin.getZ() >> 4);

        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "MnemolithQa"));
        reset(player);

        boolean writes = writes(level, player, chunkX, chunkZ);
        boolean bands = bands(level, column(level, chunkX + 8, chunkZ));
        boolean extract = extract(level, player, column(level, chunkX + 9, chunkZ));
        boolean formulas = formulas(level, player, column(level, chunkX + 10, chunkZ));
        boolean quietFail = quietFail(level, player, column(level, chunkX + 11, chunkZ));
        boolean loudFail = loudFail(level, player, column(level, chunkX + 12, chunkZ));
        boolean mute = mute(level, column(level, chunkX + 13, chunkZ));
        boolean lens = lens(level, column(level, chunkX + 14, chunkZ));
        boolean catalog = catalog(level, player, column(level, chunkX + 15, chunkZ));
        boolean recipe = recipe(level);
        boolean vein = vein(level, column(level, chunkX + 16, chunkZ));
        BlockPos observatoryAt = column(level, chunkX + 20, chunkZ);
        boolean pocket = pocket(level, column(level, chunkX + 18, chunkZ));
        boolean observatory = observatory(level, origin, observatoryAt.offset(0, 24, 0));
        boolean loot = loot(level);
        BlockPos mobs = column(level, chunkX + 24, chunkZ);
        boolean strider = strider(level, mobs);
        boolean archivist = archivist(level, mobs.offset(2, 0, 0));
        boolean replicant = replicant(level, mobs.offset(-2, 0, 0));

        String dimension = level.dimension().identifier().toString();
        Mnemolith.LOGGER.info(
                "Mnemolith qa writes={} bands={} extract={} formulas={} quietFail={} loudFail={} mute={} lens={} catalog={} recipe={} vein={} pocket={} observatory={} loot={} strider={} archivist={} replicant={} dimension={}",
                writes,
                bands,
                extract,
                formulas,
                quietFail,
                loudFail,
                mute,
                lens,
                catalog,
                recipe,
                vein,
                pocket,
                observatory,
                loot,
                strider,
                archivist,
                replicant,
                dimension);
        int passed = count(
                writes, bands, extract, formulas, quietFail, loudFail, mute, lens,
                catalog, recipe, vein, pocket, observatory, loot, strider, archivist, replicant);
        int reported = passed;
        source.sendSuccess(() -> Component.translatable("mnemolith.command.qa", reported, CHECKS), true);
        return passed;
    }

    private static boolean writes(ServerLevel level, FakePlayer player, int chunkX, int chunkZ) {
        boolean path = walk(level, player, column(level, chunkX, chunkZ));
        boolean build = writeTag(level, column(level, chunkX + 1, chunkZ), ImprintTag.BUILD);
        boolean death = writeTag(level, column(level, chunkX + 2, chunkZ), ImprintTag.DEATH);
        boolean explosion = writeTag(level, column(level, chunkX + 3, chunkZ), ImprintTag.EXPLOSION);
        boolean fall = writeTag(level, column(level, chunkX + 4, chunkZ), ImprintTag.FALL);
        boolean silence = writeTag(level, column(level, chunkX + 5, chunkZ), ImprintTag.SILENCE);
        return path && build && death && explosion && fall && silence;
    }

    private static boolean walk(ServerLevel level, FakePlayer player, BlockPos pos) {
        BlockPos next = pos.offset(16, 0, 0);
        level.getChunkAt(pos);
        level.getChunkAt(next);
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        PathLedger.note(player);
        player.setPos(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D);
        PathLedger.note(player);
        return hasTag(level, pos, ImprintTag.PATH) || hasTag(level, next, ImprintTag.PATH);
    }

    private static boolean writeTag(ServerLevel level, BlockPos pos, ImprintTag tag) {
        clear(level, pos);
        return ImprintWriter.tryWrite(level, pos, tag, null, false) && hasTag(level, pos, tag);
    }

    private static boolean bands(ServerLevel level, BlockPos pos) {
        int saturated = CommonConfig.SATURATED_THRESHOLD.get();
        int overloaded = CommonConfig.OVERLOADED_THRESHOLD.get();
        int fracture = CommonConfig.FRACTURE_THRESHOLD.get();
        boolean defaults = saturated == 20
                && overloaded == 50
                && fracture == 80
                && CommonConfig.PRESSURE_SOFT_CAP.get() == 100
                && Math.abs(CommonConfig.RECOLLECTION_STORM_THRESHOLD.get() - 1.0D) < 0.001D;
        boolean edges = MemoryPressure.band(saturated - 1) == PressureBand.CALM
                && MemoryPressure.band(saturated) == PressureBand.SATURATED
                && MemoryPressure.band(overloaded - 1) == PressureBand.SATURATED
                && MemoryPressure.band(overloaded) == PressureBand.OVERLOADED
                && MemoryPressure.band(fracture - 1) == PressureBand.OVERLOADED
                && MemoryPressure.band(fracture) == PressureBand.FRACTURE;
        ChunkPos chunk = ChunkPos.containing(pos);
        tickColumn(level, pos);
        try {
            clear(level, pos);
            discardReplicants(level, pos);
            int calm = ImprintWriter.spike(level, pos, saturated - 1);
            boolean calmLive = calm == saturated - 1 && MemoryPressure.band(calm) == PressureBand.CALM;
            int saturatedLive = ImprintWriter.spike(level, pos, 1);
            boolean saturatedOk = saturatedLive == saturated && MemoryPressure.band(saturatedLive) == PressureBand.SATURATED;
            clear(level, pos);
            discardReplicants(level, pos);
            int fractured = ImprintWriter.spike(level, pos, fracture);
            ChunkMemory memory = memory(level, pos);
            int replicants = replicantCount(level, pos);
            boolean fractureLive = fractured >= fracture
                    && memory != null
                    && memory.fractured()
                    && MemoryPressure.band(fractured) == PressureBand.FRACTURE
                    && replicants >= 1;
            boolean ok = defaults && edges && calmLive && saturatedOk && fractureLive;
            if (!ok) {
                Mnemolith.LOGGER.info(
                        "Mnemolith qa bands detail defaults={} edges={} calm={} saturated={} fractureLive={} pressure={} fractured={} replicants={} ticking={}",
                        defaults,
                        edges,
                        calmLive,
                        saturatedOk,
                        fractureLive,
                        fractured,
                        memory != null && memory.fractured(),
                        replicants,
                        level.isPositionEntityTicking(pos));
            }
            discardReplicants(level, pos);
            return ok;
        } finally {
            releaseColumn(level, chunk);
        }
    }

    private static boolean extract(ServerLevel level, FakePlayer player, BlockPos pos) {
        reset(player);
        clear(level, pos);
        if (!ImprintWriter.tryWrite(level, pos, ImprintTag.FALL, null, false)) {
            return false;
        }
        if (ImprintWriter.extract(level, pos, player).isEmpty()) {
            return false;
        }
        OpenCatalogPayload payload = CatalogFragmentItem.payloadFor(player);
        return holdsTag(player, ImprintTag.FALL)
                && payload.tags() == (1 << ImprintTag.FALL.ordinal())
                && payload.formulas() == 0;
    }

    private static boolean formulas(ServerLevel level, FakePlayer player, BlockPos pos) {
        reset(player);
        clear(level, pos);
        boolean all = true;
        for (CompositionFormula formula : CompositionFormula.values()) {
            List<ImprintTag> tags = formula.tags();
            if (tags.size() != 2) {
                return false;
            }
            ComposeResult result = compose(level, pos, player, tags.get(0), tags.get(1));
            all &= result.success() && result.formulaOrdinal() == formula.ordinal();
            all &= player.getData(ModAttachments.DISCOVERY.get()).hasFormula(formula.ordinal());
        }
        OpenCatalogPayload payload = CatalogFragmentItem.payloadFor(player);
        return all && Integer.bitCount(payload.formulas() & ((1 << CompositionFormula.values().length) - 1)) == CompositionFormula.values().length;
    }

    private static boolean quietFail(ServerLevel level, FakePlayer player, BlockPos pos) {
        reset(player);
        clear(level, pos);
        discardReplicants(level, pos);
        int before = replicantCount(level, pos);
        ComposeResult result = compose(level, pos, player, ImprintTag.BUILD, ImprintTag.BUILD);
        ChunkMemory memory = memory(level, pos);
        int pressure = memory == null ? -1 : memory.cachedPressure();
        int spike = CommonConfig.FAILURE_PRESSURE_SPIKE.get();
        return !result.success()
                && spike > 0
                && spike < CommonConfig.SATURATED_THRESHOLD.get()
                && pressure == spike
                && MemoryPressure.band(pressure) == PressureBand.CALM
                && replicantCount(level, pos) == before;
    }

    private static boolean loudFail(ServerLevel level, FakePlayer player, BlockPos pos) {
        reset(player);
        ChunkPos chunk = ChunkPos.containing(pos);
        tickColumn(level, pos);
        try {
            clear(level, pos);
            discardReplicants(level, pos);
            ImprintWriter.tryWrite(level, pos, ImprintTag.DEATH, null, false);
            ImprintWriter.tryWrite(level, pos, ImprintTag.EXPLOSION, null, false);
            ChunkMemory before = memory(level, pos);
            if (before == null || MemoryPressure.band(before.cachedPressure()) != PressureBand.OVERLOADED) {
                Mnemolith.LOGGER.info(
                        "Mnemolith qa loudFail detail overloaded=false pressure={}",
                        before == null ? -1 : before.cachedPressure());
                return false;
            }
            int spawned = replicantCount(level, pos);
            ComposeResult result = compose(level, pos, player, ImprintTag.BUILD, ImprintTag.BUILD);
            ChunkMemory after = memory(level, pos);
            PressureBand band = after == null ? PressureBand.CALM : MemoryPressure.band(after.cachedPressure());
            boolean loud = band == PressureBand.OVERLOADED || band == PressureBand.FRACTURE;
            int replicants = replicantCount(level, pos);
            boolean ok = !result.success() && loud && replicants == spawned + 1;
            if (!ok) {
                Mnemolith.LOGGER.info(
                        "Mnemolith qa loudFail detail success={} band={} before={} after={} ticking={}",
                        result.success(),
                        band,
                        spawned,
                        replicants,
                        level.isPositionEntityTicking(pos));
            }
            discardReplicants(level, pos);
            return ok;
        } finally {
            releaseColumn(level, chunk);
        }
    }

    private static boolean mute(ServerLevel level, BlockPos pos) {
        clear(level, pos);
        level.setBlock(pos, ModBlocks.MUTE_STONE.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        boolean silence = hasTag(level, pos, ImprintTag.SILENCE);
        boolean blocked = LoadedChunkMemory.isMuted(level, pos) && !ImprintWriter.tryWrite(level, pos, ImprintTag.BUILD, null, false);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        return silence && blocked;
    }

    private static boolean lens(ServerLevel level, BlockPos pos) {
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

    private static boolean catalog(ServerLevel level, FakePlayer player, BlockPos pos) {
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

    private static boolean recipe(ServerLevel level) {
        Identifier id = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "catalog_fragment");
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
        boolean loaded = level.getServer().getRecipeManager().byKey(key).isPresent();
        return loaded && resourceContains(level, "recipe/catalog_fragment.json", "\"id\": \"mnemolith:catalog_fragment\"");
    }

    private static boolean vein(ServerLevel level, BlockPos pos) {
        boolean placed = ModFeatures.ARCHIVAL_VEIN.get().placeVein(level, pos.below(8), level.getRandom(), true);
        ChunkMemory memory = memory(level, pos.below(8));
        return placed && memory != null && memory.strataCount() > 0;
    }

    private static boolean pocket(ServerLevel level, BlockPos pos) {
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

    private static boolean observatory(ServerLevel level, BlockPos searchFrom, BlockPos placeAt) {
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, OBSERVATORY_ID);
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Optional<Holder.Reference<Structure>> holder = structures.get(key);
        if (holder.isEmpty()) {
            Mnemolith.LOGGER.info("Mnemolith qa observatory registered=false");
            return false;
        }
        boolean chest = placeTemplate(level, placeAt);
        BlockPos located = locate(level, searchFrom, holder.get());
        Mnemolith.LOGGER.info("Mnemolith qa observatory registered=true chest={} located={}", chest, located);
        return chest && located != null;
    }

    private static boolean placeTemplate(ServerLevel level, BlockPos pos) {
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

    private static BlockPos locate(ServerLevel level, BlockPos pos, Holder<Structure> structure) {
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

    private static boolean loot(ServerLevel level) {
        return resourceContains(level, "loot_table/chests/chronicle_observatory.json", "\"name\": \"mnemolith:catalog_fragment\"");
    }

    private static boolean containsLensOrNeedle(Container chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(ModItems.CHRONICLE_LENS.get()) || stack.is(ModItems.EXTRACTION_NEEDLE.get())) {
                return true;
            }
        }
        return false;
    }

    private static boolean strider(ServerLevel level, BlockPos pos) {
        EchoStrider strider = MobSpawns.summonStrider(level, pos);
        if (strider == null) {
            return false;
        }
        strider.beginCharge();
        boolean charged = strider.action() == MobActions.TELEGRAPH;
        strider.discard();
        return charged;
    }

    private static boolean archivist(ServerLevel level, BlockPos pos) {
        Archivist archivist = MobSpawns.summonArchivist(level, pos);
        if (archivist == null) {
            return false;
        }
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, ImprintSlips.of(ImprintTag.DEATH, pos));
        boolean stole = archivist.snatch(level, container, null, true);
        boolean fleeing = archivist.action() == MobActions.FLEE;
        archivist.discard();
        return stole && fleeing && container.getItem(0).isEmpty();
    }

    private static boolean replicant(ServerLevel level, BlockPos pos) {
        MomentReplicant replicant = MobSpawns.summonReplicant(level, pos);
        if (replicant == null) {
            return false;
        }
        replicant.beginTelegraph(CopiedActionKind.MELEE, null);
        boolean telegraph = replicant.action() == MobActions.TELEGRAPH;
        replicant.discard();
        return telegraph;
    }

    private static ComposeResult compose(ServerLevel level, BlockPos pos, FakePlayer player, ImprintTag first, ImprintTag second) {
        SimpleContainer container = new SimpleContainer(3);
        container.setItem(0, ImprintSlips.of(first, pos));
        container.setItem(1, ImprintSlips.of(second, pos));
        return Composition.compose(level, pos, player, container);
    }

    private static boolean resourceContains(ServerLevel level, String path, String needle) {
        Identifier id = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
            return text.indexOf(needle) >= 0;
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean holdsTag(FakePlayer player, ImprintTag tag) {
        Container inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ImprintSlips.isSlip(stack)) {
                continue;
            }
            ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
            if (cast != null && cast.tag() == tag) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTag(ServerLevel level, BlockPos pos, ImprintTag tag) {
        ChunkMemory memory = memory(level, pos);
        return memory != null && memory.tags().contains(tag);
    }

    private static ChunkMemory memory(ServerLevel level, BlockPos pos) {
        return LoadedChunkMemory.existing(level.getChunkAt(pos));
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        LoadedChunkMemory.clear(level.getChunkAt(pos));
    }

    private static void reset(FakePlayer player) {
        player.getInventory().clearContent();
        player.setData(ModAttachments.DISCOVERY.get(), new Discovery());
    }

    private static BlockPos column(ServerLevel level, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinY()) {
            y = level.getSeaLevel();
        }
        return new BlockPos(x, Math.min(level.getMaxY() - 2, y + 1), z);
    }

    private static int replicantCount(ServerLevel level, BlockPos pos) {
        return replicants(level, pos).size();
    }

    private static void discardReplicants(ServerLevel level, BlockPos pos) {
        for (MomentReplicant replicant : replicants(level, pos)) {
            replicant.discard();
        }
    }

    /**
     * Loads the column as entity-ticking. A plain {@code getChunk} leaves far columns hidden from entity queries,
     * and the tracking promotion is queued on the server thread after the chunk future completes.
     */
    private static void tickColumn(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = ChunkPos.containing(pos);
        var future = level.getChunkSource().addTicketAndLoadWithRadius(TicketType.FORCED, chunk, 2);
        var server = level.getServer();
        server.managedBlock(future::isDone);
        long deadline = System.nanoTime() + 2_000_000_000L;
        server.managedBlock(() -> level.isPositionEntityTicking(pos) || System.nanoTime() > deadline);
    }

    private static void releaseColumn(ServerLevel level, ChunkPos chunk) {
        level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, chunk, 2);
    }

    private static List<MomentReplicant> replicants(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = ChunkPos.containing(pos);
        AABB column = new AABB(
                chunk.getMinBlockX(),
                level.getMinY(),
                chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1.0D,
                level.getMaxY(),
                chunk.getMaxBlockZ() + 1.0D);
        return level.getEntitiesOfClass(MomentReplicant.class, column);
    }

    private static int count(boolean... flags) {
        int passed = 0;
        for (boolean flag : flags) {
            if (flag) {
                passed++;
            }
        }
        return passed;
    }
}
