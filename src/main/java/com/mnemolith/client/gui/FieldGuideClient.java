package com.mnemolith.client.gui;

import com.mnemolith.content.ModItems;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Opens the field guide on the physical client. Registered from {@code MnemolithClient}. */
public final class FieldGuideClient {
    private FieldGuideClient() {}

    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || !event.getItemStack().is(ModItems.FIELD_GUIDE.get())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.gui.screen() instanceof FieldGuideScreen) {
                return;
            }
            minecraft.setScreenAndShow(new FieldGuideScreen());
        });
    }
}
