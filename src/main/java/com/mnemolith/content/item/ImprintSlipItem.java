package com.mnemolith.content.item;

import org.jspecify.annotations.Nullable;

import com.mnemolith.data.ImprintSlips;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ImprintSlipItem extends Item {
    public ImprintSlipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (owner instanceof ServerPlayer player) {
            ImprintSlips.settle(player, stack);
        }
    }
}
