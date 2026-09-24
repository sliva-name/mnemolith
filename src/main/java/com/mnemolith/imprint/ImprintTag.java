package com.mnemolith.imprint;

import io.netty.buffer.ByteBuf;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** One memory tag. Weight is the pressure contribution per point of intensity. */
public enum ImprintTag implements StringRepresentable {
    FIRE("fire", 6),
    FALL("fall", 8),
    DEATH("death", 12),
    BUILD("build", 3),
    EXPLOSION("explosion", 10),
    SILENCE("silence", 5),
    PLAYER("player", 4),
    REDSTONE("redstone", 3);

    public static final Codec<ImprintTag> CODEC = StringRepresentable.fromEnum(ImprintTag::values);
    public static final StreamCodec<ByteBuf, ImprintTag> STREAM_CODEC = ByteBufCodecs.idMapper(ImprintTag::byOrdinal, ImprintTag::ordinal);

    private final String name;
    private final int weight;

    ImprintTag(String name, int weight) {
        this.name = name;
        this.weight = weight;
    }

    public int weight() {
        return this.weight;
    }

    public String translationKey() {
        return "mnemolith.tag." + this.name;
    }

    public static ImprintTag byOrdinal(int ordinal) {
        ImprintTag[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return FIRE;
        }
        return values[ordinal];
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
