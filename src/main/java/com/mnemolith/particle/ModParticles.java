package com.mnemolith.particle;

import com.mnemolith.Mnemolith;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom particle types. Registered on both sides so a dedicated server can name them
 * in the vanilla level-particles packet. Providers stay in {@code ClientParticles}.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, Mnemolith.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMPRINT_SHIMMER = register("imprint_shimmer");
    /** Memory feel: one family per event kind (see {@code MemoryFeel} and {@code docs/design/memory-feel.md}). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MEMORY_WRITTEN = register("memory_written");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MEMORY_ERASED = register("memory_erased");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MEMORY_KEPT = register("memory_kept");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MEMORY_CAUGHT = register("memory_caught");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MEMORY_FORGOTTEN = register("memory_forgotten");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMPRINT_EXTRACT = register("imprint_extract");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> COMPOSE_SUCCESS = register("compose_success");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> COMPOSE_FAIL = register("compose_fail");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PRESSURE_WARN = register("pressure_warn");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUTE_HAZE = register("mute_haze");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STRIDER_TRAIL = register("strider_trail");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ARCHIVIST_SNATCH = register("archivist_snatch");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REPLICANT_TELEGRAPH = register("replicant_telegraph");
    // Memory grafts: one mote per temper, same sprite, tinted by the client provider.
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAFT_HUSHED = register("graft_hushed");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAFT_GRAVE = register("graft_grave");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAFT_KINDLED = register("graft_kindled");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAFT_PLUNGING = register("graft_plunging");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAFT_VOLATILE = register("graft_volatile");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAFT_CHARGED = register("graft_charged");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAFT_WANDERING = register("graft_wandering");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAFT_DEEP = register("graft_deep");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAFT_FORGOTTEN = register("graft_forgotten");

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
