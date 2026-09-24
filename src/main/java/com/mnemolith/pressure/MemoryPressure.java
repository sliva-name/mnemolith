package com.mnemolith.pressure;

/**
 * Future home of memory pressure: a value derived from imprints in loaded chunks.
 * <p>
 * Crossing a threshold is what later starts a recollection storm. Phase 2 only stores the
 * threshold defaults in the common config. Pressure is not computed yet.
 */
public final class MemoryPressure {
    private MemoryPressure() {}
}
