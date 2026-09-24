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
    public static final DeferredHolder<SoundEvent, SoundEvent> LENS_FOCUS = register("lens_focus");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESSURE_WARN = register("pressure_warn");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTE_BREAK = register("mute_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTE_PLACE = register("mute_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRATUM_BREAK = register("stratum_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRATUM_PLACE = register("stratum_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXTRACT = register("extract");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPOSE_SUCCESS = register("compose_success");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPOSE_FAIL = register("compose_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_AMBIENT = register("strider_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_HURT = register("strider_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_DEATH = register("strider_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_CHARGE = register("strider_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHIVIST_AMBIENT = register("archivist_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHIVIST_HURT = register("archivist_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHIVIST_DEATH = register("archivist_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHIVIST_STEAL = register("archivist_steal");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICANT_AMBIENT = register("replicant_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICANT_HURT = register("replicant_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICANT_DEATH = register("replicant_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICANT_TELEGRAPH = register("replicant_telegraph");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPLICANT_BLIND = register("replicant_blind");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
