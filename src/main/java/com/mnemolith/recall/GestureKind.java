package com.mnemolith.recall;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** One recognizable thing a player did. Order is the packet id; do not reorder. */
public enum GestureKind implements StringRepresentable {
    ATTACK("attack"),
    PLACE("place"),
    USE("use"),
    FALL("fall");

    public static final Codec<GestureKind> CODEC = StringRepresentable.fromEnum(GestureKind::values);

    private final String name;

    GestureKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
