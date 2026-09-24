package com.mnemolith.audio;

import com.mnemolith.Mnemolith;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound event registry. Sound events are common content: the dedicated server must know them
 * so it can tell clients to play them. Client playback stays in {@code com.mnemolith.client.audio}.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Mnemolith.MOD_ID);

    private ModSounds() {}

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
