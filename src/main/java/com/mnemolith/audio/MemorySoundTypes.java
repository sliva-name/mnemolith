package com.mnemolith.audio;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Block sounds that resolve mod events when they play.
 * Step, hit, and fall stay on the vanilla fallback. Break and place do not call
 * {@code DeferredHolder.get()} while block properties are built.
 */
public final class MemorySoundTypes {
    public static final SoundType MUTE = layered(SoundType.STONE, ModSounds.MUTE_BREAK, ModSounds.MUTE_PLACE);
    public static final SoundType STRATUM = layered(SoundType.DEEPSLATE, ModSounds.STRATUM_BREAK, ModSounds.STRATUM_PLACE);

    private MemorySoundTypes() {}

    private static SoundType layered(SoundType fallback, DeferredHolder<SoundEvent, SoundEvent> breakSound, DeferredHolder<SoundEvent, SoundEvent> placeSound) {
        return new SoundType(
                fallback.getVolume(),
                fallback.getPitch(),
                fallback.getBreakSound(),
                fallback.getStepSound(),
                fallback.getPlaceSound(),
                fallback.getHitSound(),
                fallback.getFallSound()) {
            @Override
            public SoundEvent getBreakSound() {
                return breakSound.get();
            }

            @Override
            public SoundEvent getPlaceSound() {
                return placeSound.get();
            }
        };
    }
}
