package com.mnemolith.echo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code echo_possession} player attachment. While a player is inside an echo, this holds everything that belongs
 * to the real body: items, health, food, effects, experience, and where the shell stands. It is saved with the
 * player file, so a disconnect, an unloaded shell, or a crash cannot separate the real items from their owner.
 * Empty when the player is not possessing; empty state is not written.
 */
public final class PossessionState {
    public record Real(List<SlotStack> items, int selected, float health, int food, float saturation, List<MobEffectInstance> effects,
            int xpLevel, float xpProgress, int xpTotal) {
        static final Codec<Real> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SlotStack.LIST_CODEC.optionalFieldOf("items", List.of()).forGetter(Real::items),
                Codec.INT.optionalFieldOf("selected", 0).forGetter(Real::selected),
                Codec.FLOAT.fieldOf("health").forGetter(Real::health),
                Codec.INT.optionalFieldOf("food", 20).forGetter(Real::food),
                Codec.FLOAT.optionalFieldOf("saturation", 5.0F).forGetter(Real::saturation),
                MobEffectInstance.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Real::effects),
                Codec.INT.optionalFieldOf("xp_level", 0).forGetter(Real::xpLevel),
                Codec.FLOAT.optionalFieldOf("xp_progress", 0.0F).forGetter(Real::xpProgress),
                Codec.INT.optionalFieldOf("xp_total", 0).forGetter(Real::xpTotal))
                .apply(instance, Real::new));

        public Real withHealth(float value) {
            return new Real(this.items, this.selected, value, this.food, this.saturation, this.effects, this.xpLevel, this.xpProgress, this.xpTotal);
        }
    }

    public record Anchor(ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot, UUID shell) {
        static final Codec<Anchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Anchor::dimension),
                Vec3.CODEC.fieldOf("pos").forGetter(Anchor::pos),
                Codec.FLOAT.optionalFieldOf("y_rot", 0.0F).forGetter(Anchor::yRot),
                Codec.FLOAT.optionalFieldOf("x_rot", 0.0F).forGetter(Anchor::xRot),
                UUIDUtil.CODEC.fieldOf("shell").forGetter(Anchor::shell))
                .apply(instance, Anchor::new));
    }

    public record Body(UUID echo, float maxHealth, Optional<EchoRecording> recording) {
        static final Codec<Body> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("echo").forGetter(Body::echo),
                Codec.FLOAT.optionalFieldOf("max_health", 20.0F).forGetter(Body::maxHealth),
                EchoRecording.CODEC.optionalFieldOf("recording").forGetter(Body::recording))
                .apply(instance, Body::new));
    }

    public record Data(Real real, Anchor anchor, Body body) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Real.CODEC.fieldOf("real").forGetter(Data::real),
                Anchor.CODEC.fieldOf("anchor").forGetter(Data::anchor),
                Body.CODEC.fieldOf("body").forGetter(Data::body))
                .apply(instance, Data::new));
    }

    public static final MapCodec<PossessionState> MAP_CODEC = Data.CODEC.optionalFieldOf("possession")
            .xmap(data -> new PossessionState(data.orElse(null)), state -> Optional.ofNullable(state.data));

    private @Nullable Data data;

    public PossessionState() {}

    public PossessionState(@Nullable Data data) {
        this.data = data;
    }

    public boolean isActive() {
        return this.data != null;
    }

    public @Nullable Data data() {
        return this.data;
    }

    public void setData(@Nullable Data data) {
        this.data = data;
    }
}
