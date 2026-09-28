package com.mnemolith.event;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Fired on {@link NeoForge#EVENT_BUS} after a composition attempt finishes (success or fail). */
public final class ComposeFinishedEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final @Nullable ServerPlayer player;
    private final int status;
    private final int formulaOrdinal;

    public ComposeFinishedEvent(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, int status, int formulaOrdinal) {
        this.level = level;
        this.pos = pos.immutable();
        this.player = player;
        this.status = status;
        this.formulaOrdinal = formulaOrdinal;
    }

    public ServerLevel getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public @Nullable ServerPlayer getPlayer() { return player; }
    /** Matches {@code Composition.ComposeResult} status codes. */
    public int getStatus() { return status; }
    public int getFormulaOrdinal() { return formulaOrdinal; }
    public boolean isSuccess() { return status == com.mnemolith.content.composition.ComposeResult.SUCCESS; }
}
