package com.mnemolith.content;

import java.util.function.UnaryOperator;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.item.CatalogFragmentItem;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.content.item.EchoRecordingItem;
import com.mnemolith.content.item.EchoSlipItem;
import com.mnemolith.content.item.ExtractionNeedleItem;
import com.mnemolith.content.item.FieldGuideItem;
import com.mnemolith.content.item.ImprintSlipItem;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.core.component.DataComponents;
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
    public static final DeferredItem<FieldGuideItem> FIELD_GUIDE = ITEMS.registerItem(
            "field_guide",
            FieldGuideItem::new,
            stackTo(1));
    public static final DeferredItem<CatalogFragmentItem> CATALOG_FRAGMENT = ITEMS.registerItem(
            "catalog_fragment",
            CatalogFragmentItem::new,
            stackTo(64));
    public static final DeferredItem<Item> ARCHIVIST_HUSK = ITEMS.registerItem("archivist_husk", Item::new, stackTo(64));
    public static final DeferredItem<Item> UNSTABLE_SLIP = ITEMS.registerItem("unstable_slip", Item::new, stackTo(16));
    public static final DeferredItem<Item> ARCHIVAL_TABLET = ITEMS.registerItem("archival_tablet", Item::new, stackTo(16));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MUTE_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.MUTE_STONE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> COMPOSITION_REEL = ITEMS.registerSimpleBlockItem(ModBlocks.COMPOSITION_REEL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> RESONATOR_TRAP = ITEMS.registerSimpleBlockItem(ModBlocks.RESONATOR_TRAP);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ARCHIVAL_STRATUM = ITEMS.registerSimpleBlockItem(ModBlocks.ARCHIVAL_STRATUM);
    public static final DeferredItem<EchoSlipItem> ECHO_SLIP = ITEMS.registerItem("echo_slip", EchoSlipItem::new, stackTo(16));
    public static final DeferredItem<EchoRecordingItem> ECHO_RECORDING = ITEMS.registerItem(
            "echo_recording",
            EchoRecordingItem::new,
            properties -> properties.stacksTo(1).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    public static final DeferredItem<com.mnemolith.content.item.EchoUpgradeItem> ECHO_CHORUS_SLIP = ITEMS.registerItem(
            "echo_chorus_slip", properties -> new com.mnemolith.content.item.EchoUpgradeItem(com.mnemolith.echo.EchoProgress.Kind.CHORUS, properties), stackTo(16));
    public static final DeferredItem<com.mnemolith.content.item.EchoUpgradeItem> ECHO_LONG_SLIP = ITEMS.registerItem(
            "echo_long_slip", properties -> new com.mnemolith.content.item.EchoUpgradeItem(com.mnemolith.echo.EchoProgress.Kind.LONG_TAKE, properties), stackTo(16));
    public static final DeferredItem<com.mnemolith.content.item.EchoUpgradeItem> ECHO_STURDY_SLIP = ITEMS.registerItem(
            "echo_sturdy_slip", properties -> new com.mnemolith.content.item.EchoUpgradeItem(com.mnemolith.echo.EchoProgress.Kind.STURDY, properties), stackTo(16));
    public static final DeferredItem<com.mnemolith.content.item.ResidualShardItem> RESIDUAL_SHARD = ITEMS.registerItem(
            "residual_shard", com.mnemolith.content.item.ResidualShardItem::new, properties -> properties.stacksTo(1));
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
        return properties -> properties.stacksTo(1).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    private static UnaryOperator<Item.Properties> stackTo(int size) {
        return properties -> properties.stacksTo(size);
    }

    private static UnaryOperator<Item.Properties> needle() {
        return properties -> properties.durability(ImprintConstants.NEEDLE_DURABILITY);
    }

    private static UnaryOperator<Item.Properties> slip() {
        return properties -> properties.stacksTo(ImprintConstants.SLIP_STACK_SIZE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }
}
