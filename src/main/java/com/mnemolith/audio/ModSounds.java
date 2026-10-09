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
    public static final DeferredHolder<SoundEvent, SoundEvent> MITE_AMBIENT = register("mite_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MITE_HURT = register("mite_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WITNESS_AMBIENT = register("witness_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> WITNESS_HURT = register("witness_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_AMBIENT = register("stalker_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_HURT = register("stalker_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_DEATH = register("stalker_death");
    /** The chair's meme clip. Streamed; it is about nineteen seconds. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAIR_VOICE = register("chair_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_STEP = register("echo_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_HURT = register("echo_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_DEATH = register("echo_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_POSSESS = register("echo_possess");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_WAKE = register("echo_wake");

    public static final DeferredHolder<SoundEvent, SoundEvent> SCAR_AMBIENT = register("scar_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAR_HURT = register("scar_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAR_DEATH = register("scar_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAR_CAST = register("scar_cast");

    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_GATHER = register("storm_gather");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_WAVE = register("storm_wave");

    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY_TIE = register("relay_tie");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY_UNTIE = register("relay_untie");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY_HOP = register("relay_hop");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY_BREAK = register("relay_break");

    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_CHIME = register("vault_chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_DRAW = register("vault_draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_RUPTURE = register("vault_rupture");

    /** Situational / jukebox music (streamed). */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_STORM_GATHERING = register("music_storm_gathering");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SCAR_FIGHT = register("music_scar_fight");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DISC_RECOLLECTION = register("music_disc_recollection");

    /** Memory Hollows (stage 2): biome music and ambience (worldgen/biome/memory_hollows.json), and a caught flicker. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MEMORY_HOLLOWS = register("music.memory_hollows");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_HOLLOWS_ADDITIONS = register("ambient.memory_hollows.additions");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_HOLLOWS_MOOD = register("ambient.memory_hollows.mood");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLICKER_CATCH = register("flicker_catch");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
