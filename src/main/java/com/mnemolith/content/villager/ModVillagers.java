package com.mnemolith.content.villager;

import com.google.common.collect.ImmutableSet;
import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Chronicler profession: composition reel as workstation (B1). */
public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, Mnemolith.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(BuiltInRegistries.VILLAGER_PROFESSION, Mnemolith.MOD_ID);

    public static final ResourceKey<PoiType> CHRONICLER_POI_KEY = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE, Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chronicler"));

    public static final DeferredHolder<PoiType, PoiType> CHRONICLER_POI = POI_TYPES.register("chronicler", () -> new PoiType(
            ImmutableSet.copyOf(ModBlocks.COMPOSITION_REEL.get().getStateDefinition().getPossibleStates()),
            1,
            1));

    public static final ResourceKey<TradeSet> CHRONICLER_LEVEL_1 = trade("chronicler/level_1");
    public static final ResourceKey<TradeSet> CHRONICLER_LEVEL_2 = trade("chronicler/level_2");
    public static final ResourceKey<TradeSet> CHRONICLER_LEVEL_3 = trade("chronicler/level_3");
    public static final ResourceKey<TradeSet> CHRONICLER_LEVEL_4 = trade("chronicler/level_4");
    public static final ResourceKey<TradeSet> CHRONICLER_LEVEL_5 = trade("chronicler/level_5");

    public static final DeferredHolder<VillagerProfession, VillagerProfession> CHRONICLER = PROFESSIONS.register("chronicler", () -> new VillagerProfession(
            Component.translatable("entity.mnemolith.villager.chronicler"),
            holder -> holder.is(CHRONICLER_POI_KEY),
            holder -> holder.is(CHRONICLER_POI_KEY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_LIBRARIAN,
            Int2ObjectMap.ofEntries(
                    Int2ObjectMap.entry(1, CHRONICLER_LEVEL_1),
                    Int2ObjectMap.entry(2, CHRONICLER_LEVEL_2),
                    Int2ObjectMap.entry(3, CHRONICLER_LEVEL_3),
                    Int2ObjectMap.entry(4, CHRONICLER_LEVEL_4),
                    Int2ObjectMap.entry(5, CHRONICLER_LEVEL_5))));

    private ModVillagers() {}

    private static ResourceKey<TradeSet> trade(String path) {
        return ResourceKey.create(Registries.TRADE_SET, Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path));
    }

    public static void register(IEventBus modEventBus) {
        POI_TYPES.register(modEventBus);
        PROFESSIONS.register(modEventBus);
    }
}
