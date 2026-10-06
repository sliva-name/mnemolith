package com.mnemolith.recall;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * A large, recognizable event of one player's life in this world. Order is the payload id; do not reorder.
 * {@code mergeRadius} is how close a repeat must be to count as the same place. {@code cap} is how many of
 * this kind one player keeps.
 */
public enum LifeMomentKind implements StringRepresentable {
    DEATH("death", 4, 8),
    HOME("home", 6, 4),
    BUILD("build", 24, 6),
    BATTLE("battle", 8, 6);

    public static final Codec<LifeMomentKind> CODEC = StringRepresentable.fromEnum(LifeMomentKind::values);

    private final String name;
    private final int mergeRadius;
    private final int cap;

    LifeMomentKind(String name, int mergeRadius, int cap) {
        this.name = name;
        this.mergeRadius = mergeRadius;
        this.cap = cap;
    }

    public int mergeRadius() {
        return this.mergeRadius;
    }

    public int cap() {
        return this.cap;
    }

    public static LifeMomentKind byOrdinal(int ordinal) {
        LifeMomentKind[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DEATH;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
