package com.mnemolith.echo.job;

/** Shared caps for the job loop. The numbers are the same ones the old single class used. */
final class JobLimits {
    static final int CANDIDATE_CAP = 256;
    static final int UNREACHABLE_GIVE_UP = 12;
    static final int WAIT_POLL = 40;
    /** Ticks between two placed blocks: the same pace as a player's right-click delay. */
    static final int PLACE_INTERVAL = 4;
    static final int STUCK_TICKS = 50;
    static final double CHEST_REACH = 4.0D;

    private JobLimits() {}
}
