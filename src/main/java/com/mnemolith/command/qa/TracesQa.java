package com.mnemolith.command.qa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mnemolith.common.MemoryGlyphs;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.particle.AfterEcho;
import com.mnemolith.particle.MemoryTrace;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.pressure.PressureBand;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

/**
 * {@code /mnemolith tracesqa}. Visible memory: the band-to-trace mapping the client grows from the pressure
 * snapshot. Calm grows nothing; motes, size, life, alpha, cluster count and spread rise strictly with the band;
 * only fracture lays a seam; the seam and the cluster centres are deterministic per chunk and stay inside it;
 * every imprint kind has its glyph colour, an after-echo motion and a silhouette (explosion ring, path trail,
 * build outline, fire embers, death wisps); ring and outline points really follow those lines; a band-only
 * snapshot (no loudest) falls back to the pale after-image colour; the particle type and the client toggle's
 * RU/EN lang exist; the mapping stays cheap. Rendering itself is checked in the visual tour ({@code traces_*}
 * scenes, bands and kinds).
 */
public final class TracesQa {
    private static final String[] NAMES = {"calmEmpty", "monotonic", "fractureSeamOnly", "seamStableInside",
            "coloursPerKind", "motionsPerKind", "bandOnlyFallback", "particle", "lang", "cheap",
            "patternsPerKind", "clusterStable", "silhouetteShapes"};

    private TracesQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();

        ok[0] = MemoryTrace.style(PressureBand.CALM).motes() == 0;

        MemoryTrace.Style saturated = MemoryTrace.style(PressureBand.SATURATED);
        MemoryTrace.Style overloaded = MemoryTrace.style(PressureBand.OVERLOADED);
        MemoryTrace.Style fracture = MemoryTrace.style(PressureBand.FRACTURE);
        ok[1] = saturated.motes() > 0
                && saturated.motes() < overloaded.motes() && overloaded.motes() < fracture.motes()
                && saturated.size() < overloaded.size() && overloaded.size() < fracture.size()
                && saturated.life() < overloaded.life() && overloaded.life() < fracture.life()
                && saturated.alpha() < overloaded.alpha() && overloaded.alpha() <= fracture.alpha()
                && saturated.rise() < overloaded.rise() && overloaded.rise() < fracture.rise()
                && saturated.clusters() < overloaded.clusters() && overloaded.clusters() < fracture.clusters()
                && saturated.spread() < overloaded.spread() && overloaded.spread() < fracture.spread();

        ok[2] = fracture.seam() && !saturated.seam() && !overloaded.seam() && !MemoryTrace.style(PressureBand.CALM).seam();

        RandomSource random = level.getRandom();
        boolean seam = true;
        for (int cx = -3; cx <= 3 && seam; cx++) {
            for (int cz = -3; cz <= 3 && seam; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                int[] first = MemoryTrace.seamPoint(chunk, 0.37D, RandomSource.create(42L));
                int[] again = MemoryTrace.seamPoint(chunk, 0.37D, RandomSource.create(42L));
                seam &= first[0] == again[0] && first[1] == again[1];
                for (double t = 0.0D; t <= 1.0D; t += 0.1D) {
                    int[] point = MemoryTrace.seamPoint(chunk, t, random);
                    seam &= point[0] >= chunk.getMinBlockX() && point[0] <= chunk.getMaxBlockX()
                            && point[1] >= chunk.getMinBlockZ() && point[1] <= chunk.getMaxBlockZ();
                }
            }
        }
        ok[3] = seam;

        Set<Integer> colours = new HashSet<>();
        boolean perKind = true;
        for (ImprintTag tag : ImprintTag.values()) {
            perKind &= MemoryTrace.colour(tag.ordinal()) == MemoryGlyphs.colour(tag);
            perKind &= MemoryTrace.tag(tag.ordinal()) == tag;
            colours.add(MemoryTrace.colour(tag.ordinal()));
        }
        ok[4] = perKind && colours.size() >= 6;
        notes.add("colours=" + colours.size());

        boolean motions = true;
        Set<AfterEcho.Motion> seen = new HashSet<>();
        for (ImprintTag tag : ImprintTag.values()) {
            motions &= AfterEcho.motionFor(tag) != null;
            seen.add(AfterEcho.motionFor(tag));
        }
        ok[5] = motions && seen.size() == AfterEcho.Motion.values().length;

        ok[6] = MemoryTrace.colour(-1) == com.mnemolith.common.MemoryPalette.AFTERIMAGE
                && MemoryTrace.colour(999) == com.mnemolith.common.MemoryPalette.AFTERIMAGE
                && MemoryTrace.tag(-1) == null && MemoryTrace.tag(999) == null;

        ok[7] = BuiltInRegistries.PARTICLE_TYPE.containsKey(
                Identifier.fromNamespaceAndPath("mnemolith", "memory_trace")) && ModParticles.MEMORY_TRACE.isBound();

        boolean lang = true;
        for (String l : new String[] {"en_us", "ru_ru"}) {
            lang &= Feel3Qa.langHasPub(l, "mnemolith.configuration.memoryTraces")
                    && Feel3Qa.langHasPub(l, "mnemolith.configuration.memoryTraces.tooltip");
        }
        ok[8] = lang;

        long start = System.nanoTime();
        int sink = 0;
        for (int i = 0; i < 20_000; i++) {
            sink += MemoryTrace.style(PressureBand.byOrdinal(i & 3)).motes();
            sink += MemoryTrace.seamPoint(new ChunkPos(i, -i), 0.5D, random)[0];
            sink += (int) MemoryTrace.patternPoint(MemoryTrace.Pattern.RING, new ChunkPos(i, -i), 0, 0.5D, random, 2.4D)[0];
        }
        long mean = (System.nanoTime() - start) / 20_000L;
        ok[9] = mean < 20_000L && sink != Integer.MIN_VALUE;
        notes.add("mappingNs=" + mean);

        boolean patterns = MemoryTrace.pattern(ImprintTag.EXPLOSION) == MemoryTrace.Pattern.RING
                && MemoryTrace.pattern(ImprintTag.PATH) == MemoryTrace.Pattern.TRAIL
                && MemoryTrace.pattern(ImprintTag.BUILD) == MemoryTrace.Pattern.OUTLINE
                && MemoryTrace.pattern(ImprintTag.FIRE) == MemoryTrace.Pattern.EMBERS
                && MemoryTrace.pattern(ImprintTag.DEATH) == MemoryTrace.Pattern.WISPS
                && MemoryTrace.pattern(null) == MemoryTrace.Pattern.PATINA;
        for (ImprintTag tag : ImprintTag.values()) {
            patterns &= MemoryTrace.pattern(tag) != null;
        }
        ok[10] = patterns;

        boolean clusters = true;
        ChunkPos probe = new ChunkPos(2, -3);
        for (int index = 0; index < 3; index++) {
            double[] center = MemoryTrace.clusterCenter(probe, index);
            double[] again = MemoryTrace.clusterCenter(probe, index);
            clusters &= center[0] == again[0] && center[1] == again[1]
                    && center[0] > probe.getMinBlockX() + 2 && center[0] < probe.getMaxBlockX() - 2
                    && center[1] > probe.getMinBlockZ() + 2 && center[1] < probe.getMaxBlockZ() - 2;
        }
        double[] c0 = MemoryTrace.clusterCenter(probe, 0);
        double[] c1 = MemoryTrace.clusterCenter(probe, 1);
        clusters &= c0[0] != c1[0] || c0[1] != c1[1];
        ok[11] = clusters;

        boolean shapes = true;
        double[] center = MemoryTrace.clusterCenter(probe, 0);
        for (double t = 0.0D; t < 1.0D; t += 0.05D) {
            double[] ring = MemoryTrace.patternPoint(MemoryTrace.Pattern.RING, probe, 0, t, RandomSource.create(7L), 2.4D);
            double dist = Math.hypot(ring[0] - center[0], ring[1] - center[1]);
            shapes &= dist > 1.8D && dist < 3.4D;
            double[] outline = MemoryTrace.patternPoint(MemoryTrace.Pattern.OUTLINE, probe, 0, t, RandomSource.create(7L), 2.4D);
            double norm = Math.max(Math.abs(outline[0] - center[0]), Math.abs(outline[1] - center[1]));
            shapes &= norm > 1.5D && norm < 2.6D;
            double[] trail = MemoryTrace.patternPoint(MemoryTrace.Pattern.TRAIL, probe, 0, t, RandomSource.create(7L), 2.4D);
            shapes &= trail[0] >= probe.getMinBlockX() && trail[0] <= probe.getMaxBlockX() + 1
                    && trail[1] >= probe.getMinBlockZ() && trail[1] <= probe.getMaxBlockZ() + 1;
        }
        ok[12] = shapes;

        return new QaReport("tracesqa", NAMES, ok, notes);
    }
}
