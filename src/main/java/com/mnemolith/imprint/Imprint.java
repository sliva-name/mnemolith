package com.mnemolith.imprint;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/** One server-authoritative memory stored on a chunk. */
public record Imprint(ImprintTag tag, int intensity, BlockPos origin, Optional<UUID> player, int contextHash, long writtenAt) {
    public static final Codec<Imprint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ImprintTag.CODEC.fieldOf("tag").forGetter(Imprint::tag),
            Codec.INT.fieldOf("intensity").forGetter(Imprint::intensity),
            BlockPos.CODEC.fieldOf("origin").forGetter(Imprint::origin),
            UUIDUtil.CODEC.optionalFieldOf("player").forGetter(Imprint::player),
            Codec.INT.fieldOf("context_hash").forGetter(Imprint::contextHash),
            Codec.LONG.fieldOf("written_at").forGetter(Imprint::writtenAt)
    ).apply(instance, Imprint::new));

    public static int contextHash(ImprintTag tag, BlockPos pos, long writtenAt) {
        int hash = tag.ordinal();
        hash = 31 * hash + pos.getX();
        hash = 31 * hash + pos.getY();
        hash = 31 * hash + pos.getZ();
        hash = 31 * hash + Long.hashCode(writtenAt);
        return hash;
    }

    public int pressureContribution() {
        return this.intensity * this.tag.weight();
    }
}
