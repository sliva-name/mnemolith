package com.mnemolith.worldgen.hollows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableSet;
import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The active biome regions of the running server. Filled while NeoForge applies biome modifiers on server start,
 * cleared when the server stops. Read from worldgen threads by the biome source mixins, so the list is replaced as
 * a whole (copy on write) and never mutated in place.
 */
public final class HollowRegions {
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Mnemolith.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<BiomeRegion>> REGION_CODEC =
            SERIALIZERS.register("biome_region", () -> BiomeRegion.CODEC);

    private static volatile List<BiomeRegion> active = List.of();
    /** Bumped on every change, so cached possible-biome sets know to rebuild. 0 means no region ever. */
    private static volatile int version;

    private HollowRegions() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }

    static synchronized void register(BiomeRegion region) {
        if (active.contains(region)) {
            return;
        }
        if (!CommonConfig.MEMORY_HOLLOWS_ENABLED.get()) {
            Mnemolith.LOGGER.info("Mnemolith biome region for {} skipped: memoryHollowsEnabled is off", region.biome().getRegisteredName());
            return;
        }
        List<BiomeRegion> next = new ArrayList<>(active);
        next.add(region);
        active = List.copyOf(next);
        version++;
        Mnemolith.LOGGER.info("Mnemolith biome region active: {} over {} host biomes", region.biome().getRegisteredName(), region.hosts().size());
    }

    public static synchronized void clear() {
        if (!active.isEmpty()) {
            active = List.of();
            version++;
        }
    }

    public static List<BiomeRegion> active() {
        return active;
    }

    public static int version() {
        return version;
    }

    /** The region's biome when {@code picked} is one of its hosts and the climate point is inside it, else null. */
    public static @Nullable Holder<Biome> replace(Holder<Biome> picked, Climate.TargetPoint point) {
        List<BiomeRegion> regions = active;
        for (int i = 0, n = regions.size(); i < n; i++) {
            BiomeRegion region = regions.get(i);
            if (region.inside(point) && region.hosts().contains(picked)) {
                return region.biome();
            }
        }
        return null;
    }

    /** {@code base} plus every region biome whose hosts {@code base} can produce; {@code base} itself if none. */
    public static Set<Holder<Biome>> augment(Set<Holder<Biome>> base) {
        List<BiomeRegion> regions = active;
        ImmutableSet.Builder<Holder<Biome>> extra = null;
        for (BiomeRegion region : regions) {
            if (base.contains(region.biome())) {
                continue;
            }
            boolean hosted = false;
            for (Holder<Biome> host : region.hosts()) {
                if (base.contains(host)) {
                    hosted = true;
                    break;
                }
            }
            if (hosted) {
                if (extra == null) {
                    extra = ImmutableSet.<Holder<Biome>>builder().addAll(base);
                }
                extra.add(region.biome());
            }
        }
        return extra == null ? base : extra.build();
    }
}
