package com.mnemolith.echo;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;

/**
 * Guard lesson from a recording: the player landed melee hits on hostile mobs. The echo then holds a post and fights
 * hostile mobs near it (see {@code GuardController}).
 */
public record GuardLesson(List<EntityType<?>> foes, int hits, int kills) {
    public static final int MAX_FOES = 6;
    public static final GuardLesson NONE = new GuardLesson(List.of(), 0, 0);
    public static final Codec<GuardLesson> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf().optionalFieldOf("foes", List.of()).forGetter(GuardLesson::foes),
            Codec.INT.optionalFieldOf("hits", 0).forGetter(GuardLesson::hits),
            Codec.INT.optionalFieldOf("kills", 0).forGetter(GuardLesson::kills))
            .apply(instance, GuardLesson::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuardLesson> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ENTITY_TYPE).apply(ByteBufCodecs.list(MAX_FOES)), GuardLesson::foes,
            ByteBufCodecs.VAR_INT, GuardLesson::hits,
            ByteBufCodecs.VAR_INT, GuardLesson::kills,
            GuardLesson::new);

    public GuardLesson {
        foes = List.copyOf(foes.size() > MAX_FOES ? foes.subList(0, MAX_FOES) : foes);
    }

    /** At least two melee hits on hostile mobs. */
    public boolean teaches() {
        return this.hits >= 2;
    }

    /** "Zombie, Skeleton" (at most three names). */
    public Component describe() {
        MutableComponent out = Component.empty();
        int shown = 0;
        for (EntityType<?> type : this.foes) {
            if (shown == 3) {
                out.append(", …");
                break;
            }
            if (shown > 0) {
                out.append(", ");
            }
            out.append(type.getDescription());
            shown++;
        }
        if (shown == 0) {
            out.append(Component.translatable("mnemolith.guard.hostiles"));
        }
        return out;
    }
}
