package com.mnemolith.worldgen.hollows;

import com.mnemolith.Mnemolith;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Keys for the Memory Hollows biome. The biome itself, its features and its region are datapack files. */
public final class Hollows {
    public static final ResourceKey<Biome> MEMORY_HOLLOWS = ResourceKey.create(Registries.BIOME, id("memory_hollows"));
    /** Vanilla biomes a hollows patch may replace (see {@code memory_hollows_region.json}). */
    public static final TagKey<Biome> HOSTS = TagKey.create(Registries.BIOME, id("memory_hollows_hosts"));

    private Hollows() {}

    public static boolean is(Holder<Biome> biome) {
        return biome.is(MEMORY_HOLLOWS);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
