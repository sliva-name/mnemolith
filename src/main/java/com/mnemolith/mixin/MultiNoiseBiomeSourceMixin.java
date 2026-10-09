package com.mnemolith.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mnemolith.worldgen.hollows.HollowRegions;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

/** Biome regions: a host biome inside a region's climate window becomes the region's biome. See HollowRegions. */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    @Inject(method = "getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Lnet/minecraft/core/Holder;",
            at = @At("RETURN"), cancellable = true)
    private void mnemolith$applyRegion(Climate.TargetPoint target, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> replaced = HollowRegions.replace(cir.getReturnValue(), target);
        if (replaced != null) {
            cir.setReturnValue(replaced);
        }
    }
}
