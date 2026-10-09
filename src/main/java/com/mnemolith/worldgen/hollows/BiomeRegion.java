package com.mnemolith.worldgen.hollows;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Biome modifier type {@code mnemolith:biome_region}: where the multi-noise overworld may show {@link #biome()}
 * instead of one of its {@link #hosts()}. It does not change the biome it is applied to; applying it (NeoForge does
 * this on server start, before the per-step feature lists are rebuilt) registers the region with {@link HollowRegions}.
 * Each climate range defaults to the whole axis.
 */
public record BiomeRegion(Holder<Biome> biome, HolderSet<Biome> hosts, Climate.Parameter temperature, Climate.Parameter humidity,
        Climate.Parameter continentalness, Climate.Parameter erosion, Climate.Parameter weirdness) implements BiomeModifier {
    private static final Climate.Parameter ANY = Climate.Parameter.span(-2.0F, 2.0F);

    public static final MapCodec<BiomeRegion> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.fieldOf("biome").forGetter(BiomeRegion::biome),
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("hosts").forGetter(BiomeRegion::hosts),
            Climate.Parameter.CODEC.optionalFieldOf("temperature", ANY).forGetter(BiomeRegion::temperature),
            Climate.Parameter.CODEC.optionalFieldOf("humidity", ANY).forGetter(BiomeRegion::humidity),
            Climate.Parameter.CODEC.optionalFieldOf("continentalness", ANY).forGetter(BiomeRegion::continentalness),
            Climate.Parameter.CODEC.optionalFieldOf("erosion", ANY).forGetter(BiomeRegion::erosion),
            Climate.Parameter.CODEC.optionalFieldOf("weirdness", ANY).forGetter(BiomeRegion::weirdness)
    ).apply(instance, BiomeRegion::new));

    @Override
    public void modify(Holder<Biome> target, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.BEFORE_EVERYTHING) {
            HollowRegions.register(this);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return HollowRegions.REGION_CODEC.get();
    }

    /** Whether a climate sample lies inside every range. A few long comparisons, cheapest test first. */
    public boolean inside(Climate.TargetPoint point) {
        return within(this.weirdness, point.weirdness())
                && within(this.erosion, point.erosion())
                && within(this.humidity, point.humidity())
                && within(this.temperature, point.temperature())
                && within(this.continentalness, point.continentalness());
    }

    private static boolean within(Climate.Parameter range, long value) {
        return value >= range.min() && value <= range.max();
    }
}
