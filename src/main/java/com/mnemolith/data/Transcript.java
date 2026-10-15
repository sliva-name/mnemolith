package com.mnemolith.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A written chronicle transcript: the tag and formula bits its author had discovered, and the author's name. */
public record Transcript(int tags, int formulas, String author) {
    public static final Codec<Transcript> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("tags").forGetter(Transcript::tags),
            Codec.INT.fieldOf("formulas").forGetter(Transcript::formulas),
            Codec.STRING.fieldOf("author").forGetter(Transcript::author)).apply(instance, Transcript::new));
    public static final StreamCodec<ByteBuf, Transcript> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Transcript::tags,
            ByteBufCodecs.VAR_INT, Transcript::formulas,
            ByteBufCodecs.STRING_UTF8, Transcript::author,
            Transcript::new);

    public int tagCount() {
        return Integer.bitCount(this.tags);
    }

    public int formulaCount() {
        return Integer.bitCount(this.formulas);
    }
}
