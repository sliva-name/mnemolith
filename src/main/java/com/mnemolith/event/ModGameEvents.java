package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.ServerConfig;

import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Common game events. Fires on the integrated server and the dedicated server.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class ModGameEvents {
    private ModGameEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Mnemolith.LOGGER.info(
                "Mnemolith logical server starting; allowRecollectionStorms={} maxStormsPerDimension={}",
                ServerConfig.ALLOW_RECOLLECTION_STORMS.get(),
                ServerConfig.MAX_STORMS_PER_DIMENSION.get());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mnemolith")
                .then(Commands.literal("inspect").executes(MnemolithCommands::inspect))
                .then(Commands.literal("smoke")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(MnemolithCommands::smoke))
                .then(Commands.literal("spawn")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .then(Commands.literal("echo_strider").executes(context -> MnemolithCommands.spawn(context, "echo_strider")))
                        .then(Commands.literal("archivist").executes(context -> MnemolithCommands.spawn(context, "archivist")))
                        .then(Commands.literal("moment_replicant").executes(context -> MnemolithCommands.spawn(context, "moment_replicant"))))
                .then(Commands.literal("mobs")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(MnemolithCommands::mobs))
                .then(Commands.literal("worldgen")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(MnemolithCommands::worldgen))
                .then(Commands.literal("perf")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(MnemolithCommands::perf))
                .then(Commands.literal("mpsmoke")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(MnemolithCommands::mpsmoke)));
    }
}
