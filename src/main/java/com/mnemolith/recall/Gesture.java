package com.mnemolith.recall;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * One saved gesture. {@code itemId}, {@code blockId} and {@code targetId} are registry ids or empty.
 * {@code face} is a {@link net.minecraft.core.Direction} ordinal, or -1. {@code trail} is a short path
 * ending near {@code pos}, oldest first, at most eight points. {@code distorted} is level C: the local
 * flash nudges it; a replicant still plays the yaw as stored.
 */
public record Gesture(
        GestureKind kind,
        String dimension,
        BlockPos pos,
        float yaw,
        float pitch,
        String itemId,
        String blockId,
        int face,
        String targetId,
        long gameTime,
        boolean distorted,
        List<BlockPos> trail) {

    public static final int TRAIL_CAP = 8;

    public static final Codec<Gesture> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GestureKind.CODEC.fieldOf("kind").forGetter(Gesture::kind),
            Codec.STRING.fieldOf("dimension").forGetter(Gesture::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Gesture::pos),
            Codec.FLOAT.fieldOf("yaw").forGetter(Gesture::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(Gesture::pitch),
            Codec.STRING.optionalFieldOf("item", "").forGetter(Gesture::itemId),
            Codec.STRING.optionalFieldOf("block", "").forGetter(Gesture::blockId),
            Codec.INT.optionalFieldOf("face", -1).forGetter(Gesture::face),
            Codec.STRING.optionalFieldOf("target", "").forGetter(Gesture::targetId),
            Codec.LONG.fieldOf("time").forGetter(Gesture::gameTime),
            Codec.BOOL.optionalFieldOf("distorted", false).forGetter(Gesture::distorted),
            BlockPos.CODEC.listOf().optionalFieldOf("trail", List.of()).forGetter(Gesture::trail)
    ).apply(instance, Gesture::new));

    public Gesture {
        pos = pos.immutable();
        itemId = itemId == null ? "" : itemId;
        blockId = blockId == null ? "" : blockId;
        targetId = targetId == null ? "" : targetId;
        dimension = dimension == null ? "" : dimension;
        if (face < -1 || face > 5) {
            face = -1;
        }
        if (trail.size() > TRAIL_CAP) {
            trail = List.copyOf(trail.subList(trail.size() - TRAIL_CAP, trail.size()));
        } else {
            List<BlockPos> frozen = new ArrayList<>(trail.size());
            for (BlockPos step : trail) {
                frozen.add(step.immutable());
            }
            trail = List.copyOf(frozen);
        }
    }
}
