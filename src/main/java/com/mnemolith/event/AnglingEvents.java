package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.fishing.Angling;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

/** Undertow: a reeled catch may bring up a memory (see {@link Angling}). Runs after other mods could cancel it. */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class AnglingEvents {
    private AnglingEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFished(ItemFishedEvent event) {
        if (event.isCanceled() || !(event.getHookEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Angling.onCatch(level, event.getHookEntity(), event.getEntity());
    }
}
