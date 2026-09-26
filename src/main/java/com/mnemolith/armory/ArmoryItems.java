package com.mnemolith.armory;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.ModEntities;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Armor, weapons, and the materials they are made from. */
public final class ArmoryItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mnemolith.MOD_ID);

    public static final DeferredItem<Item> HUSH_FIBER = ITEMS.registerItem("hush_fiber", Item::new, properties -> properties.stacksTo(64));
    public static final DeferredItem<Item> GRAVE_SCALE = ITEMS.registerItem("grave_scale", Item::new, properties -> properties.stacksTo(64));
    public static final DeferredItem<Item> SCAR_SINEW = ITEMS.registerItem("scar_sinew", Item::new, properties -> properties.stacksTo(64));
    public static final DeferredItem<Item> MEMORY_BOLT = ITEMS.registerItem("memory_bolt", Item::new, properties -> properties.stacksTo(16));

    public static final DeferredItem<Item> HUSH_HELMET = armor("hush_helmet", ArmoryMaterials.HUSH, ArmorType.HELMET);
    public static final DeferredItem<Item> HUSH_CHESTPLATE = armor("hush_chestplate", ArmoryMaterials.HUSH, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> HUSH_LEGGINGS = armor("hush_leggings", ArmoryMaterials.HUSH, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> HUSH_BOOTS = armor("hush_boots", ArmoryMaterials.HUSH, ArmorType.BOOTS);

    public static final DeferredItem<Item> GRAVE_HELMET = armor("grave_helmet", ArmoryMaterials.GRAVE, ArmorType.HELMET);
    public static final DeferredItem<Item> GRAVE_CHESTPLATE = armor("grave_chestplate", ArmoryMaterials.GRAVE, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> GRAVE_LEGGINGS = armor("grave_leggings", ArmoryMaterials.GRAVE, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> GRAVE_BOOTS = armor("grave_boots", ArmoryMaterials.GRAVE, ArmorType.BOOTS);

    public static final DeferredItem<Item> ECHO_HELMET = armor("echo_helmet", ArmoryMaterials.ECHO, ArmorType.HELMET);
    public static final DeferredItem<Item> ECHO_CHESTPLATE = armor("echo_chestplate", ArmoryMaterials.ECHO, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> ECHO_LEGGINGS = armor("echo_leggings", ArmoryMaterials.ECHO, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> ECHO_BOOTS = armor("echo_boots", ArmoryMaterials.ECHO, ArmorType.BOOTS);

    public static final DeferredItem<Item> SCAR_HELMET = armor("scar_helmet", ArmoryMaterials.SCAR, ArmorType.HELMET);
    public static final DeferredItem<Item> SCAR_CHESTPLATE = armor("scar_chestplate", ArmoryMaterials.SCAR, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> SCAR_LEGGINGS = armor("scar_leggings", ArmoryMaterials.SCAR, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> SCAR_BOOTS = armor("scar_boots", ArmoryMaterials.SCAR, ArmorType.BOOTS);

    public static final DeferredItem<RecallBladeItem> RECALL_BLADE = ITEMS.registerItem(
            "recall_blade", RecallBladeItem::new, properties -> properties.sword(ArmoryMaterials.RECALL, 3.0F, -2.4F));
    public static final DeferredItem<GraveMaulItem> GRAVE_MAUL = ITEMS.registerItem(
            "grave_maul", GraveMaulItem::new, properties -> properties.sword(ArmoryMaterials.MAUL, 5.0F, -3.4F));
    public static final DeferredItem<HushSpearItem> HUSH_SPEAR = ITEMS.registerItem(
            "hush_spear", HushSpearItem::new, properties -> properties.spear(ArmoryMaterials.SPEAR, 0.9F, 0.88F, 0.6F, 4.0F, 12.0F, 8.0F, 5.1F, 12.0F, 4.6F));
    public static final DeferredItem<ChorusSlingItem> CHORUS_SLING = ITEMS.registerItem(
            "chorus_sling", ChorusSlingItem::new, properties -> properties.durability(180).stacksTo(1));
    public static final DeferredItem<ScarBrandItem> SCAR_BRAND = ITEMS.registerItem(
            "scar_brand", ScarBrandItem::new, properties -> properties.durability(128).stacksTo(1));

    public static final DeferredItem<SpawnEggItem> LEDGER_MITE_SPAWN_EGG = ITEMS.registerItem(
            "ledger_mite_spawn_egg", SpawnEggItem::new, properties -> properties.spawnEgg(ModEntities.LEDGER_MITE.get()));
    public static final DeferredItem<SpawnEggItem> KIN_WITNESS_SPAWN_EGG = ITEMS.registerItem(
            "kin_witness_spawn_egg", SpawnEggItem::new, properties -> properties.spawnEgg(ModEntities.KIN_WITNESS.get()));
    public static final DeferredItem<SpawnEggItem> FRACTURE_STALKER_SPAWN_EGG = ITEMS.registerItem(
            "fracture_stalker_spawn_egg", SpawnEggItem::new, properties -> properties.spawnEgg(ModEntities.FRACTURE_STALKER.get()));

    private ArmoryItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private static DeferredItem<Item> armor(String name, net.minecraft.world.item.equipment.ArmorMaterial material, ArmorType type) {
        return ITEMS.registerItem(name, Item::new, properties -> properties.humanoidArmor(material, type));
    }
}
