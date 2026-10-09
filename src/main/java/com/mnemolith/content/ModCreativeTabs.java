package com.mnemolith.content;

import com.mnemolith.Mnemolith;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mnemolith.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MNEMOLITH = CREATIVE_MODE_TABS.register("mnemolith", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mnemolith"))
            .icon(() -> new ItemStack(ModItems.CHRONICLE_LENS.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.FIELD_GUIDE.get());
                output.accept(ModItems.CHRONICLE_LENS.get());
                output.accept(ModItems.EXTRACTION_NEEDLE.get());
                output.accept(ModItems.REINFORCED_NEEDLE.get());
                output.accept(ModItems.TWIN_NEEDLE.get());
                output.accept(ModItems.IMPRINT_SEAL.get());
                output.accept(ModItems.IMPRINT_SLIP.get());
                output.accept(ModItems.ECHO_SLIP.get());
                output.accept(ModItems.ECHO_RECORDING.get());
                output.accept(ModItems.ECHO_CHORUS_SLIP.get());
                output.accept(ModItems.ECHO_LONG_SLIP.get());
                output.accept(ModItems.ECHO_STURDY_SLIP.get());
                output.accept(ModItems.COMPOSITION_REEL.get());
                output.accept(ModItems.MUTE_STONE.get());
                output.accept(ModItems.MUTE_STONE_BRICKS.get());
                output.accept(ModItems.MUTE_STONE_STAIRS.get());
                output.accept(ModItems.MUTE_STONE_SLAB.get());
                output.accept(ModItems.MUTE_STONE_WALL.get());
                output.accept(ModItems.SELECTIVE_MUTE_STONE.get());
                output.accept(ModItems.PRESSURE_SENSOR.get());
                output.accept(ModItems.PLAYER_MEMORIAL.get());
                output.accept(ModItems.ECHO_HOME.get());
                output.accept(ModItems.PRESSURE_LAMP.get());
                output.accept(ModItems.ARCHIVAL_STRATUM.get());
                output.accept(ModItems.ARCHIVAL_STRATUM_BRICKS.get());
                output.accept(ModItems.ARCHIVAL_STRATUM_STAIRS.get());
                output.accept(ModItems.ARCHIVAL_STRATUM_SLAB.get());
                output.accept(ModItems.ARCHIVAL_STRATUM_WALL.get());
                output.accept(ModItems.HOLLOW_TURF.get());
                output.accept(ModItems.HOLLOWSTONE.get());
                output.accept(ModItems.HOLLOWSTONE_BRICKS.get());
                output.accept(ModItems.HOLLOWSTONE_BRICK_STAIRS.get());
                output.accept(ModItems.HOLLOWSTONE_BRICK_SLAB.get());
                output.accept(ModItems.HOLLOWSTONE_BRICK_WALL.get());
                output.accept(ModItems.RECOLLITE_ORE.get());
                output.accept(ModItems.RECOLLITE_SHARD.get());
                output.accept(ModItems.RECOLLITE_BLOCK.get());
                output.accept(ModItems.FORGET_ME_NOT.get());
                output.accept(ModItems.ARCHIVAL_TABLET.get());
                output.accept(ModItems.ARCHIVE_SCHEMATIC.get());
                output.accept(ModItems.RESONATOR_TRAP.get());
                output.accept(ModItems.ARCHIVIST_BAIT.get());
                output.accept(ModItems.CATALOG_FRAGMENT.get());
                output.accept(ModItems.ARCHIVIST_HUSK.get());
                output.accept(ModItems.UNSTABLE_SLIP.get());
                for (com.mnemolith.echo.graft.Temper temper : com.mnemolith.echo.graft.Temper.values()) {
                    output.accept(com.mnemolith.echo.residue.Residues.shard(temper.tag(), 4, net.minecraft.core.BlockPos.ZERO, 0L));
                }
                output.accept(ModItems.MEMORY_COMPASS.get());
                output.accept(ModItems.ECHO_ARMOR_TRIM_SMITHING_TEMPLATE.get());
                output.accept(ModItems.SCAR_FRAGMENT.get());
                output.accept(ModItems.SCAR_GLASS.get());
                output.accept(ModItems.SCAR_GLASS_PANE.get());
                output.accept(ModItems.RELAY_THREAD.get());
                output.accept(ModItems.MUSIC_DISC_RECOLLECTION.get());
                output.accept(ModItems.ARCHIVE_VAULT.get());
                output.accept(ModItems.ARCHIVE_SHRINE.get());
                output.accept(ModItems.ECHO_STRIDER_SPAWN_EGG.get());
                output.accept(ModItems.ARCHIVIST_SPAWN_EGG.get());
                output.accept(ModItems.MOMENT_REPLICANT_SPAWN_EGG.get());
                output.accept(com.mnemolith.armory.ArmoryItems.HUSH_FIBER.get());
                output.accept(com.mnemolith.armory.ArmoryItems.HUSH_HELMET.get());
                output.accept(com.mnemolith.armory.ArmoryItems.HUSH_CHESTPLATE.get());
                output.accept(com.mnemolith.armory.ArmoryItems.HUSH_LEGGINGS.get());
                output.accept(com.mnemolith.armory.ArmoryItems.HUSH_BOOTS.get());
                output.accept(com.mnemolith.armory.ArmoryItems.HUSH_SPEAR.get());
                output.accept(com.mnemolith.armory.ArmoryItems.GRAVE_SCALE.get());
                output.accept(com.mnemolith.armory.ArmoryItems.GRAVE_HELMET.get());
                output.accept(com.mnemolith.armory.ArmoryItems.GRAVE_CHESTPLATE.get());
                output.accept(com.mnemolith.armory.ArmoryItems.GRAVE_LEGGINGS.get());
                output.accept(com.mnemolith.armory.ArmoryItems.GRAVE_BOOTS.get());
                output.accept(com.mnemolith.armory.ArmoryItems.GRAVE_MAUL.get());
                output.accept(com.mnemolith.armory.ArmoryItems.ECHO_HELMET.get());
                output.accept(com.mnemolith.armory.ArmoryItems.ECHO_CHESTPLATE.get());
                output.accept(com.mnemolith.armory.ArmoryItems.ECHO_LEGGINGS.get());
                output.accept(com.mnemolith.armory.ArmoryItems.ECHO_BOOTS.get());
                output.accept(com.mnemolith.armory.ArmoryItems.RECALL_BLADE.get());
                output.accept(com.mnemolith.armory.ArmoryItems.CHORUS_SLING.get());
                output.accept(com.mnemolith.armory.ArmoryItems.MEMORY_BOLT.get());
                output.accept(com.mnemolith.armory.ArmoryItems.SCAR_SINEW.get());
                output.accept(com.mnemolith.armory.ArmoryItems.SCAR_HELMET.get());
                output.accept(com.mnemolith.armory.ArmoryItems.SCAR_CHESTPLATE.get());
                output.accept(com.mnemolith.armory.ArmoryItems.SCAR_LEGGINGS.get());
                output.accept(com.mnemolith.armory.ArmoryItems.SCAR_BOOTS.get());
                output.accept(com.mnemolith.armory.ArmoryItems.SCAR_BRAND.get());
                output.accept(com.mnemolith.armory.ArmoryItems.LEDGER_MITE_SPAWN_EGG.get());
                output.accept(com.mnemolith.armory.ArmoryItems.KIN_WITNESS_SPAWN_EGG.get());
                output.accept(com.mnemolith.armory.ArmoryItems.FRACTURE_STALKER_SPAWN_EGG.get());
            })
            .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .build());

    private ModCreativeTabs() {}
}
