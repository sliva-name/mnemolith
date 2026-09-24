package com.mnemolith;

import org.slf4j.Logger;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.common.ModInfo;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.config.ServerConfig;
import com.mnemolith.content.ModContent;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.entity.ModEffects;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.ModEntityAttributes;
import com.mnemolith.entity.ModSpawnPlacements;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.ModNetwork;
import com.mnemolith.worldgen.ModWorldgen;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Common entrypoint. Loaded on the physical client and the dedicated server.
 * Client-only types stay behind {@link MnemolithClient}.
 */
@Mod(Mnemolith.MOD_ID)
public final class Mnemolith {
    public static final String MOD_ID = "mnemolith";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mnemolith(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ModContent.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEntities.register(modEventBus);
        modEventBus.addListener(ModEntityAttributes::onCreate);
        modEventBus.addListener(ModSpawnPlacements::onRegister);
        ModEffects.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModNetwork.register(modEventBus);
        ModWorldgen.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        LOGGER.info("Mnemolith ({}) common entry loaded", ModInfo.DISPLAY_NAME_RU);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info(
                "Mnemolith common setup; compositionEnabled={} recollectionStormThreshold={}",
                CommonConfig.COMPOSITION_ENABLED.get(),
                CommonConfig.RECOLLECTION_STORM_THRESHOLD.get());
    }
}
