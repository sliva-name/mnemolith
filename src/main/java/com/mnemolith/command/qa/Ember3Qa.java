package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.config.ServerConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.EmberBrazierBlock;
import com.mnemolith.entity.ModEntities;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code /mnemolith ember3qa}. Embers, stage 3: the server options ({@code cinderkinSpawns},
 * {@code emberBrazierBurnTicks}, {@code emberBrazierChargePerEmber}) with their ranges and lang, the spawn rule obeying
 * {@code cinderkinSpawns} while eggs still work, the brazier taking its charge per ember and burn period from the
 * options, and the two advancements. Two real players sharing a brazier are the live test {@code brazier_shared}.
 */
public final class Ember3Qa {
    private static final String[] NAMES = {"spawnsOption", "burnTicksOption", "chargeOption", "lang", "spawnRuleOff", "eggStillWorks",
            "overworldNever", "chargeFromConfig", "periodFromConfig", "advancements"};
    private static int salt;

    private Ember3Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ok[0] = ServerConfig.CINDERKIN_SPAWNS.getDefault();
        ok[1] = ServerConfig.EMBER_BRAZIER_BURN_TICKS.getDefault() == EmberBrazierBlock.BURN_PERIOD && ServerConfig.EMBER_BRAZIER_BURN_TICKS.getSpec().test(20)
                && !ServerConfig.EMBER_BRAZIER_BURN_TICKS.getSpec().test(19) && !ServerConfig.EMBER_BRAZIER_BURN_TICKS.getSpec().test(1201);
        ok[2] = ServerConfig.EMBER_BRAZIER_CHARGE_PER_EMBER.getDefault() == EmberBrazierBlock.CHARGE_PER_EMBER
                && ServerConfig.EMBER_BRAZIER_CHARGE_PER_EMBER.getSpec().test(EmberBrazierBlock.MAX_CHARGE)
                && !ServerConfig.EMBER_BRAZIER_CHARGE_PER_EMBER.getSpec().test(0) && !ServerConfig.EMBER_BRAZIER_CHARGE_PER_EMBER.getSpec().test(17);
        boolean lang = langHas("en_us", "mnemolith.configuration.embers") && langHas("ru_ru", "mnemolith.configuration.embers");
        for (String key : List.of("cinderkinSpawns", "emberBrazierBurnTicks", "emberBrazierChargePerEmber")) {
            lang &= langHas("en_us", "mnemolith.configuration." + key + ".tooltip") && langHas("ru_ru", "mnemolith.configuration." + key + ".tooltip");
        }
        for (String adv : List.of("memory_ember", "ember_brazier_kindled")) {
            lang &= langHas("en_us", "advancements.mnemolith." + adv + ".title") && langHas("ru_ru", "advancements.mnemolith." + adv + ".description");
        }
        ok[3] = lang;

        // ---------- the spawn rule ----------
        ServerLevel nether = server.getLevel(Level.NETHER);
        boolean spawns = ServerConfig.CINDERKIN_SPAWNS.get();
        if (nether != null) {
            BlockPos n = new BlockPos((spawn.getX() >> 4 << 4) + 6600 + salt * 48, 64, (spawn.getZ() >> 4 << 4) - 5900);
            tickColumn(nether, n);
            try {
                ServerConfig.CINDERKIN_SPAWNS.set(false);
                ok[4] = !SpawnPlacements.checkSpawnRules(ModEntities.CINDERKIN.get(), nether, EntitySpawnReason.NATURAL, n, nether.getRandom());
                ok[5] = SpawnPlacements.checkSpawnRules(ModEntities.CINDERKIN.get(), nether, EntitySpawnReason.SPAWN_ITEM_USE, n, nether.getRandom());
            } finally {
                ServerConfig.CINDERKIN_SPAWNS.set(spawns);
                releaseColumn(nether, ChunkPos.containing(n));
            }
        } else {
            notes.add("no nether level");
        }

        BlockPos a = column(level, (spawn.getX() >> 4) + 430 + salt * 3, (spawn.getZ() >> 4) - 420);
        tickColumn(level, a);
        int charge = ServerConfig.EMBER_BRAZIER_CHARGE_PER_EMBER.get();
        int ticks = ServerConfig.EMBER_BRAZIER_BURN_TICKS.get();
        try {
            ok[6] = !SpawnPlacements.checkSpawnRules(ModEntities.CINDERKIN.get(), level, EntitySpawnReason.NATURAL, a, level.getRandom());

            // ---------- the brazier reads the options ----------
            ServerConfig.EMBER_BRAZIER_CHARGE_PER_EMBER.set(8);
            ServerConfig.EMBER_BRAZIER_BURN_TICKS.set(40);
            level.setBlock(a, ModBlocks.EMBER_BRAZIER.get().defaultBlockState(), 3);
            boolean first = EmberBrazierBlock.feed(level, a, level.getBlockState(a));
            boolean second = EmberBrazierBlock.feed(level, a, level.getBlockState(a));
            boolean third = EmberBrazierBlock.feed(level, a, level.getBlockState(a));
            int got = level.getBlockState(a).getValue(EmberBrazierBlock.CHARGE);
            ok[7] = first && second && !third && got == 16;
            ok[8] = EmberBrazierBlock.burnPeriod() == 40 && level.getBlockTicks().hasScheduledTick(a, ModBlocks.EMBER_BRAZIER.get());
            notes.add("charge8 feeds=" + first + "/" + second + "/" + third + " charge=" + got);
        } finally {
            ServerConfig.EMBER_BRAZIER_CHARGE_PER_EMBER.set(charge);
            ServerConfig.EMBER_BRAZIER_BURN_TICKS.set(ticks);
            level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
            QaSupport.clear(level, a);
            releaseColumn(level, ChunkPos.containing(a));
        }
        ok[9] = server.getAdvancements().get(Identifier.fromNamespaceAndPath(com.mnemolith.Mnemolith.MOD_ID, "memory_ember")) != null
                && server.getAdvancements().get(EmberBrazierBlock.ADVANCEMENT) != null;
        return new QaReport("ember3qa", NAMES, ok, notes);
    }

    private static boolean langHas(String lang, String key) {
        try (var in = Ember3Qa.class.getResourceAsStream("/assets/mnemolith/lang/" + lang + ".json")) {
            return in != null && com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().has(key);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }
}
