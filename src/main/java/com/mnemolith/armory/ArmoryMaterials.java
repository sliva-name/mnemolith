package com.mnemolith.armory;

import java.util.Map;

import com.mnemolith.Mnemolith;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Defense numbers sit between leather and diamond. The set bonus, not the armor value, is the reason to wear one. */
public final class ArmoryMaterials {
    public static final TagKey<Item> REPAIRS_HUSH = repair("repairs_hush_armor");
    public static final TagKey<Item> REPAIRS_GRAVE = repair("repairs_grave_armor");
    public static final TagKey<Item> REPAIRS_ECHO = repair("repairs_echo_armor");
    public static final TagKey<Item> REPAIRS_SCAR = repair("repairs_scar_armor");
    public static final TagKey<Item> REPAIRS_RECALL = repair("repairs_recall_blade");
    public static final TagKey<Item> REPAIRS_MAUL = repair("repairs_grave_maul");
    public static final TagKey<Item> REPAIRS_SPEAR = repair("repairs_hush_spear");

    public static final ArmorMaterial HUSH = armor("hush", 8, defense(1, 3, 4, 2), 12, 0.0F, 0.0F, REPAIRS_HUSH, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final ArmorMaterial GRAVE = armor("grave", 18, defense(2, 5, 6, 2), 9, 1.0F, 0.05F, REPAIRS_GRAVE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final ArmorMaterial ECHO = armor("echo", 16, defense(2, 4, 5, 2), 14, 0.0F, 0.0F, REPAIRS_ECHO, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final ArmorMaterial SCAR = armor("scar", 28, defense(3, 6, 7, 3), 12, 2.0F, 0.05F, REPAIRS_SCAR, SoundEvents.ARMOR_EQUIP_NETHERITE);

    public static final ToolMaterial RECALL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 250, 6.0F, 2.0F, 14, REPAIRS_RECALL);
    public static final ToolMaterial MAUL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 400, 4.0F, 3.0F, 10, REPAIRS_MAUL);
    public static final ToolMaterial SPEAR = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 190, 5.0F, 1.0F, 12, REPAIRS_SPEAR);

    private ArmoryMaterials() {}

    private static TagKey<Item> repair(String name) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, name));
    }

    private static ResourceKey<EquipmentAsset> asset(String name) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, name));
    }

    /** Boots, leggings, chest, helmet. Body stays at the chest value; players never wear a body slot. */
    private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helm) {
        return Map.of(ArmorType.BOOTS, boots, ArmorType.LEGGINGS, legs, ArmorType.CHESTPLATE, chest, ArmorType.HELMET, helm, ArmorType.BODY, chest);
    }

    private static ArmorMaterial armor(String name, int durability, Map<ArmorType, Integer> defense, int enchant, float toughness, float knockback,
            TagKey<Item> repair, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> equip) {
        return new ArmorMaterial(durability, defense, enchant, equip, toughness, knockback, repair, asset(name));
    }
}
