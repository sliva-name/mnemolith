package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.command.InspectCommand;
import com.mnemolith.command.MobCommands;
import com.mnemolith.command.WorldgenCommand;
import com.mnemolith.command.qa.MnemolithQa;
import com.mnemolith.command.qa.MultiplayerSmoke;
import com.mnemolith.command.qa.PerfCommand;
import com.mnemolith.command.qa.SmokeCommand;
import com.mnemolith.config.ServerConfig;
import com.mnemolith.pressure.MemoryPressure;

import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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

    /** Static per-player maps outlive the server in single player; start the next world clean. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MobEvents.clearAll();
        MemoryPressure.clearDeferred();
        com.mnemolith.echo.storm.Storms.clearBars();
        com.mnemolith.echo.relay.EchoRelays.clearPending();
        com.mnemolith.vault.ArchiveVaults.clearAll();
    }

    /** Replicant attempts that a chunk load queued instead of spawning inside the load event. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MemoryPressure.runDeferred(event.getServer());
        com.mnemolith.echo.storm.Storms.tick(event.getServer());
        com.mnemolith.echo.relay.EchoRelays.serverTick(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mnemolith")
                .then(Commands.literal("inspect").executes(InspectCommand::inspect))
                .then(Commands.literal("smoke")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(SmokeCommand::smoke))
                .then(Commands.literal("spawn")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .then(Commands.literal("echo_strider").executes(context -> MobCommands.spawn(context, "echo_strider")))
                        .then(Commands.literal("archivist").executes(context -> MobCommands.spawn(context, "archivist")))
                        .then(Commands.literal("moment_replicant").executes(context -> MobCommands.spawn(context, "moment_replicant"))))
                .then(Commands.literal("mobs")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(MobCommands::mobs))
                .then(Commands.literal("worldgen")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(WorldgenCommand::worldgen))
                .then(Commands.literal("perf")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(PerfCommand::perf))
                .then(Commands.literal("mpsmoke")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(MultiplayerSmoke::mpsmoke))
                .then(Commands.literal("echoqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.EchoQa::run))
                .then(Commands.literal("jobqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.JobQa::run))
                .then(Commands.literal("echo3qa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.Echo3Qa::run))
                .then(Commands.literal("graftqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.GraftQa::run))
                .then(Commands.literal("residueqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.ResidueQa::run))
                .then(Commands.literal("stormqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.StormQa::run))
                .then(Commands.literal("relayqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.RelayQa::run))
                .then(Commands.literal("echodemo")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .then(Commands.literal("farm").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "farm")))
                        .then(Commands.literal("overload").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "overload")))
                        .then(Commands.literal("door").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "door")))
                        .then(Commands.literal("gate").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "gate")))
                        .then(Commands.literal("ladder").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "ladder"))))
                .then(Commands.literal("qa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(MnemolithQa::run)));
    }
}
