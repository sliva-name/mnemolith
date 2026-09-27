package com.mnemolith.recall;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * One offer condensed from this play. {@code pos} is the true place. {@code guide} is where the silhouette walks;
 * on a lie it is not {@code pos}. {@code tag} is an {@link com.mnemolith.imprint.ImprintTag} ordinal.
 * {@code voice} is a {@link GestureKind} ordinal, or -1 when nothing was recorded.
 */
public record Legend(
        UUID owner,
        String dimension,
        BlockPos pos,
        BlockPos guide,
        AnchorKind source,
        int tag,
        int voice,
        boolean lie,
        Choice choice,
        boolean witnessed,
        boolean struck,
        long gameTime) {

    public static final Codec<Legend> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Legend::owner),
            Codec.STRING.fieldOf("dimension").forGetter(Legend::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Legend::pos),
            BlockPos.CODEC.fieldOf("guide").forGetter(Legend::guide),
            AnchorKind.CODEC.fieldOf("source").forGetter(Legend::source),
            Codec.INT.fieldOf("tag").forGetter(Legend::tag),
            Codec.INT.optionalFieldOf("voice", -1).forGetter(Legend::voice),
            Codec.BOOL.optionalFieldOf("lie", false).forGetter(Legend::lie),
            Choice.CODEC.optionalFieldOf("choice", Choice.OPEN).forGetter(Legend::choice),
            Codec.BOOL.optionalFieldOf("witnessed", false).forGetter(Legend::witnessed),
            Codec.BOOL.optionalFieldOf("struck", false).forGetter(Legend::struck),
            Codec.LONG.fieldOf("time").forGetter(Legend::gameTime)
    ).apply(instance, Legend::new));

    public Legend {
        pos = pos.immutable();
        guide = guide.immutable();
        dimension = dimension == null ? "" : dimension;
    }

    public Legend withChoice(Choice choice) {
        return new Legend(this.owner, this.dimension, this.pos, this.guide, this.source, this.tag, this.voice, this.lie, choice, this.witnessed, this.struck, this.gameTime);
    }

    public Legend withWitnessed() {
        return new Legend(this.owner, this.dimension, this.pos, this.guide, this.source, this.tag, this.voice, this.lie, this.choice, true, this.struck, this.gameTime);
    }

    public Legend withStruck() {
        return new Legend(this.owner, this.dimension, this.pos, this.guide, this.source, this.tag, this.voice, this.lie, this.choice, this.witnessed, true, this.gameTime);
    }
}
