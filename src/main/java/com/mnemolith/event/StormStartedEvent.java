package com.mnemolith.event;

import com.mnemolith.echo.storm.RecollectionStorm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Fired on {@link NeoForge#EVENT_BUS} when a recollection storm begins. */
public final class StormStartedEvent extends Event {
    private final ServerLevel level;
    private final ChunkPos chunk;
    private final String cause;
    private final RecollectionStorm storm;

    public StormStartedEvent(ServerLevel level, ChunkPos chunk, String cause, RecollectionStorm storm) {
        this.level = level;
        this.chunk = chunk;
        this.cause = cause;
        this.storm = storm;
    }

    public ServerLevel getLevel() { return level; }
    public ChunkPos getChunk() { return chunk; }
    public String getCause() { return cause; }
    public RecollectionStorm getStorm() { return storm; }
}
