package com.mnemolith.imprint;

/**
 * Future home of imprints: tagged memories attached to a chunk when a world event writes history.
 * <p>
 * Phase 2 does not store imprints. Later phases write them from the event that created them
 * and keep the data on that chunk. See {@code docs/architecture.md}.
 */
public final class Imprints {
    private Imprints() {}
}
