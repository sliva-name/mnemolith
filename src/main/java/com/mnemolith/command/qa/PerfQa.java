package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.echo.storm.ScarSites;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowLanterns;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code /mnemolith perfqa}. Per-call time budgets for the server-side checks added since 0.4.0 that run on ticks or
 * per entity: the storm gate and lull lookup (once a second per player in a fracture), the memorial calm check (per
 * residue every 100 ticks and at each fester, with and without a memorial in reach), and the lantern guard (on
 * forgetting and flickers). Each runs {@link #CALLS} times after a warm-up; the mean must stay under its budget. The
 * budgets are about ten times the means seen locally, so they catch a regression (a scan that starts loading chunks,
 * walking whole sections every call, ...) and not machine noise. Means are logged for {@code /mnemolith perf}-style
 * tracking.
 */
public final class PerfQa {
    private static final String[] NAMES = {"stormGate", "lullLookup", "calmNone", "calmMemorial", "lanternGuard", "lanternFound"};
    static final int CALLS = 2000;
    /** Budgets in nanoseconds per call. */
    static final long[] BUDGET_NS = {50_000L, 20_000L, 50_000L, 400_000L, 50_000L, 400_000L};
    private static int salt;

    private PerfQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        BlockPos a = column(level, (spawn.getX() >> 4) + 310 + salt * 3, (spawn.getZ() >> 4) - 240);
        BlockPos b = column(level, (spawn.getX() >> 4) + 310 + salt * 3, (spawn.getZ() >> 4) - 236);
        tickColumn(level, a);
        tickColumn(level, b);
        ChunkPos ca = ChunkPos.containing(a);
        long[] mean = new long[NAMES.length];
        try {
            LoadedChunkMemory.clear(level.getChunkAt(a));
            LoadedChunkMemory.clear(level.getChunkAt(b));
            mean[0] = time(() -> Storms.gate(level, ca) != null);
            mean[1] = time(() -> Storms.lullLeft(level, ca) >= 0L);
            BlockPos probe = a.offset(3, 0, 0);
            mean[2] = time(() -> !ScarSites.nearMemorial(level, probe));
            level.setBlock(b, ModBlocks.MEMORIAL_STONE.get().defaultBlockState(), 3);
            LoadedChunkMemory.getOrCreate(level.getChunkAt(b)).setScar(new ScarSite(1, level.getGameTime(), 3, level.getGameTime(), true));
            BlockPos near = b.offset(3, 0, 0);
            boolean calmed = ScarSites.nearMemorial(level, near);
            mean[3] = time(() -> ScarSites.nearMemorial(level, near));
            mean[4] = time(() -> !HollowLanterns.guarded(level, probe));
            // A lantern 5 blocks off, in the next chunk and section edge cases aside: found, and still in budget.
            BlockPos lamp = probe.offset(5, 2, -5);
            level.setBlock(lamp, ModBlocks.RECOLLITE_LANTERN.get().defaultBlockState(), 3);
            mean[5] = time(() -> HollowLanterns.guarded(level, probe));
            level.setBlock(lamp, Blocks.AIR.defaultBlockState(), 3);
            for (int i = 0; i < NAMES.length; i++) {
                ok[i] = mean[i] <= BUDGET_NS[i] && (i != 3 || calmed);
                notes.add(NAMES[i] + "=" + mean[i] + "ns/" + BUDGET_NS[i]);
            }
        } finally {
            level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            LoadedChunkMemory.clear(level.getChunkAt(a));
            LoadedChunkMemory.clear(level.getChunkAt(b));
            releaseColumn(level, ca);
            releaseColumn(level, ChunkPos.containing(b));
        }
        Mnemolith.LOGGER.info("Mnemolith perfqa {}", String.join(" ", notes));
        return new QaReport("perfqa", NAMES, ok, notes);
    }

    /** Mean nanoseconds per call over {@link #CALLS} calls, after {@code CALLS / 4} warm-up calls. False results are counted as a failure (-1). */
    private static long time(BooleanSupplier call) {
        boolean fine = true;
        for (int i = 0; i < CALLS / 4; i++) {
            fine &= call.getAsBoolean();
        }
        long start = System.nanoTime();
        for (int i = 0; i < CALLS; i++) {
            fine &= call.getAsBoolean();
        }
        long mean = (System.nanoTime() - start) / CALLS;
        return fine ? mean : Long.MAX_VALUE;
    }
}
