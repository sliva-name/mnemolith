package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.echo.storm.RecollectionStorm;
import com.mnemolith.echo.storm.StormData;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.pressure.PressureBand;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * {@code /mnemolith lullqa}. The storm lull: a spent or contained storm leaves a lull of one Minecraft day; a passed,
 * scarred or faded storm leaves none; the lull covers the storm-spacing range and no further, only in its own
 * dimension, and ends on time; the storm gate reports it on a real fracture; it survives a save; both languages carry
 * the message. Storms parked by the suite are put back.
 */
public final class LullQa {
    private static final String[] NAMES = {"lullDay", "spentLulls", "containedLulls", "othersDont", "range", "dimension",
            "expires", "gate", "saved", "lang", "lineMinutes", "shardRefused", "config"};
    private static int salt;

    private LullQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        StormData data = StormData.get(level.getServer());
        List<RecollectionStorm> parked = data.storms();
        for (RecollectionStorm storm : parked) {
            data.remove(storm);
        }
        int bx = (spawn.getX() >> 4) + 260 + salt * 12;
        int bz = (spawn.getZ() >> 4) - 90;
        ChunkPos[] centres = {new ChunkPos(bx, bz), new ChunkPos(bx + 6, bz), new ChunkPos(bx, bz + 6), new ChunkPos(bx + 6, bz + 6)};
        BlockPos a = null;
        try {
            ok[0] = Storms.LULL_TICKS == 24000L;
            Storms.finish(level, Storms.start(level, centres[0], "qa"), Storms.End.SPENT);
            long spent = Storms.lullLeft(level, centres[0]);
            ok[1] = spent == Storms.lullTicks();
            Storms.finish(level, Storms.start(level, centres[1], "qa"), Storms.End.CONTAINED);
            ok[2] = Storms.lullLeft(level, centres[1]) == Storms.lullTicks();
            Storms.finish(level, Storms.start(level, centres[2], "qa"), Storms.End.PASSED);
            Storms.finish(level, Storms.start(level, centres[3], "qa"), Storms.End.FADED);
            ok[3] = Storms.lullLeft(level, centres[2]) == 0L && Storms.lullLeft(level, centres[3]) == 0L;
            notes.add("spent=" + spent + " passed=" + Storms.lullLeft(level, centres[2]) + " faded=" + Storms.lullLeft(level, centres[3]));

            ChunkPos edge = new ChunkPos(bx - 2 * Storms.AREA, bz + 2 * Storms.AREA);
            ChunkPos past = new ChunkPos(bx - 2 * Storms.AREA - 1, bz);
            ok[4] = Storms.lullLeft(level, edge) > 0L && Storms.lullLeft(level, past) == 0L;

            long now = level.getGameTime();
            ChunkPos nether = new ChunkPos(bx - 40, bz);
            data.addLull(new StormData.Lull(Level.NETHER, nether.x(), nether.z(), now + Storms.LULL_TICKS), now);
            ok[5] = Storms.lullLeft(level, nether) == 0L;

            ChunkPos old = new ChunkPos(bx - 60, bz);
            data.addLull(new StormData.Lull(level.dimension(), old.x(), old.z(), now), now);
            data.addLull(new StormData.Lull(level.dimension(), old.x() + 1, old.z(), now + 5L), now);
            ok[6] = Storms.lullLeft(level, new ChunkPos(old.x() - 2, old.z())) == 0L && Storms.lullLeft(level, old) == 5L;

            // The gate on a real fracture: open, then lulled once a storm there is spent.
            a = column(level, bx - 20, bz - 20);
            tickColumn(level, a);
            ChunkPos ca = ChunkPos.containing(a);
            StormQa.resetArea(level, a);
            PressureBand band = StormQa.loud(level, a, PressureBand.FRACTURE);
            Storms.Gate before = Storms.gate(level, ca);
            if (before == Storms.Gate.OK) {
                Storms.finish(level, Storms.start(level, ca, "qa"), Storms.End.SPENT);
            }
            Storms.Gate after = Storms.gate(level, ca);
            ok[7] = !Storms.enabled() || Storms.maxPerDimension() <= 0 || Storms.lullTicks() <= 0L ? true
                    : band == PressureBand.FRACTURE && before == Storms.Gate.OK && after == Storms.Gate.LULL;
            notes.add("gate band=" + band + " before=" + before + " after=" + after);

            var encoded = StormData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            StormData copy = StormData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            ok[8] = copy.lulls().equals(data.lulls()) && !copy.lulls().isEmpty();

            String en = Component.translatable("mnemolith.storm.lull").getString();
            ok[9] = !en.equals("mnemolith.storm.lull") && langHas("ru_ru", "mnemolith.storm.lull");

            ok[10] = minutes(Storms.lullLine(Storms.LULL_TICKS)) == 20L && minutes(Storms.lullLine(1L)) == 1L
                    && minutes(Storms.lullLine(1201L)) == 2L && minutes(Storms.lullLine(0L)) == 1L
                    && langHas("ru_ru", "mnemolith.storm.lulled") && langHas("en_us", "mnemolith.storm.lulled");
            ok[11] = !Storms.enabled() || Storms.maxPerDimension() <= 0 || Storms.lullTicks() <= 0L
                    ? true : !Storms.callByShard(level, residue(level, a), null) && Storms.at(level, ca) == null;
            ok[12] = com.mnemolith.config.ServerConfig.STORM_LULL_TICKS.getDefault() == 24000
                    && com.mnemolith.config.ServerConfig.STORM_LULL_TICKS.getSpec().test(0)
                    && !com.mnemolith.config.ServerConfig.STORM_LULL_TICKS.getSpec().test(-1)
                    && langHas("en_us", "mnemolith.configuration.stormLullTicks.tooltip") && langHas("ru_ru", "mnemolith.configuration.stormLullTicks.tooltip");
            notes.add("lullTicks=" + Storms.lullTicks());
        } finally {
            int x0 = bx - 64;
            data.removeLulls(lull -> lull.chunkX() >= x0 && lull.chunkX() <= bx + 8 && Math.abs(lull.chunkZ() - bz) <= 26);
            for (RecollectionStorm storm : parked) {
                data.add(storm);
            }
            if (a != null) {
                StormQa.resetArea(level, a);
                releaseColumn(level, ChunkPos.containing(a));
            }
        }
        return new QaReport("lullqa", NAMES, ok, notes);
    }

    private static long minutes(Component line) {
        return line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getArgs().length == 1
                && t.getArgs()[0] instanceof Long n ? n : -1L;
    }

    private static com.mnemolith.entity.echo.ResidueEntity residue(ServerLevel level, BlockPos pos) {
        var residue = com.mnemolith.entity.ModEntities.RESIDUE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (residue == null) {
            throw new IllegalStateException("residue did not create");
        }
        residue.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        return residue;
    }

    private static boolean langHas(String lang, String key) {
        try (var in = LullQa.class.getResourceAsStream("/assets/mnemolith/lang/" + lang + ".json")) {
            return in != null && com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().has(key);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }
}
