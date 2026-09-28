package com.mnemolith.content.composition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mnemolith.Mnemolith;
import com.mnemolith.imprint.ImprintTag;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Datapack drum formulas under {@code data/<ns>/mnemolith/composition_formula/*.json}.
 * The first four built-in ids keep discovery bit indices 0–3 for old saves.
 * The client keeps the same built-in list for UI; the server replaces it from datapacks on reload.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class CompositionRecipes {
    private static final Identifier[] BUILTIN_ORDER = {
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "unrecorded"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "fire_trail"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "landing_burst"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "bait"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "signal_path"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "tuned_lens"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "selective_hush"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "reinforced_needle"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "twin_needle"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "imprint_seal"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "charged_bolt"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "wanderers_gate"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "deep_hush"),
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "triumph"),
    };

    private static List<CompositionRecipe> RECIPES = builtins();

    private CompositionRecipes() {}

    public static List<CompositionRecipe> all() {
        return RECIPES;
    }

    public static Optional<CompositionRecipe> byIndex(int index) {
        if (index < 0 || index >= RECIPES.size()) {
            return Optional.empty();
        }
        return Optional.of(RECIPES.get(index));
    }

    public static Optional<CompositionRecipe> match(List<ImprintTag> tags) {
        List<ImprintTag> sorted = tags.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        for (CompositionRecipe recipe : RECIPES) {
            if (recipe.tags().equals(sorted)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static int indexOf(CompositionRecipe recipe) {
        return RECIPES.indexOf(recipe);
    }

    @SubscribeEvent
    public static void onReload(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "composition_formulas"), new Loader());
    }

    private static final class Loader extends SimplePreparableReloadListener<List<CompositionRecipe>> {
        @Override
        protected List<CompositionRecipe> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<Identifier, Resource> found =
                    manager.listResources("mnemolith/composition_formula", path -> path.getPath().endsWith(".json"));
            List<CompositionRecipe> loaded = new ArrayList<>();
            for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
                Identifier fileId = entry.getKey();
                try (var reader = entry.getValue().openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    String path = fileId.getPath();
                    String name = path.substring("mnemolith/composition_formula/".length(), path.length() - ".json".length());
                    Identifier id = Identifier.fromNamespaceAndPath(fileId.getNamespace(), name);
                    List<ImprintTag> tags = new ArrayList<>();
                    JsonArray arr = json.getAsJsonArray("tags");
                    for (JsonElement el : arr) {
                        tags.add(ImprintTag.CODEC.parse(JsonOps.INSTANCE, el).getOrThrow());
                    }
                    ImprintTag product = ImprintTag.CODEC.parse(JsonOps.INSTANCE, json.get("product")).getOrThrow();
                    Optional<Identifier> resultItem = Optional.empty();
                    if (json.has("result")) {
                        resultItem = Optional.of(Identifier.parse(json.get("result").getAsString()));
                    }
                    loaded.add(new CompositionRecipe(id, tags, product, resultItem));
                } catch (Exception ex) {
                    Mnemolith.LOGGER.error("Failed to load composition formula {}", fileId, ex);
                }
            }
            return order(loaded);
        }

        @Override
        protected void apply(List<CompositionRecipe> prepared, ResourceManager manager, ProfilerFiller profiler) {
            RECIPES = List.copyOf(prepared.isEmpty() ? builtins() : prepared);
            Mnemolith.LOGGER.info("Mnemolith loaded {} composition formulas", RECIPES.size());
        }
    }

    static List<CompositionRecipe> order(List<CompositionRecipe> loaded) {
        List<CompositionRecipe> ordered = new ArrayList<>();
        for (Identifier id : BUILTIN_ORDER) {
            loaded.stream().filter(r -> r.id().equals(id)).findFirst().ifPresent(ordered::add);
        }
        loaded.stream()
                .filter(r -> {
                    for (Identifier id : BUILTIN_ORDER) {
                        if (r.id().equals(id)) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing(r -> r.id().toString()))
                .forEach(ordered::add);
        return ordered.isEmpty() ? builtins() : ordered;
    }

    private static List<CompositionRecipe> builtins() {
        return List.of(
                new CompositionRecipe(BUILTIN_ORDER[0], List.of(ImprintTag.DEATH, ImprintTag.SILENCE), ImprintTag.EXPLOSION),
                new CompositionRecipe(BUILTIN_ORDER[1], List.of(ImprintTag.FIRE, ImprintTag.BUILD), ImprintTag.FIRE),
                new CompositionRecipe(BUILTIN_ORDER[2], List.of(ImprintTag.FALL, ImprintTag.PLAYER), ImprintTag.FALL),
                new CompositionRecipe(BUILTIN_ORDER[3], List.of(ImprintTag.SILENCE, ImprintTag.PLAYER), ImprintTag.SILENCE),
                new CompositionRecipe(BUILTIN_ORDER[4], List.of(ImprintTag.REDSTONE, ImprintTag.PATH, ImprintTag.BUILD), ImprintTag.PLAYER),
                new CompositionRecipe(BUILTIN_ORDER[5], List.of(ImprintTag.FIRE, ImprintTag.PATH), ImprintTag.FIRE,
                        Optional.of(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chronicle_lens"))),
                new CompositionRecipe(BUILTIN_ORDER[6], List.of(ImprintTag.DEATH, ImprintTag.PATH), ImprintTag.DEATH,
                        Optional.of(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "selective_mute_stone"))),
                new CompositionRecipe(BUILTIN_ORDER[7], List.of(ImprintTag.REDSTONE, ImprintTag.SILENCE), ImprintTag.SILENCE,
                        Optional.of(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "reinforced_needle"))),
                new CompositionRecipe(BUILTIN_ORDER[8], List.of(ImprintTag.FALL, ImprintTag.REDSTONE), ImprintTag.FALL,
                        Optional.of(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "twin_needle"))),
                new CompositionRecipe(BUILTIN_ORDER[9], List.of(ImprintTag.FALL, ImprintTag.BUILD), ImprintTag.BUILD,
                        Optional.of(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "imprint_seal"))),
                new CompositionRecipe(BUILTIN_ORDER[10], List.of(ImprintTag.LIGHTNING, ImprintTag.FIRE), ImprintTag.LIGHTNING),
                new CompositionRecipe(BUILTIN_ORDER[11], List.of(ImprintTag.PORTAL, ImprintTag.PATH), ImprintTag.PORTAL),
                new CompositionRecipe(BUILTIN_ORDER[12], List.of(ImprintTag.SCULK, ImprintTag.SILENCE), ImprintTag.SCULK),
                new CompositionRecipe(BUILTIN_ORDER[13], List.of(ImprintTag.BOSS, ImprintTag.DEATH), ImprintTag.DEATH));
    }
}
