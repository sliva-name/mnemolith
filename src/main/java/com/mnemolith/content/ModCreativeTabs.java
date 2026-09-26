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
                output.accept(ModItems.IMPRINT_SLIP.get());
                output.accept(ModItems.ECHO_SLIP.get());
                output.accept(ModItems.ECHO_CHORUS_SLIP.get());
                output.accept(ModItems.ECHO_LONG_SLIP.get());
                output.accept(ModItems.ECHO_STURDY_SLIP.get());
                output.accept(ModItems.COMPOSITION_REEL.get());
                output.accept(ModItems.MUTE_STONE.get());
                output.accept(ModItems.ARCHIVAL_STRATUM.get());
                output.accept(ModItems.ARCHIVAL_TABLET.get());
                output.accept(ModItems.RESONATOR_TRAP.get());
                output.accept(ModItems.ARCHIVIST_BAIT.get());
                output.accept(ModItems.CATALOG_FRAGMENT.get());
                output.accept(ModItems.ARCHIVIST_HUSK.get());
                output.accept(ModItems.UNSTABLE_SLIP.get());
                for (com.mnemolith.echo.graft.Temper temper : com.mnemolith.echo.graft.Temper.values()) {
                    output.accept(com.mnemolith.echo.residue.Residues.shard(temper.tag(), 4, net.minecraft.core.BlockPos.ZERO, 0L));
                }
                output.accept(ModItems.SCAR_FRAGMENT.get());
                output.accept(ModItems.SCAR_GLASS.get());
                output.accept(ModItems.ECHO_STRIDER_SPAWN_EGG.get());
                output.accept(ModItems.ARCHIVIST_SPAWN_EGG.get());
                output.accept(ModItems.MOMENT_REPLICANT_SPAWN_EGG.get());
            })
            .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .build());

    private ModCreativeTabs() {}
}
