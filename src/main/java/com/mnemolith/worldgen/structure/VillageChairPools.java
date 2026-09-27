package com.mnemolith.worldgen.structure;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.Mnemolith;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Appends {@link PleadingChairProcessor} to vanilla village house pieces.
 * Shared processor lists (mossify) are copied, so streets and other structures keep theirs.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class VillageChairPools {
    private static final String[] POOLS = {
            "minecraft:village/plains/houses",
            "minecraft:village/desert/houses",
            "minecraft:village/savanna/houses",
            "minecraft:village/taiga/houses",
            "minecraft:village/snowy/houses",
            "minecraft:village/plains/zombie/houses",
            "minecraft:village/desert/zombie/houses",
            "minecraft:village/savanna/zombie/houses",
            "minecraft:village/taiga/zombie/houses",
            "minecraft:village/snowy/zombie/houses"
    };

    private VillageChairPools() {}

    @SubscribeEvent
    public static void onServerData(TagsUpdatedEvent.ServerDataLoad event) {
        attach(event.getRegistries());
    }

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        attach(event.getServer().registryAccess());
    }

    public static int attach(RegistryAccess access) {
        Registry<StructureTemplatePool> pools = access.lookupOrThrow(Registries.TEMPLATE_POOL);
        int wired = 0;
        for (String id : POOLS) {
            ResourceKey<StructureTemplatePool> key = ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.parse(id));
            StructureTemplatePool pool = pools.getValue(key.identifier());
            if (pool == null) {
                continue;
            }
            for (var pair : pool.getTemplates()) {
                StructurePoolElement element = pair.getFirst();
                if (element instanceof SinglePoolElement single && houseInterior(single) && wire(single)) {
                    wired++;
                }
            }
        }
        if (wired > 0) {
            Mnemolith.LOGGER.debug("Mnemolith pleading chair wired into {} house pieces", wired);
        }
        return wired;
    }

    /** Houses with a room. Farms, pens, stables, and the meeting points stay empty of the chair. */
    public static boolean houseInterior(SinglePoolElement element) {
        Identifier location;
        try {
            location = element.getTemplateLocation();
        } catch (RuntimeException ignored) {
            return false;
        }
        String path = location.getPath();
        if (!path.contains("/houses/")) {
            return false;
        }
        return !path.contains("farm")
                && !path.contains("animal_pen")
                && !path.contains("meeting_point")
                && !path.contains("stable")
                && !path.contains("accessory");
    }

    private static boolean wire(SinglePoolElement element) {
        List<StructureProcessor> current = element.processors.value().list();
        for (StructureProcessor processor : current) {
            if (processor instanceof PleadingChairProcessor) {
                return false;
            }
        }
        List<StructureProcessor> copy = new ArrayList<>(current);
        copy.add(PleadingChairProcessor.INSTANCE);
        element.processors = Holder.direct(new StructureProcessorList(copy));
        return true;
    }
}
