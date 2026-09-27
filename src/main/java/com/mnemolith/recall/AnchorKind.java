package com.mnemolith.recall;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * One kind of trace this playthrough can leave. Order is the catalog bit and the packet id; do not reorder.
 * A sticky kind is kept once per player and is not aged out. Fracture, a gesture trace and a recall trace are a ring.
 */
public enum AnchorKind implements StringRepresentable {
    DEATH("death", true, false),
    MUTE("mute", true, false),
    BLANK("blank", true, false),
    ECHO("echo", true, false),
    FRACTURE("fracture", false, true),
    LOUD("loud", true, true),
    FLASH("flash", false, false),
    RECALL("recall", false, false);

    public static final Codec<AnchorKind> CODEC = StringRepresentable.fromEnum(AnchorKind::values);

    private final String name;
    /** Kept when the ring overflows, and not dropped for age. Loud is replaced only by a louder chunk. */
    private final boolean sticky;
    /** Read as the place itself. The others are read as someone's act. */
    private final boolean place;

    AnchorKind(String name, boolean sticky, boolean place) {
        this.name = name;
        this.sticky = sticky;
        this.place = place;
    }

    public boolean sticky() {
        return this.sticky;
    }

    public boolean place() {
        return this.place;
    }

    public static AnchorKind byOrdinal(int ordinal) {
        AnchorKind[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DEATH;
        }
        return values[ordinal];
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
