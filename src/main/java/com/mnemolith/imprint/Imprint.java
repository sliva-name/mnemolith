package com.mnemolith.imprint;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * One server-authoritative memory stored on a chunk.
 * {@code rewritten} is optional in the codec and defaults to false, so a chunk saved before an intervention still loads.
 */
public record Imprint(ImprintTag tag, int intensity, BlockPos origin, Optional<UUID> player, int contextHash, long writtenAt, boolean rewritten) {
    public static final Codec<Imprint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ImprintTag.CODEC.fieldOf("tag").forGetter(Imprint::tag),
            Codec.INT.fieldOf("intensity").forGetter(Imprint::intensity),
            BlockPos.CODEC.fieldOf("origin").forGetter(Imprint::origin),
            UUIDUtil.CODEC.optionalFieldOf("player").forGetter(Imprint::player),
            Codec.INT.fieldOf("context_hash").forGetter(Imprint::contextHash),
            Codec.LONG.fieldOf("written_at").forGetter(Imprint::writtenAt),
            Codec.BOOL.optionalFieldOf("rewritten", false).forGetter(Imprint::rewritten)
    ).apply(instance, Imprint::of));

    /** A fresh write. Not an intervention. */
    public Imprint(ImprintTag tag, int intensity, BlockPos origin, Optional<UUID> player, int contextHash, long writtenAt) {
        this(tag, intensity, origin, player, contextHash, writtenAt, false);
    }

    public static Imprint of(ImprintTag tag, int intensity, BlockPos origin, Optional<UUID> player, int contextHash, long writtenAt, boolean rewritten) {
        return new Imprint(tag, intensity, origin, player, contextHash, writtenAt, rewritten);
    }

    /** Same strength and author, a different tag, and the intervention mark. */
    public Imprint rewritten(ImprintTag tag) {
        return new Imprint(tag, this.intensity, this.origin, this.player, contextHash(tag, this.origin, this.writtenAt), this.writtenAt, true);
    }

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
