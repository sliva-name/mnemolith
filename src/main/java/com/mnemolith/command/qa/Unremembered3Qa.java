package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.config.ServerConfig;
import com.mnemolith.content.item.HollowEffigy;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.boss.Unremembered;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith unremembered3qa}. The Unremembered, stage 3: the four server options (defaults, ranges, EN and RU
 * strings), health scaling per player, scaling applied at full health, participants counting at least one, fighters
 * recorded from player blows and posies but not from creative players, and at least one tear. Two real players are the
 * live test {@code unremembered_scales_and_shares}.
 */
public final class Unremembered3Qa {
    private static final String[] NAMES = {"configEnabled", "configHealth", "configPerPlayer", "configCallForgotten", "configLang",
            "healthFor", "scaleFor", "participantsMin", "fighters", "creativeNotFighter", "tearsMin"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-dddddddddddd");
    private static final UUID CREATIVE = UUID.fromString("66666666-1111-2222-3333-eeeeeeeeeeee");
    private static int salt;

    private Unremembered3Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();

        ok[0] = ServerConfig.UNREMEMBERED_ENABLED.getDefault();
        ok[1] = ServerConfig.UNREMEMBERED_HEALTH.getDefault() == Unremembered.MAX_HEALTH && ServerConfig.UNREMEMBERED_HEALTH.getSpec().test(40.0D)
                && !ServerConfig.UNREMEMBERED_HEALTH.getSpec().test(39.0D) && !ServerConfig.UNREMEMBERED_HEALTH.getSpec().test(2000.0D);
        ok[2] = ServerConfig.UNREMEMBERED_HEALTH_PER_PLAYER.getDefault() == 0.5D && ServerConfig.UNREMEMBERED_HEALTH_PER_PLAYER.getSpec().test(0.0D)
                && !ServerConfig.UNREMEMBERED_HEALTH_PER_PLAYER.getSpec().test(-0.1D);
        ok[3] = ServerConfig.UNREMEMBERED_CALL_FORGOTTEN.getDefault() == HollowEffigy.CALL_FORGOTTEN && ServerConfig.UNREMEMBERED_CALL_FORGOTTEN.getSpec().test(1)
                && !ServerConfig.UNREMEMBERED_CALL_FORGOTTEN.getSpec().test(0);
        boolean lang = true;
        for (String key : List.of("unrememberedEnabled", "unrememberedHealth", "unrememberedHealthPerPlayer", "unrememberedCallForgotten")) {
            lang &= langHas("en_us", "mnemolith.configuration." + key + ".tooltip") && langHas("ru_ru", "mnemolith.configuration." + key + ".tooltip");
        }
        ok[4] = lang && langHas("en_us", "mnemolith.effigy.disabled") && langHas("ru_ru", "mnemolith.effigy.disabled");

        double base = ServerConfig.UNREMEMBERED_HEALTH.get();
        double per = ServerConfig.UNREMEMBERED_HEALTH_PER_PLAYER.get();
        ok[5] = Unremembered.healthFor(1) == base && Math.abs(Unremembered.healthFor(3) - base * (1.0D + 2.0D * per)) < 1.0E-6
                && Unremembered.healthFor(0) == base;
        notes.add("health 1p=" + Unremembered.healthFor(1) + " 2p=" + Unremembered.healthFor(2) + " 3p=" + Unremembered.healthFor(3));

        BlockPos a = column(level, (spawn.getX() >> 4) + 370 + salt * 3, (spawn.getZ() >> 4) - 330);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Unremembered3Qa"));
        FakePlayer creative = FakePlayerFactory.get(level, new GameProfile(CREATIVE, "Unremembered3QaCreative"));
        Unremembered boss = ModEntities.UNREMEMBERED.get().create(level, EntitySpawnReason.COMMAND);
        try {
            if (boss == null) {
                throw new IllegalStateException("unremembered did not create");
            }
            boss.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            boss.setNoAi(true);
            level.addFreshEntity(boss);
            boss.setHealth(10.0F);
            boss.scaleFor(2);
            ok[6] = boss.scaledFor() == 2 && Math.abs(boss.getMaxHealth() - Unremembered.healthFor(2)) < 0.01F && boss.getHealth() == boss.getMaxHealth();
            ok[7] = Unremembered.participants(level, a) >= 1;
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 2.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            boss.hurtServer(level, level.damageSources().playerAttack(player), 2.0F);
            ok[8] = boss.fighters().contains(PLAYER) && boss.fighters().size() == 1;
            creative.setGameMode(GameType.CREATIVE);
            boss.hurtServer(level, level.damageSources().playerAttack(creative), 2.0F);
            ok[9] = !boss.fighters().contains(CREATIVE);
            ok[10] = boss.tearsFor(level) >= 1;
            notes.add("fighters=" + boss.fighters().size() + " tears=" + boss.tearsFor(level) + " max=" + boss.getMaxHealth());
        } finally {
            if (boss != null && !boss.isRemoved()) {
                boss.discard();
            }
            creative.setGameMode(GameType.SURVIVAL);
            releaseColumn(level, ChunkPos.containing(a));
        }
        return new QaReport("unremembered3qa", NAMES, ok, notes);
    }

    private static boolean langHas(String lang, String key) {
        try (var in = Unremembered3Qa.class.getResourceAsStream("/assets/mnemolith/lang/" + lang + ".json")) {
            return in != null && com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().has(key);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }
}
