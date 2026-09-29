package com.mnemolith.event;

import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.echo.EchoRecording;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Fired on {@link NeoForge#EVENT_BUS} after a player successfully activates an echo recording. */
public final class EchoActivatedEvent extends Event {
    private final ServerPlayer player;
    private final EchoEntity echo;
    private final EchoRecording recording;

    public EchoActivatedEvent(ServerPlayer player, EchoEntity echo, EchoRecording recording) {
        this.player = player;
        this.echo = echo;
        this.recording = recording;
    }

    public ServerPlayer getPlayer() { return player; }
    public EchoEntity getEcho() { return echo; }
    public EchoRecording getRecording() { return recording; }
}
