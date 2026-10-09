package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.entity.MemoryMob;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.echo.PastSelf;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mnemolith} with the wrong caller or the wrong words: a player without operator rights cannot reach the
 * spawning and QA commands (inspect stays open), unknown or missing sub-commands fail to parse, a player-only command
 * run from the console says so and does nothing, and an unknown mob name spawns nothing. Nothing may appear in the world.
 */
final class CommandNegativeTests {
    private CommandNegativeTests() {}

    private static final int LANE = 66;

    /** Every operator-only branch, with a sample of its arguments. */
    private static final List<String> GATED = List.of("smoke", "spawn archivist", "spawn archive_guardian", "mobs", "worldgen", "perf", "mpsmoke",
            "echoqa", "jobqa", "mineqa", "echo3qa", "graftqa", "residueqa", "stormqa", "guardqa", "relayqa", "recallqa", "rememberqa", "rememberqa death",
            "investigateqa", "useqa", "interveneqa", "echodemo farm", "echodemo guard", "qa", "debug pressure fracture", "debug storm stop", "debug misfire seed", "debug farm grow", "debug relay link", "debug residue observatory", "debug stranger", "debug imprint fire 1");

    static void badCalls(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(0);
            CommandDispatcher<CommandSourceStack> dispatcher = level.getServer().getCommands().getDispatcher();
            CommandSourceStack console = level.getServer().createCommandSourceStack().withLevel(level).withPosition(Vec3.atBottomCenterOf(at)).withSuppressedOutput();
            CommandSourceStack guest = console.withPermission(PermissionSet.NO_PERMISSIONS);
            int before = mobs(level, at);

            for (String command : GATED) {
                ParseResults<CommandSourceStack> parse = dispatcher.parse("mnemolith " + command, guest);
                helper.assertTrue(!runs(parse), "a player without operator rights can run /mnemolith " + command);
                helper.assertTrue(throwsSyntax(dispatcher, parse), "/mnemolith " + command + " without operator rights did not fail cleanly");
                // Positive control: the same words parse for an operator (so the refusal above is the permission gate).
                helper.assertTrue(runs(dispatcher.parse("mnemolith " + command, console)), "/mnemolith " + command + " does not parse even for an operator");
            }
            // Inspect is for everyone.
            try {
                int pressure = dispatcher.execute(dispatcher.parse("mnemolith inspect", guest));
                helper.assertTrue(pressure >= 0, "inspect returned " + pressure);
            } catch (CommandSyntaxException ex) {
                throw helper.assertionException(net.minecraft.network.chat.Component.literal("inspect failed for a player without operator rights: " + ex.getMessage()));
            }

            // Unknown, missing and extra words, even for an operator.
            for (String command : List.of("mnemolith spawn dragon", "mnemolith spawn", "mnemolith spawn Archivist", "mnemolith rememberqa sunset",
                    "mnemolith echodemo", "mnemolith echodemo moon", "mnemolith inspect now", "mnemolith qa please", "mnemolith nonsense", "mnemolith")) {
                ParseResults<CommandSourceStack> parse = dispatcher.parse(command, console);
                helper.assertTrue(!runs(parse) && throwsSyntax(dispatcher, parse), "'" + command + "' did not fail cleanly");
            }

            // A player-only command from the console: refused, 0, no scene.
            try {
                int result = dispatcher.execute(dispatcher.parse("mnemolith rememberqa death", console));
                helper.assertTrue(result == 0, "rememberqa death from the console returned " + result);
            } catch (CommandSyntaxException ex) {
                throw helper.assertionException(net.minecraft.network.chat.Component.literal("rememberqa death from the console threw: " + ex.getMessage()));
            }
            helper.assertTrue(level.getEntitiesOfClass(PastSelf.class, new AABB(at).inflate(32.0D)).isEmpty(), "rememberqa death from the console staged a scene");

            // An unknown mob name.
            helper.assertTrue(MobSpawns.summonNamed(level, at, "dragon") == null && MobSpawns.summonNamed(level, at, "") == null, "an unknown mob name spawned something");
            helper.assertTrue(mobs(level, at) == before, "refused commands spawned " + (mobs(level, at) - before) + " mobs");
            helper.succeed();
        }
    }

    /** The parse reached an executable node and consumed every word, with no error. */
    private static boolean runs(ParseResults<CommandSourceStack> parse) {
        return !parse.getReader().canRead() && parse.getExceptions().isEmpty() && parse.getContext().getCommand() != null;
    }

    private static boolean throwsSyntax(CommandDispatcher<CommandSourceStack> dispatcher, ParseResults<CommandSourceStack> parse) {
        try {
            dispatcher.execute(parse);
            return false;
        } catch (CommandSyntaxException ex) {
            return true;
        }
    }

    private static int mobs(ServerLevel level, BlockPos at) {
        return level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(16.0D), e -> e instanceof MemoryMob || e instanceof com.mnemolith.entity.echo.ScarEntity
                || e instanceof com.mnemolith.entity.PleadingChair).size();
    }
}
