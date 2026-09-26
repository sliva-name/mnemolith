package com.mnemolith.echo;

/** What a weapon taught an echo. Saved by name so an unknown value from an old world becomes none. */
public enum EchoRole {
    NONE,
    /** Work leaves no imprint, and hostile mobs do not pick the echo. */
    SCOUT,
    /** Keeps working in a fracture and steps in when something threatens the owner. */
    WARDEN,
    /** Walks to a chorus mark, or to a nearby hostile, and strikes with the held item. */
    HUNTER,
    /** Picks up nearby drops while it is not on a job. */
    GATHERER;

    public boolean allowsFracture() {
        return this == WARDEN;
    }

    public static EchoRole byName(String name) {
        for (EchoRole role : values()) {
            if (role.name().equalsIgnoreCase(name)) {
                return role;
            }
        }
        return NONE;
    }
}
