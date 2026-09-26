package com.mnemolith.echo.job;

import com.mnemolith.entity.echo.EchoInventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Small string and stack helpers the job controllers share. */
final class JobTexts {
    private JobTexts() {}

    static int count(EchoInventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static String key(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
