package com.mnemolith.event;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * Fired on {@link NeoForge#EVENT_BUS} when a hunter is about to keep or take a target that has Unrecorded.
 * Mnemolith posts this after other {@link LivingChangeTargetEvent} listeners, including ones that already
 * canceled or rewrote that event, and before it clears the target.
 * <p>
 * Cancel this event to leave the target alone. An addon that needs to override Unrecorded has to listen here.
 * Listening only to {@link LivingChangeTargetEvent} does not win: Mnemolith still clears unless this event is canceled.
 */
public final class UnrecordedTargetEvent extends Event implements ICancellableEvent {
    private final LivingEntity hunter;
    private final LivingEntity unrecorded;

    public UnrecordedTargetEvent(LivingEntity hunter, LivingEntity unrecorded) {
        this.hunter = hunter;
        this.unrecorded = unrecorded;
    }

    /** The entity whose target is being decided. */
    public LivingEntity getHunter() {
        return this.hunter;
    }

    /** The entity that has Unrecorded and would otherwise stay or become the target. */
    public LivingEntity getUnrecorded() {
        return this.unrecorded;
    }
}
