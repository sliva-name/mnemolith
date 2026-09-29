package com.mnemolith.imprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/** A selective mute stone: blocks imprint writes except for {@link #allowed}. */
public record SelectiveMuteMark(BlockPos pos, ImprintTag allowed) {
    public static final Codec<SelectiveMuteMark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(SelectiveMuteMark::pos),
            ImprintTag.CODEC.fieldOf("allowed").forGetter(SelectiveMuteMark::allowed)
    ).apply(instance, SelectiveMuteMark::new));
}
