package com.mnemolith.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import com.mojang.serialization.MapCodec;
import com.mnemolith.Mnemolith;
import com.mnemolith.command.qa.Echo3Qa;
import com.mnemolith.command.qa.EchoQa;
import com.mnemolith.command.qa.GraftQa;
import com.mnemolith.command.qa.JobQa;
import com.mnemolith.command.qa.MnemolithQa;
import com.mnemolith.command.qa.MultiplayerSmoke;
import com.mnemolith.command.qa.QaReport;
import com.mnemolith.command.qa.ResidueQa;
import com.mnemolith.command.qa.StormQa;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the Mnemolith game tests: {@code ./gradlew runGameTestServer} runs them all and exits non-zero when a
 * required one fails; in a dev world {@code /test runmultiple mnemolith:} runs them by hand.
 * <p>
 * Batches (one per environment), run one after the other: {@code mnemolith:suites} (the QA suites, each done within
 * its first tick), {@code mnemolith:live} (multi-tick tests with real players, cleaned up by the environment's
 * teardown), and one batch per storm test (storms are capped per dimension, so they must not run side by side).
 */
public final class MnemolithGameTests {
    private MnemolithGameTests() {}

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Mnemolith.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends TestEnvironmentDefinition<?>>> ENVIRONMENT_TYPES =
            DeferredRegister.create(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, Mnemolith.MOD_ID);

    static {
        ENVIRONMENT_TYPES.register("live_players", () -> LivePlayers.Environment.CODEC);
    }

    /** The anchor template: 3x3x3 air (see {@code tools/gametest_structure.py}). */
    private static final Identifier EMPTY = id("gametest/empty");

    /** {@code environment}: "suites", "live", or a storm batch of its own. */
    private record Spec(DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, String environment, int maxTicks) {}

    private static final List<Spec> SPECS = new ArrayList<>();

    static {
        // The suites finish inside their first tick; maxTicks only bounds a suite that never reports. Each gets its own lane.
        suite("qa", MnemolithQa::check, Map.of("locate",
                "the game test server creates its world with structure generation off, so locate finds nothing; run /mnemolith qa on a real world"));
        suite("echoqa", EchoQa::check, Map.of());
        suite("jobqa", JobQa::check, Map.of());
        suite("echo3qa", Echo3Qa::check, Map.of());
        suite("graftqa", GraftQa::check, Map.of());
        suite("residueqa", ResidueQa::check, Map.of());
        suite("mpsmoke", MultiplayerSmoke::check, Map.of());
        suite("stormqa", StormQa::check, Map.of());
        suite("relayqa", com.mnemolith.command.qa.RelayQa::check, Map.of());

        // Formation: pulses every 200 ticks at 1 in 2; 4400 ticks is 22 pulses, a miss chance under 1 in 4 million.
        live("residue_forms_where_player_stands", ResidueLiveTests::formsWherePlayerStands, 4400);
        live("residue_lashes_player_beside", ResidueLiveTests::lashesPlayerBeside, 200);
        live("residue_sneaking_halves_reach", ResidueLiveTests::sneakingHalvesReach, 300);
        live("residue_needle_slips_when_unread", ResidueLiveTests::needleSlipsWhenUnread, 40);
        live("residue_lens_reads_then_needle_captures", ResidueLiveTests::lensReadsThenNeedleCaptures, 300);
        // Festers every 1200 ticks.
        live("residue_fester_writes_back", ResidueLiveTests::festerWritesBack, 1400);
        live("residue_mute_stone_starves", ResidueLiveTests::muteStoneStarves, 1400);
        // Seeds on the first pulse (within 200 ticks), then two more pulses must not seed again.
        live("residue_observatory_seeds_once", ResidueLiveTests::observatorySeedsOnce, 800);

        // Storms: one batch each (storms are capped per dimension). Gathering 200 ticks + six waves of 200 = 1400.
        storm("storm_shard_call_merges_into_scar", StormLiveTests::shardCallsStormIntoScar, 1700);
        storm("storm_mute_stone_contains", StormLiveTests::muteStoneContains, 300);
        storm("scar_read_then_hurt", StormLiveTests::scarReadThenHurt, 400);

        // Echo relay and archive vault. The vault draws every 200 ticks, so two draws take up to 400.
        live("relay_thread_links_then_hop", RelayLiveTests::threadLinksThenHop, 100);
        live("relay_mirror_break", RelayLiveTests::mirrorBreak, 100);
        live("vault_draws_then_spends", RelayLiveTests::vaultDrawsThenSpends, 700);
    }

    private static void suite(String name, BiFunction<ServerLevel, BlockPos, QaReport> check, Map<String, String> waived) {
        int lane = SPECS.size();
        Consumer<GameTestHelper> body = helper -> SuiteTests.run(helper, lane, check, waived);
        SPECS.add(new Spec(FUNCTIONS.register("suite_" + name, () -> body), "suites", 100));
    }

    private static void live(String name, Consumer<GameTestHelper> body, int maxTicks) {
        SPECS.add(new Spec(FUNCTIONS.register(name, () -> body), "live", maxTicks));
    }

    private static void storm(String name, Consumer<GameTestHelper> body, int maxTicks) {
        SPECS.add(new Spec(FUNCTIONS.register(name, () -> body), name, maxTicks));
    }

    /** Called from the mod constructor. Does nothing unless game tests are enabled for this run. */
    public static void register(IEventBus modEventBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        FUNCTIONS.register(modEventBus);
        ENVIRONMENT_TYPES.register(modEventBus);
        modEventBus.addListener(MnemolithGameTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> suites = event.registerEnvironment(id("suites"));
        Holder<TestEnvironmentDefinition<?>> live = event.registerEnvironment(id("live"), new LivePlayers.Environment());
        for (Spec spec : SPECS) {
            Holder<TestEnvironmentDefinition<?>> environment = switch (spec.environment()) {
                case "suites" -> suites;
                case "live" -> live;
                default -> event.registerEnvironment(id(spec.environment()), new LivePlayers.Environment(true));
            };
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, EMPTY, spec.maxTicks(), 0, true);
            event.registerTest(spec.function().getId(), new FunctionGameTestInstance(spec.function().getKey(), data));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
