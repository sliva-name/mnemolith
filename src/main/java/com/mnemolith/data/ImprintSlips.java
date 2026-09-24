package com.mnemolith.data;

import java.util.Optional;

import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Builds an imprint slip with a cast. Commands and mob loot share this. */
public final class ImprintSlips {
    private ImprintSlips() {}

    public static ItemStack of(ImprintTag tag, BlockPos pos) {
        ItemStack stack = new ItemStack(ModItems.IMPRINT_SLIP.get());
        Imprint imprint = new Imprint(tag, ImprintWriter.intensityFor(tag), pos, Optional.empty(), Imprint.contextHash(tag, pos, 0L), 0L);
        stack.set(ModDataComponents.IMPRINT_CAST.get(), ImprintCast.from(imprint));
        return stack;
    }

    public static boolean isSlip(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModItems.IMPRINT_SLIP.get() && stack.get(ModDataComponents.IMPRINT_CAST.get()) != null;
    }

    public static int weight(ItemStack stack) {
        ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
        if (cast == null) {
            return -1;
        }
        return cast.tag().weight();
    }
}
