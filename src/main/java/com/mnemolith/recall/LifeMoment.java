package com.mnemolith.recall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * One event of a player's life in this world, kept by {@link LifeMoments}.
 * {@code itemId} is what was in the main hand (or the build's most used block), {@code detail} is a registry id
 * (the enemy or the killer) or a damage message id, or empty. {@code gameTime} is when it first happened and stays
 * when the same place repeats; {@code count} grows instead. {@code lastNear} is the last game time the owner stood
 * within the scene radius; a scene needs a long enough absence since then. {@code replays} counts scenes shown.
 * {@code first} marks the first moment of its kind for this player, which the cap never drops.
 */
public record LifeMoment(
        LifeMomentKind kind,
        String dimension,
        BlockPos pos,
        float yaw,
        String itemId,
        String detail,
        long gameTime,
        long lastNear,
        int count,
        int replays,
        boolean first) {

    public static final Codec<LifeMoment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LifeMomentKind.CODEC.fieldOf("kind").forGetter(LifeMoment::kind),
            Codec.STRING.fieldOf("dimension").forGetter(LifeMoment::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(LifeMoment::pos),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(LifeMoment::yaw),
            Codec.STRING.optionalFieldOf("item", "").forGetter(LifeMoment::itemId),
            Codec.STRING.optionalFieldOf("detail", "").forGetter(LifeMoment::detail),
            Codec.LONG.fieldOf("time").forGetter(LifeMoment::gameTime),
            Codec.LONG.optionalFieldOf("last_near", 0L).forGetter(LifeMoment::lastNear),
            Codec.INT.optionalFieldOf("count", 1).forGetter(LifeMoment::count),
            Codec.INT.optionalFieldOf("replays", 0).forGetter(LifeMoment::replays),
            Codec.BOOL.optionalFieldOf("first", false).forGetter(LifeMoment::first)
    ).apply(instance, LifeMoment::new));

    public LifeMoment {
        pos = pos.immutable();
        dimension = dimension == null ? "" : dimension;
        itemId = itemId == null ? "" : itemId;
        detail = detail == null ? "" : detail;
        count = Math.max(1, count);
        replays = Math.max(0, replays);
    }

    /** A fresh moment: seen right now, never replayed. */
    public static LifeMoment fresh(LifeMomentKind kind, String dimension, BlockPos pos, float yaw, String itemId, String detail, long now) {
        return new LifeMoment(kind, dimension, pos, yaw, itemId, detail, now, now, 1, 0, false);
    }

    public LifeMoment withNear(long now) {
        return new LifeMoment(this.kind, this.dimension, this.pos, this.yaw, this.itemId, this.detail, this.gameTime, now, this.count, this.replays, this.first);
    }

    public LifeMoment replayed(long now) {
        return new LifeMoment(this.kind, this.dimension, this.pos, this.yaw, this.itemId, this.detail, this.gameTime, now, this.count, this.replays + 1, this.first);
    }

    /** The same place again: one more time, the newest hand and detail, seen now. The first time stays. */
    public LifeMoment repeated(LifeMoment again) {
        String item = again.itemId.isEmpty() ? this.itemId : again.itemId;
        String why = again.detail.isEmpty() ? this.detail : again.detail;
        return new LifeMoment(this.kind, this.dimension, this.pos, again.yaw, item, why, this.gameTime, Math.max(this.lastNear, again.lastNear), this.count + 1, this.replays, this.first);
    }

    LifeMoment markFirst() {
        return new LifeMoment(this.kind, this.dimension, this.pos, this.yaw, this.itemId, this.detail, this.gameTime, this.lastNear, this.count, this.replays, true);
    }

    public boolean sameSpot(LifeMoment other) {
        if (this.kind != other.kind || !this.dimension.equals(other.dimension)) {
            return false;
        }
        int radius = this.kind.mergeRadius();
        return this.pos.distSqr(other.pos) <= (double) radius * radius;
    }
}
