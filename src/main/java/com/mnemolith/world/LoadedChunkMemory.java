package com.mnemolith.world;

/**
 * Future tracker for imprint state on loaded chunks.
 * <p>
 * Updates belong on chunk load, chunk unload, and the world event that wrote an imprint.
 * A later tick, if one is needed, may drain a bounded queue of dirty chunks. It must not
 * walk every loaded chunk, and it must not scan the dimension.
 */
public final class LoadedChunkMemory {
    private LoadedChunkMemory() {}
}
