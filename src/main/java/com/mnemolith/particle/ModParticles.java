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
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMPRINT_EXTRACT = register("imprint_extract");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> COMPOSE_SUCCESS = register("compose_success");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> COMPOSE_FAIL = register("compose_fail");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PRESSURE_WARN = register("pressure_warn");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUTE_HAZE = register("mute_haze");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STRIDER_TRAIL = register("strider_trail");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ARCHIVIST_SNATCH = register("archivist_snatch");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REPLICANT_TELEGRAPH = register("replicant_telegraph");

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
