package com.mnemolith.recall;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * What was done with one legendary offer. Open is still waiting. The others do not reopen that place.
 */
public enum Choice implements StringRepresentable {
    OPEN("open"),
    LEFT("left"),
    MUTED("muted"),
    TAKEN("taken"),
    STORED("stored");

    public static final Codec<Choice> CODEC = StringRepresentable.fromEnum(Choice::values);

    private final String name;

    Choice(String name) {
        this.name = name;
    }

    public boolean open() {
        return this == OPEN;
    }

    /** Taking, muting or storing spends the source kind. Leaving only refuses that spot. */
    public boolean spendsKind() {
        return this == MUTED || this == TAKEN || this == STORED;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
