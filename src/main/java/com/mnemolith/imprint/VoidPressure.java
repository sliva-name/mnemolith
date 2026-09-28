package com.mnemolith.imprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Quiet pressure while mute stones hold a chunk (P3). Packed so ChunkMemory's codec stays under the group arity limit. */
public record VoidPressure(int pressure, boolean warned, long cooldownUntil) {
    public static final VoidPressure NONE = new VoidPressure(0, false, 0L);

    public static final Codec<VoidPressure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("pressure", 0).forGetter(VoidPressure::pressure),
            Codec.BOOL.optionalFieldOf("warned", false).forGetter(VoidPressure::warned),
            Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(VoidPressure::cooldownUntil)
    ).apply(instance, VoidPressure::of));

    public static VoidPressure of(int pressure, boolean warned, long cooldownUntil) {
        return new VoidPressure(Math.max(0, pressure), warned, Math.max(0L, cooldownUntil));
    }

    public boolean isEmpty() {
        return this.pressure == 0 && !this.warned && this.cooldownUntil == 0L;
    }

    public VoidPressure withPressure(int pressure) {
        return of(pressure, this.warned, this.cooldownUntil);
    }

    public VoidPressure withWarned(boolean warned) {
        return of(this.pressure, warned, this.cooldownUntil);
    }

    public VoidPressure withCooldownUntil(long cooldownUntil) {
        return of(this.pressure, this.warned, cooldownUntil);
    }
}
