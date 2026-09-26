package com.mnemolith.armory;

import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

/** Counts worn pieces. Set bonuses read this; the armor value itself stays on the item. */
public final class Armory {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Armory() {}

    public static ArmorySet setOf(ItemStack stack) {
        Item item = stack.getItem();
        if (item == ArmoryItems.HUSH_HELMET.get() || item == ArmoryItems.HUSH_CHESTPLATE.get() || item == ArmoryItems.HUSH_LEGGINGS.get() || item == ArmoryItems.HUSH_BOOTS.get()) {
            return ArmorySet.HUSH;
        }
        if (item == ArmoryItems.GRAVE_HELMET.get() || item == ArmoryItems.GRAVE_CHESTPLATE.get() || item == ArmoryItems.GRAVE_LEGGINGS.get() || item == ArmoryItems.GRAVE_BOOTS.get()) {
            return ArmorySet.GRAVE;
        }
        if (item == ArmoryItems.ECHO_HELMET.get() || item == ArmoryItems.ECHO_CHESTPLATE.get() || item == ArmoryItems.ECHO_LEGGINGS.get() || item == ArmoryItems.ECHO_BOOTS.get()) {
            return ArmorySet.ECHO;
        }
        if (item == ArmoryItems.SCAR_HELMET.get() || item == ArmoryItems.SCAR_CHESTPLATE.get() || item == ArmoryItems.SCAR_LEGGINGS.get() || item == ArmoryItems.SCAR_BOOTS.get()) {
            return ArmorySet.SCAR;
        }
        return null;
    }

    public static int pieces(LivingEntity entity, ArmorySet set) {
        int count = 0;
        for (EquipmentSlot slot : ARMOR) {
            if (setOf(entity.getItemBySlot(slot)) == set) {
                count++;
            }
        }
        return count;
    }

    public static boolean full(LivingEntity entity, ArmorySet set) {
        return pieces(entity, set) == 4;
    }

    /** A fracture chunk under the wearer. Scar plate only spends instability there. */
    public static boolean inFracture(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        var memory = LoadedChunkMemory.existing(level.getChunkAt(entity.blockPosition()));
        if (memory == null) {
            return false;
        }
        return com.mnemolith.pressure.MemoryPressure.band(memory.cachedPressure()) == com.mnemolith.pressure.PressureBand.FRACTURE;
    }

    public static boolean overloaded(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        var memory = LoadedChunkMemory.existing(level.getChunkAt(entity.blockPosition()));
        if (memory == null) {
            return false;
        }
        var band = com.mnemolith.pressure.MemoryPressure.band(memory.cachedPressure());
        return band == com.mnemolith.pressure.PressureBand.OVERLOADED || band == com.mnemolith.pressure.PressureBand.FRACTURE;
    }

    public static ChunkPos chunk(LivingEntity entity) {
        return ChunkPos.containing(entity.blockPosition());
    }
}
