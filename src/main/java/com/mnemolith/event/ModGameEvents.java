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

import com.mnemolith.command.qa.DebugCommands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

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
        com.mnemolith.worldgen.hollows.HollowFlickers.clear();
        com.mnemolith.worldgen.hollows.LecternReplay.clear();
        com.mnemolith.worldgen.hollows.HollowRegions.clear();
    }

    /** Replicant attempts that a chunk load queued instead of spawning inside the load event. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MemoryPressure.runDeferred(event.getServer());
        com.mnemolith.echo.storm.Storms.tick(event.getServer());
        com.mnemolith.echo.relay.EchoRelays.serverTick(event.getServer());
        com.mnemolith.worldgen.hollows.HollowFlickers.tick(event.getServer());
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
                        .then(Commands.literal("moment_replicant").executes(context -> MobCommands.spawn(context, "moment_replicant")))
                        .then(Commands.literal("ledger_mite").executes(context -> MobCommands.spawn(context, "ledger_mite")))
                        .then(Commands.literal("kin_witness").executes(context -> MobCommands.spawn(context, "kin_witness")))
                        .then(Commands.literal("fracture_stalker").executes(context -> MobCommands.spawn(context, "fracture_stalker")))
                        .then(Commands.literal("faded").executes(context -> MobCommands.spawn(context, "faded")))
                        .then(Commands.literal("pleading_chair").executes(context -> MobCommands.spawn(context, "pleading_chair")))
                        .then(Commands.literal("silence_mirror").executes(context -> MobCommands.spawn(context, "silence_mirror")))
                        .then(Commands.literal("archive_guardian").executes(context -> MobCommands.spawn(context, "archive_guardian"))))
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
                .then(Commands.literal("mineqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.MineQa::run))
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
                .then(Commands.literal("hollowsqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.HollowsQa::run))
                .then(Commands.literal("hollows2qa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.Hollows2Qa::run))
                .then(Commands.literal("hollows3qa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.Hollows3Qa::run))
                .then(Commands.literal("hollows")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .then(Commands.literal("survey")
                                .executes(context -> com.mnemolith.command.HollowsCommand.survey(context, 2048))
                                .then(Commands.argument("radius", com.mnemolith.command.HollowsCommand.radius())
                                        .executes(context -> com.mnemolith.command.HollowsCommand.survey(context,
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius")))))
                        .then(Commands.literal("flicker")
                                .executes(context -> com.mnemolith.command.HollowsCommand.flicker(context, -1))
                                .then(Commands.argument("scene", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4))
                                        .executes(context -> com.mnemolith.command.HollowsCommand.flicker(context,
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "scene"))))))
                .then(Commands.literal("guardqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.GuardQa::run))
                .then(Commands.literal("relayqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.RelayQa::run))
                .then(Commands.literal("recallqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.RecallQa::run))
                .then(Commands.literal("rememberqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.RememberQa::run)
                        .then(Commands.literal("death").executes(context -> com.mnemolith.command.qa.RememberQa.show(context, com.mnemolith.recall.LifeMomentKind.DEATH)))
                        .then(Commands.literal("home").executes(context -> com.mnemolith.command.qa.RememberQa.show(context, com.mnemolith.recall.LifeMomentKind.HOME)))
                        .then(Commands.literal("build").executes(context -> com.mnemolith.command.qa.RememberQa.show(context, com.mnemolith.recall.LifeMomentKind.BUILD)))
                        .then(Commands.literal("battle").executes(context -> com.mnemolith.command.qa.RememberQa.show(context, com.mnemolith.recall.LifeMomentKind.BATTLE))))
                .then(Commands.literal("investigateqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.InvestigateQa::run))
                .then(Commands.literal("useqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.UseQa::run))
                .then(Commands.literal("interveneqa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(com.mnemolith.command.qa.InterveneQa::run))
                .then(Commands.literal("echodemo")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .then(Commands.literal("farm").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "farm")))
                        .then(Commands.literal("overload").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "overload")))
                        .then(Commands.literal("door").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "door")))
                        .then(Commands.literal("gate").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "gate")))
                        .then(Commands.literal("ladder").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "ladder")))
                        .then(Commands.literal("guard").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "guard")))
                        .then(Commands.literal("archer").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "archer")))
                        .then(Commands.literal("escort").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "escort")))
                        .then(Commands.literal("worn").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "worn")))
                        .then(Commands.literal("armored").executes(context -> com.mnemolith.command.qa.EchoDemo.run(context, "armored"))))
                .then(Commands.literal("debug")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .then(Commands.literal("pressure")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 10000)).executes(DebugCommands::pressureAmount))
                                .then(Commands.literal("calm").executes(context -> DebugCommands.pressureNamed(context, "calm")))
                                .then(Commands.literal("saturated").executes(context -> DebugCommands.pressureNamed(context, "saturated")))
                                .then(Commands.literal("overloaded").executes(context -> DebugCommands.pressureNamed(context, "overloaded")))
                                .then(Commands.literal("fracture").executes(context -> DebugCommands.pressureNamed(context, "fracture"))))
                        .then(Commands.literal("storm")
                                .then(Commands.literal("start").executes(DebugCommands::stormStart))
                                .then(Commands.literal("stop").executes(DebugCommands::stormStop))
                                .then(Commands.literal("scar").executes(DebugCommands::stormScar))
                                .then(Commands.literal("step").then(Commands.argument("ticks", IntegerArgumentType.integer(1, 400)).executes(DebugCommands::stormStep))))
                        .then(Commands.literal("vault")
                                .then(Commands.literal("fill")
                                        .executes(DebugCommands::vaultFill)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(DebugCommands::vaultFill)))
                                .then(Commands.literal("leak").executes(DebugCommands::vaultLeak)))
                        .then(Commands.literal("relay")
                                .then(Commands.literal("link").executes(DebugCommands::relayLink))
                                .then(Commands.literal("thread").executes(DebugCommands::relayThread))
                                .then(Commands.literal("possess").executes(DebugCommands::relayPossess))
                                .then(Commands.literal("hop").executes(DebugCommands::relayHop)))
                        .then(Commands.literal("misfire")
                                .then(Commands.literal("skip").executes(context -> DebugCommands.misfire(context, "skip")))
                                .then(Commands.literal("wrong").executes(context -> DebugCommands.misfire(context, "wrong")))
                                .then(Commands.literal("extra").executes(context -> DebugCommands.misfire(context, "extra")))
                                .then(Commands.literal("seed").executes(context -> DebugCommands.misfire(context, "seed"))))
                        .then(Commands.literal("residue")
                                .then(Commands.literal("observatory").executes(context -> DebugCommands.residueNamed(context, "observatory")))
                                .then(Commands.literal("old").executes(DebugCommands::residueOld)
                                        .then(Commands.argument("tag", StringArgumentType.word()).executes(DebugCommands::residueOld)))
                                .then(Commands.argument("tag", StringArgumentType.word()).executes(DebugCommands::residue)
                                        .then(Commands.argument("strength", IntegerArgumentType.integer(1, 8)).executes(DebugCommands::residue))))
                        .then(Commands.literal("graft")
                                .then(Commands.argument("temper", StringArgumentType.word()).executes(DebugCommands::graft)))
                        .then(Commands.literal("farm")
                                .then(Commands.literal("grow").executes(DebugCommands::farmGrow))
                                .then(Commands.literal("show").then(Commands.argument("count", IntegerArgumentType.integer(0, 999)).executes(DebugCommands::farmShow))))
                        .then(Commands.literal("lava").executes(DebugCommands::lava))
                        .then(Commands.literal("shard")
                                .then(Commands.literal("graft").executes(DebugCommands::shardGraft))
                                .then(Commands.literal("release").executes(DebugCommands::shardRelease)))
                        .then(Commands.literal("imprint")
                                .then(Commands.argument("tag", StringArgumentType.word())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(DebugCommands::imprint))))
                        .then(Commands.literal("stranger").executes(DebugCommands::stranger)))
                .then(Commands.literal("qa")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(MnemolithQa::run)));
    }
}
