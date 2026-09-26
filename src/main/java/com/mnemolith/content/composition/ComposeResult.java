package com.mnemolith.content.composition;

/** What the server decided. The menu copies these ints into data slots. */
public record ComposeResult(int status, int formulaOrdinal) {
    public static final int IDLE = 0;
    public static final int SUCCESS = 1;
    public static final int FAIL = 2;
    public static final int EMPTY = 3;
    public static final int DISABLED = 4;
    /** The formula would hand over an item, and the inventory cannot take it. Nothing was spent. */
    public static final int FULL = 5;

    public boolean success() {
        return this.status == SUCCESS;
    }
}
