package com.mnemolith.entity.ai;

/** Actions a moment replicant is allowed to copy. Anything else is ignored. */
public enum CopiedActionKind {
    MELEE("melee"),
    JUMP("jump"),
    PLACE("place"),
    USE("use");

    private final String name;

    CopiedActionKind(String name) {
        this.name = name;
    }

    public String serialized() {
        return this.name;
    }
}
