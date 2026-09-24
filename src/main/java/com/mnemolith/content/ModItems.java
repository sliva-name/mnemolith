package com.mnemolith.content;

import java.util.function.UnaryOperator;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.content.item.ExtractionNeedleItem;
import com.mnemolith.content.item.ImprintSlipItem;
import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mnemolith.MOD_ID);

    public static final DeferredItem<ChronicleLensItem> CHRONICLE_LENS = ITEMS.registerItem(
            "chronicle_lens",
            ChronicleLensItem::new,
            stackToOne());
    public static final DeferredItem<ExtractionNeedleItem> EXTRACTION_NEEDLE = ITEMS.registerItem(
            "extraction_needle",
            ExtractionNeedleItem::new,
            needle());
    public static final DeferredItem<ImprintSlipItem> IMPRINT_SLIP = ITEMS.registerItem(
            "imprint_slip",
            ImprintSlipItem::new,
            slip());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MUTE_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.MUTE_STONE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> COMPOSITION_REEL = ITEMS.registerSimpleBlockItem(ModBlocks.COMPOSITION_REEL);

    private ModItems() {}

    private static UnaryOperator<Item.Properties> stackToOne() {
        return properties -> properties.stacksTo(1);
    }

    private static UnaryOperator<Item.Properties> needle() {
        return properties -> properties.durability(ImprintConstants.NEEDLE_DURABILITY);
    }

    private static UnaryOperator<Item.Properties> slip() {
        return properties -> properties.stacksTo(ImprintConstants.SLIP_STACK_SIZE);
    }
}
