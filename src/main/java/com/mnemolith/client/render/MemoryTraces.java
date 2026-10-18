package com.mnemolith.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.network.ChunkPressure;
import com.mnemolith.particle.MemoryTrace;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.pressure.PressureBand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Visible memory, client side: grows {@link MemoryTrace} stains on the ground of nearby loud chunks,
 * read straight from the pressure snapshot {@link PressureClient} already polls. No world mutation,
 * no server work: motes are local particles inside the shared {@code MemoryFxBudget}, so
 * {@code particleDensity} scales them and 0 (or {@code imprintParticles} off) stills them.
 * <p>
 * Traces answer "which band, roughly where, what kind of memory" from the world alone. Standing
 * still in your own chunk doubles its traces at the same {@link MemoryTrace#STILL_TICKS} threshold
 * where after-images pinpoint the loudest origins and after-echoes sound them, so the three cues
 * arrive together instead of duplicating each other; the lens stays the precise instrument.
 * This toggle is deliberately separate from {@code pressureEffectStrength}: killing the screen
 * effects never hides the traces that replace them.
 */
public final class MemoryTraces {
    private static int rounds;
    private static double lastX;
    private static double lastZ;
    private static int stillTicks;

    private MemoryTraces() {}

    /** True while the traces toggle needs the band snapshot, even with every screen effect off. */
    public static boolean wantsSnapshot() {
        return ClientConfig.MEMORY_TRACES.get();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !ClientConfig.MEMORY_TRACES.get()) {
            stillTicks = 0;
            return;
        }
        double dx = player.getX() - lastX;
        double dz = player.getZ() - lastZ;
        stillTicks = dx * dx + dz * dz > 0.05D * 0.05D ? 0 : stillTicks + 1;
        lastX = player.getX();
        lastZ = player.getZ();
        if (++rounds % MemoryTrace.SPAWN_PERIOD != 0) {
            return;
        }
        double density = ClientConfig.PARTICLE_DENSITY.get();
        if (density <= 0.0D) {
            return;
        }
        List<ChunkPressure> snapshot = PressureClient.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        ChunkPos origin = player.chunkPosition();
        List<ChunkPressure> near = new ArrayList<>();
        for (ChunkPressure chunk : snapshot) {
            if (MemoryTrace.style(PressureBand.byOrdinal(chunk.band())).motes() <= 0) {
                continue;
            }
            int chebyshev = Math.max(Math.abs(chunk.chunkX() - origin.x()), Math.abs(chunk.chunkZ() - origin.z()));
            if (chebyshev <= MemoryTrace.RADIUS) {
                near.add(chunk);
            }
        }
        near.sort(Comparator.comparingInt(chunk ->
                Math.max(Math.abs(chunk.chunkX() - origin.x()), Math.abs(chunk.chunkZ() - origin.z()))));
        RandomSource random = player.getRandom();
        int attempts = 0;
        for (ChunkPressure chunk : near) {
            if (attempts >= MemoryTrace.MAX_PER_ROUND) {
                return;
            }
            PressureBand band = PressureBand.byOrdinal(chunk.band());
            MemoryTrace.Style style = MemoryTrace.style(band);
            boolean bloom = stillTicks >= MemoryTrace.STILL_TICKS && chunk.chunkX() == origin.x() && chunk.chunkZ() == origin.z();
            int count = (int) Math.round(style.motes() * density * (bloom ? 2 : 1));
            var tag = MemoryTrace.tag(chunk.loudest());
            MemoryTrace.Pattern pattern = MemoryTrace.pattern(tag);
            ChunkPos pos = new ChunkPos(chunk.chunkX(), chunk.chunkZ());
            for (int i = 0; i < count && attempts < MemoryTrace.MAX_PER_ROUND; i++) {
                attempts++;
                double px;
                double pz;
                double lift = 0.08D;
                boolean seamMote = style.seam() && i % 3 != 2;
                if (seamMote) {
                    // two of every three fracture motes (six of nine) feed the seam, so the crack stays continuous
                    int[] point = MemoryTrace.seamPoint(pos, random.nextDouble(), random);
                    px = point[0] + 0.5D;
                    pz = point[1] + 0.5D;
                    lift = 0.05D;
                } else {
                    double[] point = MemoryTrace.patternPoint(pattern, pos, i % Math.max(1, style.clusters()),
                            random.nextDouble(), random, style.spread());
                    px = point[0];
                    pz = point[1];
                    if (pattern == MemoryTrace.Pattern.WISPS) {
                        lift = 1.25D;
                    } else if (pattern == MemoryTrace.Pattern.EMBERS) {
                        lift = 0.3D;
                    }
                }
                int bx = (int) Math.floor(px);
                int bz = (int) Math.floor(pz);
                if (!level.hasChunk(bx >> 4, bz >> 4)) {
                    continue;
                }
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + lift;
                level.addParticle(ModParticles.MEMORY_TRACE.get(), px, y, pz,
                        chunk.loudest(), band.ordinal(), seamMote ? 1.0D : 0.0D);
            }
        }
    }
}
