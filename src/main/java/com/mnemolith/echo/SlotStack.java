package com.mnemolith.echo;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** A non-empty stack and the slot it came from. Used for saved echo inventories and the stored real inventory. */
public record SlotStack(int slot, ItemStack stack) {
    public static final Codec<SlotStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(SlotStack::slot),
            ItemStack.CODEC.fieldOf("item").forGetter(SlotStack::stack))
            .apply(instance, SlotStack::new));
    public static final Codec<List<SlotStack>> LIST_CODEC = CODEC.listOf();

    /** Moves every non-empty stack out of {@code container} (the container is left empty). */
    public static List<SlotStack> drain(Container container) {
        List<SlotStack> out = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                out.add(new SlotStack(i, stack));
            }
        }
        return out;
    }

    /** Copies (not moves) every non-empty stack, for saving. */
    public static List<SlotStack> snapshot(Container container) {
        List<SlotStack> out = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                out.add(new SlotStack(i, stack.copy()));
            }
        }
        return out;
    }

    public static int count(Iterable<SlotStack> stacks) {
        int total = 0;
        for (SlotStack stack : stacks) {
            total += stack.stack().getCount();
        }
        return total;
    }
}
