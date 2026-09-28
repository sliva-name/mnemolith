package com.mnemolith.content;

import java.util.function.UnaryOperator;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.item.CatalogFragmentItem;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.content.item.EchoRecordingItem;
import com.mnemolith.content.item.EchoSlipItem;
import com.mnemolith.content.item.ExtractionNeedleItem;
import com.mnemolith.content.item.ImprintSealItem;
import com.mnemolith.content.item.MemoryCompassItem;
import com.mnemolith.content.item.FieldGuideItem;
import com.mnemolith.content.item.ImprintSlipItem;
import com.mnemolith.content.item.ScarFragmentItem;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mnemolith.MOD_ID);

    /** Datapack trim material applied by residual shards (G2). */
    public static final ResourceKey<TrimMaterial> RESIDUAL_TRIM_MATERIAL = ResourceKey.create(
            Registries.TRIM_MATERIAL,
            Identifier.parse("mnemolith:residual"));

    public static final DeferredItem<ChronicleLensItem> CHRONICLE_LENS = ITEMS.registerItem(
            "chronicle_lens",
            ChronicleLensItem::new,
            stackToOne());
    public static final DeferredItem<ExtractionNeedleItem> EXTRACTION_NEEDLE = ITEMS.registerItem(
            "extraction_needle",
            ExtractionNeedleItem::new,
            needle());
    public static final DeferredItem<ExtractionNeedleItem> REINFORCED_NEEDLE = ITEMS.registerItem(
            "reinforced_needle",
            properties -> new ExtractionNeedleItem(properties, true, false),
            needle());
    public static final DeferredItem<ExtractionNeedleItem> TWIN_NEEDLE = ITEMS.registerItem(
            "twin_needle",
            properties -> new ExtractionNeedleItem(properties, true, true),
            needle());
    public static final DeferredItem<ImprintSealItem> IMPRINT_SEAL = ITEMS.registerItem(
            "imprint_seal",
            ImprintSealItem::new,
            stackTo(16));
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
    public static final DeferredItem<Item> ARCHIVE_SCHEMATIC = ITEMS.registerItem("archive_schematic", Item::new, stackTo(16));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MUTE_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.MUTE_STONE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SELECTIVE_MUTE_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.SELECTIVE_MUTE_STONE);
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
            "residual_shard", com.mnemolith.content.item.ResidualShardItem::new, properties -> properties.stacksTo(1).trimMaterial(RESIDUAL_TRIM_MATERIAL));
    /** Points at the nearest residue, fracture, or observatory. Crafted from a residual shard and a compass. */
    public static final DeferredItem<MemoryCompassItem> MEMORY_COMPASS = ITEMS.registerItem(
            "memory_compass",
            MemoryCompassItem::new,
            properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    /** Smithing template for the echo armor trim pattern (G2). */
    public static final DeferredItem<SmithingTemplateItem> ECHO_ARMOR_TRIM_SMITHING_TEMPLATE = ITEMS.registerItem(
            "echo_armor_trim_smithing_template",
            SmithingTemplateItem::createArmorTrimTemplate,
            properties -> properties.stacksTo(64).rarity(Rarity.UNCOMMON));
    /** The Scar's drop. Right-click your echo to scar-set it, or a block to rewrite the loudest imprint there. */
    public static final DeferredItem<ScarFragmentItem> SCAR_FRAGMENT = ITEMS.registerItem(
            "scar_fragment", ScarFragmentItem::new, properties -> properties.stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SCAR_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.SCAR_GLASS);
    /** Echo relay: ties two of your echoes into a linked pair (used up by the second end). */
    public static final DeferredItem<Item> RELAY_THREAD = ITEMS.registerItem("relay_thread", Item::new, properties -> properties.stacksTo(16));
    /** Archive vault: keeps its imprints on the item when broken, so one stack is one vault. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ARCHIVE_VAULT = ITEMS.registerSimpleBlockItem(ModBlocks.ARCHIVE_VAULT, properties -> properties.stacksTo(1));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PRESSURE_SENSOR = ITEMS.registerSimpleBlockItem(ModBlocks.PRESSURE_SENSOR);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PLAYER_MEMORIAL = ITEMS.registerSimpleBlockItem(ModBlocks.PLAYER_MEMORIAL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ECHO_HOME = ITEMS.registerSimpleBlockItem(ModBlocks.ECHO_HOME);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ARCHIVAL_STRATUM_BRICKS = ITEMS.registerSimpleBlockItem(ModBlocks.ARCHIVAL_STRATUM_BRICKS);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ARCHIVAL_STRATUM_STAIRS = ITEMS.registerSimpleBlockItem(ModBlocks.ARCHIVAL_STRATUM_STAIRS);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ARCHIVAL_STRATUM_SLAB = ITEMS.registerSimpleBlockItem(ModBlocks.ARCHIVAL_STRATUM_SLAB);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ARCHIVAL_STRATUM_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.ARCHIVAL_STRATUM_WALL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MUTE_STONE_BRICKS = ITEMS.registerSimpleBlockItem(ModBlocks.MUTE_STONE_BRICKS);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MUTE_STONE_STAIRS = ITEMS.registerSimpleBlockItem(ModBlocks.MUTE_STONE_STAIRS);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MUTE_STONE_SLAB = ITEMS.registerSimpleBlockItem(ModBlocks.MUTE_STONE_SLAB);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MUTE_STONE_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.MUTE_STONE_WALL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SCAR_GLASS_PANE = ITEMS.registerSimpleBlockItem(ModBlocks.SCAR_GLASS_PANE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PRESSURE_LAMP = ITEMS.registerSimpleBlockItem(ModBlocks.PRESSURE_LAMP);
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
