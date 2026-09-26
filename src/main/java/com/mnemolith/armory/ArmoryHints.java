package com.mnemolith.armory;

import java.util.Map;

import net.minecraft.world.item.Item;

/** Tooltip translation keys for armory items. */
public final class ArmoryHints {
    private static final Map<Item, String> HINTS = Map.ofEntries(
            Map.entry(ArmoryItems.HUSH_FIBER.get(), "item.mnemolith.hush_fiber.hint"),
            Map.entry(ArmoryItems.GRAVE_SCALE.get(), "item.mnemolith.grave_scale.hint"),
            Map.entry(ArmoryItems.SCAR_SINEW.get(), "item.mnemolith.scar_sinew.hint"),
            Map.entry(ArmoryItems.MEMORY_BOLT.get(), "item.mnemolith.memory_bolt.hint"),
            Map.entry(ArmoryItems.HUSH_HELMET.get(), "item.mnemolith.hush_helmet.hint"),
            Map.entry(ArmoryItems.HUSH_CHESTPLATE.get(), "item.mnemolith.hush_helmet.hint"),
            Map.entry(ArmoryItems.HUSH_LEGGINGS.get(), "item.mnemolith.hush_helmet.hint"),
            Map.entry(ArmoryItems.HUSH_BOOTS.get(), "item.mnemolith.hush_helmet.hint"),
            Map.entry(ArmoryItems.GRAVE_HELMET.get(), "item.mnemolith.grave_helmet.hint"),
            Map.entry(ArmoryItems.GRAVE_CHESTPLATE.get(), "item.mnemolith.grave_helmet.hint"),
            Map.entry(ArmoryItems.GRAVE_LEGGINGS.get(), "item.mnemolith.grave_helmet.hint"),
            Map.entry(ArmoryItems.GRAVE_BOOTS.get(), "item.mnemolith.grave_helmet.hint"),
            Map.entry(ArmoryItems.ECHO_HELMET.get(), "item.mnemolith.echo_helmet.hint"),
            Map.entry(ArmoryItems.ECHO_CHESTPLATE.get(), "item.mnemolith.echo_helmet.hint"),
            Map.entry(ArmoryItems.ECHO_LEGGINGS.get(), "item.mnemolith.echo_helmet.hint"),
            Map.entry(ArmoryItems.ECHO_BOOTS.get(), "item.mnemolith.echo_helmet.hint"),
            Map.entry(ArmoryItems.SCAR_HELMET.get(), "item.mnemolith.scar_helmet.hint"),
            Map.entry(ArmoryItems.SCAR_CHESTPLATE.get(), "item.mnemolith.scar_helmet.hint"),
            Map.entry(ArmoryItems.SCAR_LEGGINGS.get(), "item.mnemolith.scar_helmet.hint"),
            Map.entry(ArmoryItems.SCAR_BOOTS.get(), "item.mnemolith.scar_helmet.hint"),
            Map.entry(ArmoryItems.RECALL_BLADE.get(), "item.mnemolith.recall_blade.hint"),
            Map.entry(ArmoryItems.GRAVE_MAUL.get(), "item.mnemolith.grave_maul.hint"),
            Map.entry(ArmoryItems.HUSH_SPEAR.get(), "item.mnemolith.hush_spear.hint"),
            Map.entry(ArmoryItems.CHORUS_SLING.get(), "item.mnemolith.chorus_sling.hint"),
            Map.entry(ArmoryItems.SCAR_BRAND.get(), "item.mnemolith.scar_brand.hint"));

    private ArmoryHints() {}

    public static String of(Item item) {
        return HINTS.get(item);
    }
}
