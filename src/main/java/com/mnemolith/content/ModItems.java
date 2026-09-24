package com.mnemolith.content;

import java.util.function.UnaryOperator;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.content.item.ExtractionNeedleItem;
import com.mnemolith.content.item.ImprintSlipItem;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
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
    public static final DeferredItem<Item> ARCHIVIST_BAIT = ITEMS.registerItem("archivist_bait", Item::new, stackTo(16));
    public static final DeferredItem<Item> CATALOG_FRAGMENT = ITEMS.registerItem("catalog_fragment", Item::new, stackTo(64));
    public static final DeferredItem<Item> ARCHIVIST_HUSK = ITEMS.registerItem("archivist_husk", Item::new, stackTo(64));
    public static final DeferredItem<Item> UNSTABLE_SLIP = ITEMS.registerItem("unstable_slip", Item::new, stackTo(16));
    public static final DeferredItem<Item> ARCHIVAL_TABLET = ITEMS.registerItem("archival_tablet", Item::new, stackTo(16));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MUTE_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.MUTE_STONE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> COMPOSITION_REEL = ITEMS.registerSimpleBlockItem(ModBlocks.COMPOSITION_REEL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> RESONATOR_TRAP = ITEMS.registerSimpleBlockItem(ModBlocks.RESONATOR_TRAP);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ARCHIVAL_STRATUM = ITEMS.registerSimpleBlockItem(ModBlocks.ARCHIVAL_STRATUM);
    public static final DeferredItem<SpawnEggItem> ECHO_STRIDER_SPAWN_EGG = ITEMS.registerItem(
            "echo_strider_spawn_egg",
            SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.ECHO_STRIDER.get()));
    public static final DeferredItem<SpawnEggItem> ARCHIVIST_SPAWN_EGG = ITEMS.registerItem(
            "archivist_spawn_egg",
            SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.ARCHIVIST.get()));
    public static final DeferredItem<SpawnEggItem> MOMENT_REPLICANT_SPAWN_EGG = ITEMS.registerItem(
            "moment_replicant_spawn_egg",
            SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.MOMENT_REPLICANT.get()));

    private ModItems() {}

    private static UnaryOperator<Item.Properties> stackToOne() {
        return properties -> properties.stacksTo(1);
    }

    private static UnaryOperator<Item.Properties> stackTo(int size) {
        return properties -> properties.stacksTo(size);
    }

    private static UnaryOperator<Item.Properties> needle() {
        return properties -> properties.durability(ImprintConstants.NEEDLE_DURABILITY);
    }

    private static UnaryOperator<Item.Properties> slip() {
        return properties -> properties.stacksTo(ImprintConstants.SLIP_STACK_SIZE);
    }
}
