package com.mnemolith.particle;

import com.mnemolith.imprint.ImprintTag;

/**
 * After-echoes: what an after-image does and faintly sounds like, by the kind of memory it stands for.
 * The server stamps the tag ordinal into the particle's x-velocity (a count-0 particle packet carries its
 * velocity exactly), so the client can pick motion and sound with no new payload and no protocol change.
 */
public final class AfterEcho {
    public enum Motion { COLUMN, RIPPLE, SINK, GROUND, OUTLINE }

    private AfterEcho() {}

    public static Motion motionFor(ImprintTag tag) {
        return switch (tag) {
            case EXPLOSION, BOSS, PORTAL -> Motion.RIPPLE;
            case DEATH, FALL, FORGOTTEN -> Motion.SINK;
            case PATH, PLAYER, SCULK, SILENCE -> Motion.GROUND;
            case BUILD, REDSTONE -> Motion.OUTLINE;
            case FIRE, LIGHTNING, TRADE -> Motion.COLUMN;
        };
    }

    /** The code stamped into the particle velocity; the client decodes it back with {@link ImprintTag#byOrdinal}. */
    public static float code(ImprintTag tag) {
        return tag.ordinal();
    }

    public static ImprintTag tagFor(float code) {
        return ImprintTag.byOrdinal(Math.round(code));
    }

    public static String soundName(ImprintTag tag) {
        return "memory." + tag.getSerializedName();
    }
}
