package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaLoop.*;
import static com.mnemolith.command.qa.QaMobs.*;
import static com.mnemolith.command.qa.QaSupport.*;
import static com.mnemolith.command.qa.QaWorld.*;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mnemolith.Mnemolith;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith qa}. One dedicated-server pass over the survival loop.
 * It stays off the chunks owned by {@code smoke}, {@code perf}, and {@code mpsmoke}.
 */
public final class MnemolithQa {
    private MnemolithQa() {}

    private static final String[] NAMES = {"writes", "bands", "extract", "formulas", "quietFail", "loudFail", "mute", "lens", "catalog",
            "recipe", "guide", "vein", "pocket", "observatory", "locate", "loot", "strider", "archivist", "replicant"};
    private static int salt = 1;

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        QaReport report = check(source.getLevel(), BlockPos.containing(source.getPosition()));
        int passed = report.passed();
        source.sendSuccess(() -> Component.translatable("mnemolith.command.qa", passed, report.total()), true);
        return passed;
    }

    /** Runs the checklist near {@code origin} and returns its report. Shared by the command and the game test. */
    public static QaReport check(ServerLevel level, BlockPos origin) {
        salt++;
        int chunkX = (origin.getX() >> 4) + 12 + salt * 8;
        int chunkZ = (origin.getZ() >> 4);

        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "MnemolithQa"));
        reset(player);

        boolean writes = writes(level, player, chunkX, chunkZ);
        boolean bands = bands(level, column(level, chunkX + 8, chunkZ));
        boolean extract = extract(level, player, column(level, chunkX + 9, chunkZ));
        boolean formulas = formulas(level, player, column(level, chunkX + 10, chunkZ));
        boolean quietFail = quietFail(level, player, column(level, chunkX + 11, chunkZ));
        boolean loudFail = loudFail(level, player, column(level, chunkX + 12, chunkZ));
        boolean mute = mute(level, column(level, chunkX + 13, chunkZ));
        boolean lens = lens(level, column(level, chunkX + 14, chunkZ));
        boolean catalog = catalog(level, player, column(level, chunkX + 15, chunkZ));
        boolean recipe = recipe(level);
        boolean guide = guide(level);
        boolean vein = vein(level, column(level, chunkX + 16, chunkZ));
        BlockPos observatoryAt = column(level, chunkX + 20, chunkZ);
        boolean pocket = pocket(level, column(level, chunkX + 18, chunkZ));
        boolean observatory = observatory(level, observatoryAt.offset(0, 24, 0));
        boolean locate = locateObservatory(level, origin);
        boolean loot = loot(level);
        BlockPos mobs = column(level, chunkX + 24, chunkZ);
        boolean strider = strider(level, mobs);
        boolean archivist = archivist(level, mobs.offset(2, 0, 0));
        boolean replicant = replicant(level, mobs.offset(-2, 0, 0));

        String dimension = level.dimension().identifier().toString();
        Mnemolith.LOGGER.info(
                "Mnemolith qa writes={} bands={} extract={} formulas={} quietFail={} loudFail={} mute={} lens={} catalog={} recipe={} guide={} vein={} pocket={} observatory={} locate={} loot={} strider={} archivist={} replicant={} dimension={}",
                writes,
                bands,
                extract,
                formulas,
                quietFail,
                loudFail,
                mute,
                lens,
                catalog,
                recipe,
                guide,
                vein,
                pocket,
                observatory,
                locate,
                loot,
                strider,
                archivist,
                replicant,
                dimension);
        boolean[] checks = {writes, bands, extract, formulas, quietFail, loudFail, mute, lens,
                catalog, recipe, guide, vein, pocket, observatory, locate, loot, strider, archivist, replicant};
        return new QaReport("qa", NAMES, checks, java.util.List.of());
    }
}
