package com.mnemolith.client.echo;

import com.mnemolith.client.gui.GuiArt;
import com.mnemolith.content.menu.EchoMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The echo's own inventory, in the archival panel style. */
public class EchoInventoryScreen extends AbstractContainerScreen<EchoMenu> {
    public EchoInventoryScreen(EchoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, EchoMenu.WIDTH, EchoMenu.HEIGHT);
        this.inventoryLabelX = EchoMenu.MAIN_X;
        this.inventoryLabelY = EchoMenu.PLAYER_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = EchoMenu.MAIN_X;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiArt.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            GuiArt.slot(graphics, slot.x - 1, slot.y - 1);
        }
        GuiArt.label(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, GuiArt.BONE);
        GuiArt.label(graphics, this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiArt.BONE);
    }
}
