package com.mnemolith.audio;

import com.mnemolith.Mnemolith;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound event registry. Sound events are common content: the dedicated server must know them
 * so it can tell clients to play them. Client playback stays in {@code com.mnemolith.client.audio}.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Mnemolith.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> IMPRINT_WRITE = register("imprint_write");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXTRACT = register("extract");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPOSE_SUCCESS = register("compose_success");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPOSE_FAIL = register("compose_fail");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
