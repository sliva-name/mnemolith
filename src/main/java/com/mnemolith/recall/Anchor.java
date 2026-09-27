package com.mnemolith.recall;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * One place this playthrough left behind. {@code gesture} is a {@link GestureKind} ordinal, or -1.
 * {@code pressure} is only meaningful for {@link AnchorKind#LOUD}. {@code yaw} shifts a distorted trace by one block.
 */
public record Anchor(
        AnchorKind kind,
        UUID owner,
        String dimension,
        BlockPos pos,
        long gameTime,
        int gesture,
        boolean distorted,
        int pressure,
        float yaw) {

    public static final Codec<Anchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AnchorKind.CODEC.fieldOf("kind").forGetter(Anchor::kind),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Anchor::owner),
            Codec.STRING.fieldOf("dimension").forGetter(Anchor::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Anchor::pos),
            Codec.LONG.fieldOf("time").forGetter(Anchor::gameTime),
            Codec.INT.optionalFieldOf("gesture", -1).forGetter(Anchor::gesture),
            Codec.BOOL.optionalFieldOf("distorted", false).forGetter(Anchor::distorted),
            Codec.INT.optionalFieldOf("pressure", 0).forGetter(Anchor::pressure),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Anchor::yaw)
    ).apply(instance, Anchor::new));

    public Anchor {
        pos = pos.immutable();
        dimension = dimension == null ? "" : dimension;
        if (gesture < -1) {
            gesture = -1;
        }
    }
}
