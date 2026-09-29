package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.world.PlayerMemorials;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Captures player death drops into a memorial when memorial.enabled is on (P4). */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class MemorialEvents {
    private MemorialEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDrops(LivingDropsEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PlayerMemorials.tryPlace(player, event.getDrops()) && event.getDrops().isEmpty()) {
            event.setCanceled(true);
        }
    }
}
