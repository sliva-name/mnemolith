package com.mnemolith.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mnemolith.worldgen.hollows.HollowRegions;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

/**
 * Biome regions: a multi-noise source that can produce a region's host also lists the region's biome, so its
 * features are indexed and structures and {@code /locate} see it. The vanilla set is memoized before the server
 * applies biome modifiers, so the addition is made here, cached per source and region version.
 */
@Mixin(BiomeSource.class)
public abstract class BiomeSourceMixin {
    @Unique
    private volatile Object[] mnemolith$possible;

    @Inject(method = "possibleBiomes", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void mnemolith$addRegionBiomes(CallbackInfoReturnable<Set<Holder<Biome>>> cir) {
        if (!((Object) this instanceof MultiNoiseBiomeSource)) {
            return;
        }
        int version = HollowRegions.version();
        if (version == 0) {
            return;
        }
        Object[] cached = this.mnemolith$possible;
        if (cached == null || (Integer) cached[0] != version) {
            cached = new Object[] {version, HollowRegions.augment(cir.getReturnValue())};
            this.mnemolith$possible = cached;
        }
        cir.setReturnValue((Set<Holder<Biome>>) cached[1]);
    }
}
