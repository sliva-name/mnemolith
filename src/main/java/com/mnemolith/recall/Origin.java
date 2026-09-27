package com.mnemolith.recall;

/**
 * What the raised lens is allowed to say about one place. {@code source} is {@link #GESTURE}, {@link #ANCHOR}
 * or {@link #IMPRINT}. {@code kind} is the ordinal of that source. {@code age} is a band, not a tick count.
 */
public record Origin(int source, int kind, int age, boolean distorted, boolean personal) {
    public static final int GESTURE = 0;
    public static final int ANCHOR = 1;
    public static final int IMPRINT = 2;

    /** Just now, not long ago, a while ago, long ago. */
    public static int ageBand(long age) {
        if (age < 6_000L) {
            return 0;
        }
        if (age < 36_000L) {
            return 1;
        }
        if (age < 216_000L) {
            return 2;
        }
        return 3;
    }
}
