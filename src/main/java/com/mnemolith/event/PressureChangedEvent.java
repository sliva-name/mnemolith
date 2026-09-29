package com.mnemolith.event;

import com.mnemolith.pressure.PressureBand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Fired on {@link NeoForge#EVENT_BUS} when a chunk's pressure score or fracture flag moves. */
public final class PressureChangedEvent extends Event {
    private final ServerLevel level;
    private final ChunkPos chunk;
    private final int previous;
    private final int next;
    private final PressureBand previousBand;
    private final PressureBand nextBand;
    private final boolean newlyFractured;

    public PressureChangedEvent(
            ServerLevel level,
            ChunkPos chunk,
            int previous,
            int next,
            PressureBand previousBand,
            PressureBand nextBand,
            boolean newlyFractured) {
        this.level = level;
        this.chunk = chunk;
        this.previous = previous;
        this.next = next;
        this.previousBand = previousBand;
        this.nextBand = nextBand;
        this.newlyFractured = newlyFractured;
    }

    public ServerLevel getLevel() { return level; }
    public ChunkPos getChunk() { return chunk; }
    public int getPrevious() { return previous; }
    public int getNext() { return next; }
    public PressureBand getPreviousBand() { return previousBand; }
    public PressureBand getNextBand() { return nextBand; }
    public boolean isNewlyFractured() { return newlyFractured; }
}
