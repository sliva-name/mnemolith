package com.mnemolith.content;

import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

import com.mnemolith.Mnemolith;

/** Tooltip lines for the lens, needle, and imprint slips. Avoids the deprecated item hover hook. */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class ItemTooltips {
    private ItemTooltips() {}

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, (stack, context, display, player, flag, builder) -> {
            if (stack.getItem() == ModItems.CHRONICLE_LENS.get()) {
                builder.accept(Component.translatable("item.mnemolith.chronicle_lens.hint"));
            } else if (stack.getItem() == ModItems.ARCHIVIST_BAIT.get()) {
                builder.accept(Component.translatable("item.mnemolith.archivist_bait.hint"));
            } else if (stack.getItem() == ModItems.EXTRACTION_NEEDLE.get()) {
                builder.accept(Component.translatable("item.mnemolith.extraction_needle.hint"));
            } else if (stack.getItem() == ModItems.ARCHIVAL_TABLET.get()) {
                builder.accept(Component.translatable("item.mnemolith.archival_tablet.hint"));
            } else if (stack.getItem() == ModItems.CATALOG_FRAGMENT.get()) {
                builder.accept(Component.translatable("item.mnemolith.catalog_fragment.hint"));
            }
        });
        event.registerComponentAppenderBeforeAll(ModDataComponents.IMPRINT_CAST, (stack, context, display, player, flag, builder) -> {
            ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
            if (cast == null) {
                builder.accept(Component.translatable("item.mnemolith.imprint_slip.empty"));
                return;
            }
            builder.accept(Component.translatable(
                    "item.mnemolith.imprint_slip.cast",
                    Component.translatable(cast.tag().translationKey()),
                    cast.intensity()));
        });
    }
}
