package com.mnemolith.entity;

/** Synced pose id. Renderers read it; the server is the only writer. */
public final class MobActions {
    public static final int IDLE = 0;
    public static final int TELEGRAPH = 1;
    public static final int ATTACK = 2;
    public static final int FLEE = 3;
    public static final int PHASE = 4;

    private MobActions() {}
}
