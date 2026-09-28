package com.mnemolith.event;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.imprint.ImprintTag;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Fired on {@link NeoForge#EVENT_BUS} after chunk memory accepts one or more imprint writes. */
public final class ImprintWrittenEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final List<ImprintTag> tags;
    private final @Nullable UUID player;
    private final boolean throttled;

    public ImprintWrittenEvent(ServerLevel level, BlockPos pos, List<ImprintTag> tags, @Nullable UUID player, boolean throttled) {
        this.level = level;
        this.pos = pos.immutable();
        this.tags = List.copyOf(tags);
        this.player = player;
        this.throttled = throttled;
    }

    public ServerLevel getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public List<ImprintTag> getTags() { return tags; }
    public @Nullable UUID getPlayer() { return player; }
    public boolean isThrottled() { return throttled; }
}
