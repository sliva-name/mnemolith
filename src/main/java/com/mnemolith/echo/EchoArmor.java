package com.mnemolith.echo;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Echoes wear armor from their own inventory: every two seconds the best piece for each armor slot moves from the
 * main inventory onto the body (a swap, so the piece it replaces goes back into that main slot). "Best" is armor
 * plus half the toughness of that piece in that slot. A piece with no armor (an elytra, a pumpkin, a head) is
 * never put on, and a worn piece with Curse of Binding never comes off. Nothing is created or lost: items only move.
 */
public final class EchoArmor {
    private EchoArmor() {}

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    /** Inventory index of an armor slot (36 feet .. 39 head, the player layout). */
    public static int index(EquipmentSlot slot) {
        return Inventory.INVENTORY_SIZE + slot.getIndex();
    }

    /** How much {@code stack} protects in {@code slot}: armor + toughness / 2, or 0 when it is not armor for that slot. */
    public static double score(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot) {
            return 0.0D;
        }
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double armor = modifiers.compute(Attributes.ARMOR, 0.0D, slot);
        double toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0D, slot);
        return Math.max(0.0D, armor + toughness * 0.5D);
    }

    /** Puts on better armor from the main inventory. Returns how many pieces moved onto the body. */
    public static int equipBest(EchoEntity echo) {
        if (!CommonConfig.ECHO_ARMOR_AUTO_EQUIP.get()) {
            return 0;
        }
        EchoInventory inventory = echo.inventory();
        int moved = 0;
        for (EquipmentSlot slot : SLOTS) {
            int wornIndex = index(slot);
            ItemStack worn = inventory.getItem(wornIndex);
            if (!worn.isEmpty() && EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
                continue;
            }
            double bestScore = score(worn, slot);
            int best = -1;
            for (int i = 0; i < EchoInventory.MAIN; i++) {
                double candidate = score(inventory.getItem(i), slot);
                if (candidate > bestScore) {
                    bestScore = candidate;
                    best = i;
                }
            }
            if (best < 0) {
                continue;
            }
            ItemStack piece = inventory.getItem(best);
            inventory.setItem(best, worn);
            inventory.setItem(wornIndex, piece);
            moved++;
        }
        return moved;
    }
}
