package com.mnemolith.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Seeds quiet ancient imprints into chunks that hold vanilla structure starts.
 * Rules come from {@code data/<ns>/mnemolith/imprint_seed/*.json}.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class StructureMemorySeeds {
    public record SeedRule(List<Identifier> structures, List<ImprintTag> tags, int intensity, int count) {}

    private static List<SeedRule> RULES = builtins();

    private StructureMemorySeeds() {}

    public static void trySeed(ServerLevel level, LevelChunk chunk) {
        if (!CommonConfig.STRUCTURE_MEMORY_SEEDS.get() || RULES.isEmpty()) {
            return;
        }
        ChunkMemory existing = LoadedChunkMemory.existing(chunk);
        // Once per chunk: without the flag, extracting the seeded imprints and reloading the chunk would seed them again.
        if (existing != null && (existing.imprintCount() > 0 || existing.structureSeeded())) {
            return;
        }
        Map<Structure, StructureStart> starts = chunk.getAllStarts();
        if (starts.isEmpty()) {
            return;
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<ImprintTag> toWrite = new ArrayList<>();
        int intensity = 1;
        for (Structure structure : starts.keySet()) {
            StructureStart start = starts.get(structure);
            if (start == null || !start.isValid()) {
                continue;
            }
            Optional<ResourceKey<Structure>> key = registry.getResourceKey(structure);
            if (key.isEmpty()) {
                continue;
            }
            Identifier id = key.get().identifier();
            for (SeedRule rule : RULES) {
                if (!rule.structures().contains(id)) {
                    continue;
                }
                intensity = Math.max(1, Math.min(rule.intensity(), 2));
                for (int i = 0; i < Math.max(1, rule.count()); i++) {
                    toWrite.addAll(rule.tags());
                }
            }
        }
        if (toWrite.isEmpty()) {
            return;
        }
        // Cap so a fortress does not wake archivists on first visit.
        while (toWrite.size() > 4) {
            toWrite.remove(toWrite.size() - 1);
        }
        int x = chunk.getPos().getMiddleBlockX();
        int z = chunk.getPos().getMiddleBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        ChunkMemory memory = existing != null ? existing : LoadedChunkMemory.getOrCreate(chunk);
        memory.setStructureSeeded(true);
        long now = level.getGameTime();
        for (ImprintTag tag : toWrite) {
            memory.addImprint(
                    new Imprint(tag, intensity, pos.immutable(), Optional.empty(), Imprint.contextHash(tag, pos, now), now),
                    CommonConfig.MAX_IMPRINTS_PER_CHUNK.get());
        }
        MemoryPressure.recomputeOnLoad(chunk, memory, true);
        Mnemolith.LOGGER.debug("Mnemolith structure seed at chunk {} {} tags={}", chunk.getPos().x(), chunk.getPos().z(), toWrite.size());
    }

    @SubscribeEvent
    public static void onReload(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "imprint_seeds"), new Loader());
    }

    private static final class Loader extends SimplePreparableReloadListener<List<SeedRule>> {
        @Override
        protected List<SeedRule> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<Identifier, Resource> found =
                    manager.listResources("mnemolith/imprint_seed", path -> path.getPath().endsWith(".json"));
            List<SeedRule> loaded = new ArrayList<>();
            for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
                try (var reader = entry.getValue().openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    List<Identifier> structures = new ArrayList<>();
                    for (JsonElement el : json.getAsJsonArray("structures")) {
                        structures.add(Identifier.parse(el.getAsString()));
                    }
                    List<ImprintTag> tags = new ArrayList<>();
                    for (JsonElement el : json.getAsJsonArray("tags")) {
                        tags.add(ImprintTag.CODEC.parse(JsonOps.INSTANCE, el).getOrThrow());
                    }
                    int intensity = json.has("intensity") ? json.get("intensity").getAsInt() : 1;
                    int count = json.has("count") ? json.get("count").getAsInt() : 1;
                    loaded.add(new SeedRule(List.copyOf(structures), List.copyOf(tags), intensity, count));
                } catch (Exception ex) {
                    Mnemolith.LOGGER.error("Failed to load imprint seed {}", entry.getKey(), ex);
                }
            }
            return loaded;
        }

        @Override
        protected void apply(List<SeedRule> prepared, ResourceManager manager, ProfilerFiller profiler) {
            RULES = prepared.isEmpty() ? builtins() : List.copyOf(prepared);
            Mnemolith.LOGGER.info("Mnemolith loaded {} imprint seed rules", RULES.size());
        }
    }

    private static List<SeedRule> builtins() {
        return List.of(
                new SeedRule(List.of(Identifier.parse("minecraft:ancient_city")), List.of(ImprintTag.SILENCE, ImprintTag.DEATH), 1, 1),
                new SeedRule(List.of(Identifier.parse("minecraft:stronghold")), List.of(ImprintTag.SILENCE, ImprintTag.BUILD), 1, 1),
                new SeedRule(List.of(Identifier.parse("minecraft:mineshaft"), Identifier.parse("minecraft:mineshaft_mesa")), List.of(ImprintTag.PATH, ImprintTag.BUILD), 1, 1),
                new SeedRule(List.of(Identifier.parse("minecraft:fortress"), Identifier.parse("minecraft:bastion_remnant")), List.of(ImprintTag.FIRE), 1, 1),
                new SeedRule(List.of(Identifier.parse("minecraft:end_city")), List.of(ImprintTag.EXPLOSION), 1, 1));
    }
}
