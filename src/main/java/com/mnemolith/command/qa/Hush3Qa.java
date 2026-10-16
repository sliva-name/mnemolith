package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.config.ServerConfig;
import com.mnemolith.content.block.HushLoomBlock;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Hush;
import com.mnemolith.entity.mob.Hushling;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith hush3qa}. Pale Chorus, stage 3: the server options ({@code hushlingSpawns}, {@code hushTicks},
 * {@code hushlingThinTicks}) with their defaults, ranges and lang; natural End spawns following {@code hushlingSpawns}
 * while eggs still work; the hit reading {@code hushTicks}; the thinning interval reading {@code hushlingThinTicks};
 * both advancements. Two players and a shared loom are the live test {@code hush_personal_loom_shared}.
 */
public final class Hush3Qa {
    private static final String[] NAMES = {"spawnsDefault", "hushTicksSpec", "thinTicksSpec", "lang", "spawnsOff", "eggStillWorks",
            "notOverworld", "hushReadsOption", "thinReadsOption", "advancements"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-ffffffffffff");
    private static int salt;

    private Hush3Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ok[0] = ServerConfig.HUSHLING_SPAWNS.getDefault();
        ok[1] = ServerConfig.HUSH_TICKS.getDefault() == Hushling.HUSH_TICKS && ServerConfig.HUSH_TICKS.getSpec().test(20)
                && !ServerConfig.HUSH_TICKS.getSpec().test(19) && !ServerConfig.HUSH_TICKS.getSpec().test(1201);
        ok[2] = ServerConfig.HUSHLING_THIN_TICKS.getDefault() == Hushling.THIN_PERIOD && ServerConfig.HUSHLING_THIN_TICKS.getSpec().test(40)
                && !ServerConfig.HUSHLING_THIN_TICKS.getSpec().test(39) && !ServerConfig.HUSHLING_THIN_TICKS.getSpec().test(2401);
        boolean lang = langHas("en_us", "mnemolith.configuration.paleChorus") && langHas("ru_ru", "mnemolith.configuration.paleChorus");
        for (String key : List.of("hushlingSpawns", "hushTicks", "hushlingThinTicks")) {
            lang &= langHas("en_us", "mnemolith.configuration." + key + ".tooltip") && langHas("ru_ru", "mnemolith.configuration." + key + ".tooltip");
        }
        for (String adv : List.of("hush_filament", "hush_loom_threaded")) {
            lang &= langHas("en_us", "advancements.mnemolith." + adv + ".title") && langHas("ru_ru", "advancements.mnemolith." + adv + ".description");
        }
        ok[3] = lang;

        // ---------- the spawn rule ----------
        ServerLevel end = server.getLevel(Level.END);
        boolean spawns = ServerConfig.HUSHLING_SPAWNS.get();
        if (end != null) {
            BlockPos e = new BlockPos((spawn.getX() >> 4 << 4) + 7400 + salt * 48, 64, (spawn.getZ() >> 4 << 4) - 6600);
            tickColumn(end, e);
            try {
                ServerConfig.HUSHLING_SPAWNS.set(false);
                ok[4] = !SpawnPlacements.checkSpawnRules(ModEntities.HUSHLING.get(), end, EntitySpawnReason.NATURAL, e, end.getRandom());
                ok[5] = SpawnPlacements.checkSpawnRules(ModEntities.HUSHLING.get(), end, EntitySpawnReason.SPAWN_ITEM_USE, e, end.getRandom());
            } finally {
                ServerConfig.HUSHLING_SPAWNS.set(spawns);
                releaseColumn(end, ChunkPos.containing(e));
            }
        } else {
            notes.add("no end level");
        }

        BlockPos a = column(level, (spawn.getX() >> 4) + 510 + salt * 3, (spawn.getZ() >> 4) - 500);
        tickColumn(level, a);
        int hush = ServerConfig.HUSH_TICKS.get();
        int thin = ServerConfig.HUSHLING_THIN_TICKS.get();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Hush3Qa"));
        Hushling mob = null;
        try {
            ok[6] = !SpawnPlacements.checkSpawnRules(ModEntities.HUSHLING.get(), level, EntitySpawnReason.NATURAL, a, level.getRandom());

            // ---------- the hit and the thinning read the options ----------
            ServerConfig.HUSH_TICKS.set(40);
            ServerConfig.HUSHLING_THIN_TICKS.set(60);
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 1.5D, 0.0F, 0.0F);
            Hush.forget(player.getUUID());
            mob = ModEntities.HUSHLING.get().create(level, EntitySpawnReason.COMMAND);
            if (mob != null) {
                mob.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
                mob.setNoAi(true);
                level.addFreshEntity(mob);
                boolean hit = mob.hushPlayer(level, player);
                long left = Hush.remaining(level, player.getUUID());
                ok[7] = hit && left == 40;
                notes.add("hush left=" + left);
            }
            ok[8] = Hushling.thinPeriod() == 60 && Hushling.hushTicks() == 40;
        } finally {
            ServerConfig.HUSH_TICKS.set(hush);
            ServerConfig.HUSHLING_THIN_TICKS.set(thin);
            if (mob != null) {
                mob.discard();
            }
            Hush.forget(player.getUUID());
            player.removeAllEffects();
            QaSupport.clear(level, a);
            releaseColumn(level, ChunkPos.containing(a));
        }
        ok[9] = server.getAdvancements().get(Identifier.fromNamespaceAndPath(com.mnemolith.Mnemolith.MOD_ID, "hush_filament")) != null
                && server.getAdvancements().get(HushLoomBlock.ADVANCEMENT) != null;
        return new QaReport("hush3qa", NAMES, ok, notes);
    }

    private static boolean langHas(String lang, String key) {
        try (var in = Hush3Qa.class.getResourceAsStream("/assets/mnemolith/lang/" + lang + ".json")) {
            return in != null && com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().has(key);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }
}
