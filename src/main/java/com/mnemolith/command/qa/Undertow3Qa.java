package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.config.ServerConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.fishing.Angling;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith undertow3qa}. Undertow, stage 3: the server options ({@code anglingChance}, {@code tideLureBonus})
 * with defaults, ranges and lang; the chance reading them (0 turns angling off, totals cap at 1); both advancements
 * load. Two anglers on one chunk are the live test {@code two_anglers_one_chunk}.
 */
public final class Undertow3Qa {
    private static final String[] NAMES = {"chanceSpec", "lureSpec", "lang", "readsOptions", "offTakesNothing", "capsAtOne", "advancements"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-ffffffffffff");
    private static int salt;

    private Undertow3Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ok[0] = Math.abs(ServerConfig.ANGLING_CHANCE.getDefault() - Angling.BASE_CHANCE) < 1.0E-6 && ServerConfig.ANGLING_CHANCE.getSpec().test(0.0D)
                && ServerConfig.ANGLING_CHANCE.getSpec().test(1.0D) && !ServerConfig.ANGLING_CHANCE.getSpec().test(1.01D) && !ServerConfig.ANGLING_CHANCE.getSpec().test(-0.01D);
        ok[1] = Math.abs(ServerConfig.TIDE_LURE_BONUS.getDefault() - Angling.LURE_BONUS) < 1.0E-6 && ServerConfig.TIDE_LURE_BONUS.getSpec().test(1.0D)
                && !ServerConfig.TIDE_LURE_BONUS.getSpec().test(1.01D);
        boolean lang = langHas("en_us", "mnemolith.configuration.undertow") && langHas("ru_ru", "mnemolith.configuration.undertow");
        for (String key : List.of("anglingChance", "tideLureBonus")) {
            lang &= langHas("en_us", "mnemolith.configuration." + key + ".tooltip") && langHas("ru_ru", "mnemolith.configuration." + key + ".tooltip");
        }
        for (String adv : List.of("undertow_landed", "tide_lure_landed")) {
            lang &= langHas("en_us", "advancements.mnemolith." + adv + ".title") && langHas("ru_ru", "advancements.mnemolith." + adv + ".description");
        }
        ok[2] = lang;

        double chance = ServerConfig.ANGLING_CHANCE.get();
        double bonus = ServerConfig.TIDE_LURE_BONUS.get();
        BlockPos a = column(level, (spawn.getX() >> 4) + 570 + salt * 3, (spawn.getZ() >> 4) - 560);
        tickColumn(level, a);
        BlockPos water = a.offset(2, 0, 2);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Undertow3Qa"));
        net.minecraft.world.entity.projectile.FishingHook hook = null;
        try {
            ServerConfig.ANGLING_CHANCE.set(0.5D);
            ServerConfig.TIDE_LURE_BONUS.set(0.4D);
            ok[3] = near(Angling.chanceFor(0), 0.5F) && near(Angling.chanceFor(0, true), 0.9F) && near(Angling.chanceFor(1, false), 0.6F);
            ok[5] = near(Angling.chanceFor(3, true), 1.0F) && near(Angling.chanceFor(9, false), 0.8F);

            ServerConfig.ANGLING_CHANCE.set(0.0D);
            ServerConfig.TIDE_LURE_BONUS.set(0.0D);
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() - 3.5D, 0.0F, 0.0F);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(ModItems.TIDE_LURE.get()));
            level.setBlock(water, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
            hook = new net.minecraft.world.entity.projectile.FishingHook(player, level, 0, 0);
            hook.setPos(water.getX() + 0.5D, water.getY() + 0.4D, water.getZ() + 0.5D);
            level.addFreshEntity(hook);
            QaSupport.clear(level, water);
            com.mnemolith.imprint.ImprintWriter.write(level, water, List.of(com.mnemolith.imprint.ImprintTag.BUILD), null, false);
            net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7L);
            int hits = 0;
            for (int i = 0; i < 50; i++) {
                if (Angling.onCatch(level, hook, player, random).isPresent()) {
                    hits++;
                }
            }
            ok[4] = hits == 0 && near(Angling.chance(level, player), 0.0F);
            notes.add("off hits=" + hits);
        } finally {
            ServerConfig.ANGLING_CHANCE.set(chance);
            ServerConfig.TIDE_LURE_BONUS.set(bonus);
            if (hook != null) {
                hook.discard();
            }
            level.setBlock(water, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            QaSupport.clear(level, water);
            player.getInventory().clearContent();
            releaseColumn(level, ChunkPos.containing(a));
        }
        ok[6] = server.getAdvancements().get(Angling.ADVANCEMENT) != null && server.getAdvancements().get(Angling.LURE_ADVANCEMENT) != null;
        return new QaReport("undertow3qa", NAMES, ok, notes);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.0E-4F;
    }

    private static boolean langHas(String lang, String key) {
        try (var in = Undertow3Qa.class.getResourceAsStream("/assets/mnemolith/lang/" + lang + ".json")) {
            return in != null && com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().has(key);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }
}
