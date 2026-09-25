package com.mnemolith.content;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.menu.CompositionMenu;
import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Mnemolith.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CompositionMenu>> COMPOSITION = MENUS.register(
            "composition_reel",
            () -> IMenuTypeExtension.create((containerId, inventory, buffer) -> new CompositionMenu(containerId, inventory, new SimpleContainer(ImprintConstants.COMPOSITION_SLOTS))));

    public static final DeferredHolder<MenuType<?>, MenuType<com.mnemolith.content.menu.EchoMenu>> ECHO = MENUS.register(
            "echo",
            () -> IMenuTypeExtension.create(com.mnemolith.content.menu.EchoMenu::client));

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
