package com.mnemolith.echo.storm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.config.ServerConfig;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Recollection storms. A chunk at the fracture band can give way all at once: the storm gathers for 10 seconds (a
 * warning: a boss bar, a darkened sky, a low pulse), then rages for six waves of 10 seconds over the 3x3 chunks around
 * it. Each wave condenses the area's loudest graftable memories into storm-born residues (at most six at a time), and
 * every storm residue acts its memory out together. When the last wave passes with three or more storm residues still
 * standing, they merge into the Scar (a boss) and leave a Scar site behind. Every rule lives here; the server tick,
 * the player tick and the residual shard call in.
 * <p>
 * The player's choices: <b>prepare</b> (drain the loud imprints with the needle so there is nothing to condense; place
 * scar glass, which keeps storms away), <b>contain</b> (a mute stone in the centre chunk while it gathers stops it;
 * while it rages each wave in a muted centre costs the storm two waves, and muted residues starve), <b>exploit</b>
 * (read storm residues with the lens and capture them as shards; or call a storm yourself with a shard in a fractured
 * chunk to farm them, or to raise the Scar on purpose), <b>use echoes</b> (a hushed echo swallows act-outs near it, an
 * echo of the same temper drinks a residue each wave, a grave echo draws the raised dead), or <b>flee</b>. Server
 * authoritative; the only thing clients get is vanilla boss bars, particles, sounds and messages.
 */
public final class Storms {
    /** Chebyshev radius, in chunks, of a storm's area (1 = 3x3 chunks). */
    public static final int AREA = 1;
    public static final int GATHER_TICKS = 200;
    public static final int WAVE_TICKS = 200;
    public static final int WAVES = 6;
    /** Most storm-born residues alive at once in one storm. */
    public static final int MAX_RESIDUES = 6;
    /** Storm residues still standing after the last wave that merge into the Scar. */
    public static final int SCAR_MERGE = 3;
    /** Blocks from the centre within which players see the boss bar and get the storm's messages. */
    public static final double BAR_RANGE = 48.0D;
    /** How often (ticks) a player standing in a fractured chunk rolls {@code stormAttemptChance}. */
    public static final int ATTEMPT_TICKS = 20;

    private static final Map<Long, ServerBossEvent> BARS = new HashMap<>();

    /** Why a storm may or may not gather in a chunk. */
    public enum Gate { OK, DISABLED, CAP, CALM, MUTED, WARDED, BUSY }

    /** How a storm ended. */
    public enum End { CONTAINED, SPENT, PASSED, SCAR, DISABLED }

    /**
     * Game tests only: while set, standing in a fracture never starts a storm (the residue live tests keep fractures
     * for minutes and must not be interrupted by one). Calls by shard still work.
     */
    private static volatile boolean naturalPaused;

    private Storms() {}

    public static void pauseNatural(boolean paused) {
        naturalPaused = paused;
    }

    public static boolean enabled() {
        return ServerConfig.ALLOW_RECOLLECTION_STORMS.get();
    }

    public static int maxPerDimension() {
        return ServerConfig.MAX_STORMS_PER_DIMENSION.get();
    }

    // ---- starting ----

    /** Whether a storm may gather with its centre in {@code chunk}. Never loads chunks. */
    public static Gate gate(ServerLevel level, ChunkPos chunk) {
        if (!enabled()) {
            return Gate.DISABLED;
        }
        StormData data = StormData.get(level.getServer());
        if (data.count(level.dimension()) >= maxPerDimension()) {
            return Gate.CAP;
        }
        if (!level.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
            return Gate.CALM;
        }
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunk(chunk.x(), chunk.z()));
        if (memory == null || MemoryPressure.band(memory.cachedPressure()) != PressureBand.FRACTURE) {
            return Gate.CALM;
        }
        BlockPos middle = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        if (LoadedChunkMemory.isMuted(level, middle)) {
            return Gate.MUTED;
        }
        if (LoadedChunkMemory.stormProof(level, middle)) {
            return Gate.WARDED;
        }
        for (RecollectionStorm storm : data.storms()) {
            if (storm.dimension().equals(level.dimension()) && Math.abs(storm.center().x() - chunk.x()) <= 2 * AREA
                    && Math.abs(storm.center().z() - chunk.z()) <= 2 * AREA) {
                return Gate.BUSY;
            }
        }
        return Gate.OK;
    }

    /**
     * Player tick: once a second a player standing in a fractured chunk rolls {@code stormAttemptChance} (0.02 by
     * default, so a storm usually gathers within a minute or two of standing in a fracture).
     */
    public static void onPlayerTick(ServerLevel level, ServerPlayer player) {
        if (naturalPaused || player.isSpectator() || (player.tickCount + player.getId()) % ATTEMPT_TICKS != 0) {
            return;
        }
        double chance = CommonConfig.STORM_ATTEMPT_CHANCE.get();
        if (chance <= 0.0D || !enabled()) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || MemoryPressure.band(memory.cachedPressure()) != PressureBand.FRACTURE || level.getRandom().nextDouble() >= chance) {
            return;
        }
        if (gate(level, chunk.getPos()) == Gate.OK) {
            start(level, chunk.getPos(), "natural");
        }
    }

    /**
     * A residual shard was just set free: in a fractured chunk (if a storm may gather there) it calls the storm at once,
     * and the released residue is its first storm residue. True when it did.
     */
    public static boolean callByShard(ServerLevel level, ResidueEntity residue, @Nullable ServerPlayer player) {
        ChunkPos chunk = ChunkPos.containing(residue.blockPosition());
        Gate gate = gate(level, chunk);
        if (gate != Gate.OK) {
            return false;
        }
        RecollectionStorm storm = start(level, chunk, "shard");
        adopt(storm, residue);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("mnemolith.storm.called"), true);
        }
        return true;
    }

    /** Starts a storm gathering at {@code chunk} without checking the gate (the QA uses it; callers check first). */
    public static RecollectionStorm start(ServerLevel level, ChunkPos chunk, String cause) {
        StormData data = StormData.get(level.getServer());
        RecollectionStorm storm = new RecollectionStorm(data.takeId(), level.dimension(), chunk, cause);
        data.add(storm);
        BlockPos middle = surface(level, storm);
        level.playSound(null, middle, ModSounds.PRESSURE_WARN.get(), SoundSource.HOSTILE, 3.0F, 0.5F);
        tell(level, storm, "mnemolith.storm.gathering", true);
        updateBar(level, storm);
        Mnemolith.LOGGER.info("Mnemolith storm gathering id={} chunk {} {} cause={}", storm.id(), chunk.x(), chunk.z(), cause);
        return storm;
    }

    public static void adopt(RecollectionStorm storm, ResidueEntity residue) {
        residue.setStorm(storm.id());
        if (!storm.residues().contains(residue.getUUID())) {
            storm.residues().add(residue.getUUID());
        }
    }

    // ---- ticking ----

    /** Server tick (post): advances every active storm. Only the storms themselves are iterated. */
    public static void tick(MinecraftServer server) {
        StormData data = StormData.get(server);
        List<RecollectionStorm> storms = data.storms();
        if (storms.isEmpty()) {
            return;
        }
        for (RecollectionStorm storm : storms) {
            ServerLevel level = server.getLevel(storm.dimension());
            if (level == null) {
                data.remove(storm);
                dropBar(storm);
                continue;
            }
            if (!enabled()) {
                finish(level, storm, End.DISABLED);
                continue;
            }
            if (!level.getChunkSource().hasChunk(storm.center().x(), storm.center().z())) {
                // Nobody near: the storm waits, frozen, until its centre loads again.
                ServerBossEvent bar = BARS.get(storm.id());
                if (bar != null) {
                    bar.removeAllPlayers();
                }
                continue;
            }
            step(level, data, storm);
        }
    }

    /** One tick of one storm. Public for the QA, which drives a storm tick by tick. */
    public static void step(ServerLevel level, StormData data, RecollectionStorm storm) {
        storm.tick();
        if (storm.ticks() % 20 == 0) {
            updateBar(level, storm);
            data.setDirty();
        }
        if (storm.phase() == RecollectionStorm.Phase.GATHERING) {
            if (storm.ticks() % 20 == 0) {
                BlockPos middle = surface(level, storm);
                if (LoadedChunkMemory.isMuted(level, middle) || LoadedChunkMemory.stormProof(level, middle)) {
                    finish(level, storm, End.CONTAINED);
                    return;
                }
                ring(level, storm, middle);
            }
            if (storm.ticks() >= GATHER_TICKS) {
                storm.setPhase(RecollectionStorm.Phase.RAGING);
                tell(level, storm, "mnemolith.storm.raging", true);
                level.playSound(null, surface(level, storm), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.0F, 0.4F);
                Mnemolith.LOGGER.info("Mnemolith storm raging id={}", storm.id());
                wave(level, data, storm);
            }
            return;
        }
        if (storm.ticks() % WAVE_TICKS == 0) {
            wave(level, data, storm);
        }
    }

    /** What one wave did (the QA reads it). */
    public record Wave(int condensed, int acted, int held, int starved, int fed, int hushed, boolean choked, int standing) {}

    private static @Nullable Wave lastWave;

    public static @Nullable Wave lastWave() {
        return lastWave;
    }

    /** How the last storm to end ended (the QA reads it). */
    public record Ended(long id, End end) {}

    private static @Nullable Ended lastEnd;

    public static @Nullable Ended lastEnd() {
        return lastEnd;
    }

    /**
     * One wave: a muted centre chokes the storm (two waves spent); the area's loudest graftable memories condense into
     * storm residues up to {@link #MAX_RESIDUES}; then every storm residue has its turn (muted starve, a matching echo
     * drinks, a read one holds still, the rest act out unless a hushed echo swallows it). A storm with nothing standing
     * and nothing left to condense is spent; after the last wave three or more standing merge into the Scar.
     */
    public static void wave(ServerLevel level, StormData data, RecollectionStorm storm) {
        BlockPos middle = surface(level, storm);
        boolean choked = LoadedChunkMemory.isMuted(level, middle);
        storm.spendWaves(choked ? 2 : 1);
        List<ResidueEntity> living = living(level, storm);
        int condensed = condenseArea(level, storm, MAX_RESIDUES - living.size());
        living = living(level, storm);
        int acted = 0;
        int held = 0;
        int starved = 0;
        int fed = 0;
        int hushed = 0;
        for (ResidueEntity residue : living) {
            switch (Residues.stormWave(level, residue)) {
                case ACTED -> acted++;
                case HELD -> held++;
                case STARVED, DISSOLVED -> starved++;
                case FED -> fed++;
                case HUSHED -> hushed++;
                default -> {}
            }
        }
        living = living(level, storm);
        lastWave = new Wave(condensed, acted, held, starved, fed, hushed, choked, living.size());
        level.playSound(null, middle, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.5F, 0.5F + 0.05F * storm.wavesLeft());
        level.sendParticles(ModParticles.PRESSURE_WARN.get(), middle.getX() + 0.5D, middle.getY() + 2.0D, middle.getZ() + 0.5D, 30, 6.0D, 2.0D, 6.0D, 0.02D);
        Mnemolith.LOGGER.info("Mnemolith storm wave id={} left={} condensed={} acted={} held={} starved={} fed={} hushed={} choked={} standing={}",
                storm.id(), storm.wavesLeft(), condensed, acted, held, starved, fed, hushed, choked, living.size());
        data.setDirty();
        updateBar(level, storm);
        if (living.isEmpty() && condensed == 0) {
            finish(level, storm, End.SPENT);
        } else if (storm.wavesLeft() <= 0) {
            if (living.size() >= SCAR_MERGE) {
                merge(level, storm, living);
            } else {
                finish(level, storm, End.PASSED);
            }
        }
    }

    /**
     * Condenses up to {@code room} of the loudest graftable imprints in the loaded, unmuted chunks of the storm's area
     * into storm residues. Returns how many.
     */
    static int condenseArea(ServerLevel level, RecollectionStorm storm, int room) {
        if (room <= 0 || !Residues.enabled()) {
            return 0;
        }
        record Candidate(LevelChunk chunk, ChunkMemory memory, Imprint imprint) {}
        List<Candidate> candidates = new ArrayList<>();
        ChunkPos center = storm.center();
        for (int dx = -AREA; dx <= AREA; dx++) {
            for (int dz = -AREA; dz <= AREA; dz++) {
                int x = center.x() + dx;
                int z = center.z() + dz;
                if (!level.getChunkSource().hasChunk(x, z)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(x, z);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                if (memory == null || memory.hasMuteStone()) {
                    continue;
                }
                for (Imprint imprint : memory.imprintsCopy()) {
                    if (Residues.graftable(imprint.tag())) {
                        candidates.add(new Candidate(chunk, memory, imprint));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingInt((Candidate c) -> c.imprint().pressureContribution()).reversed());
        int made = 0;
        for (Candidate candidate : candidates) {
            if (made >= room) {
                break;
            }
            if (!candidate.memory().removeImprint(candidate.imprint())) {
                continue;
            }
            MemoryPressure.recompute(candidate.chunk(), candidate.memory());
            Imprint imprint = candidate.imprint();
            ResidueEntity residue = Residues.spawn(level, Residues.airAbove(level, imprint.origin()), imprint.tag(), Residues.strengthOf(imprint.intensity()), false);
            if (residue != null) {
                adopt(storm, residue);
                made++;
            }
        }
        return made;
    }

    /** The storm's residues that are still alive and loaded; the rest drop out of the storm's list. */
    public static List<ResidueEntity> living(ServerLevel level, RecollectionStorm storm) {
        List<ResidueEntity> living = new ArrayList<>();
        Iterator<UUID> ids = storm.residues().iterator();
        while (ids.hasNext()) {
            Entity entity = level.getEntity(ids.next());
            if (entity instanceof ResidueEntity residue && residue.isAlive() && residue.storm() == storm.id()) {
                living.add(residue);
            } else {
                ids.remove();
            }
        }
        return living;
    }

    /** Three or more storm residues after the last wave: they merge into the Scar, and the centre becomes a Scar site. */
    static void merge(ServerLevel level, RecollectionStorm storm, List<ResidueEntity> living) {
        int mask = 0;
        for (ResidueEntity residue : living) {
            mask |= 1 << residue.temper().id();
        }
        BlockPos heart = ScarSites.form(level, storm.center(), mask);
        for (ResidueEntity residue : living) {
            double dx = heart.getX() + 0.5D - residue.getX();
            double dy = heart.getY() + 1.0D - residue.getY();
            double dz = heart.getZ() + 0.5D - residue.getZ();
            for (int i = 1; i <= 6; i++) {
                double t = i / 6.0D;
                level.sendParticles(EchoGrafts.particle(residue.temper()), residue.getX() + dx * t, residue.getY() + 0.8D + dy * t, residue.getZ() + dz * t,
                        2, 0.1D, 0.1D, 0.1D, 0.0D);
            }
            residue.discard();
        }
        storm.residues().clear();
        ScarEntity scar = null;
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            scar = ModEntities.SCAR.get().create(level, EntitySpawnReason.EVENT);
            if (scar != null) {
                scar.snapTo(heart.getX() + 0.5D, heart.getY() + 1.5D, heart.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
                scar.setup(mask, living.size(), heart);
                if (!level.addFreshEntity(scar)) {
                    scar = null;
                }
            }
        }
        level.playSound(null, heart, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3.0F, 0.7F);
        tell(level, storm, scar != null ? "mnemolith.storm.scar" : "mnemolith.storm.scar_site", false);
        Mnemolith.LOGGER.info("Mnemolith storm merged id={} into the Scar merged={} tempers={} at {} boss={}", storm.id(), living.size(), describe(mask),
                heart.toShortString(), scar != null);
        finish(level, storm, End.SCAR);
    }

    /** Ends the storm: its bar goes, and the storm residues still standing become ordinary residues. */
    public static void finish(ServerLevel level, RecollectionStorm storm, End end) {
        StormData data = StormData.get(level.getServer());
        if (!data.remove(storm)) {
            return;
        }
        for (ResidueEntity residue : living(level, storm)) {
            residue.setStorm(0L);
        }
        switch (end) {
            case CONTAINED -> tell(level, storm, "mnemolith.storm.contained", true);
            case SPENT -> tell(level, storm, "mnemolith.storm.spent", true);
            case PASSED -> tell(level, storm, "mnemolith.storm.passed", true);
            default -> {}
        }
        dropBar(storm);
        lastEnd = new Ended(storm.id(), end);
        Mnemolith.LOGGER.info("Mnemolith storm ended id={} end={}", storm.id(), end);
    }

    // ---- queries ----

    public static @Nullable RecollectionStorm at(ServerLevel level, ChunkPos chunk) {
        for (RecollectionStorm storm : StormData.get(level.getServer()).storms()) {
            if (storm.covers(level.dimension(), chunk)) {
                return storm;
            }
        }
        return null;
    }

    public static boolean isActive(ServerLevel level, long id) {
        return id != 0L && StormData.get(level.getServer()).byId(id) != null;
    }

    public static String describe(int mask) {
        StringBuilder out = new StringBuilder();
        for (Temper temper : Temper.values()) {
            if ((mask & (1 << temper.id())) != 0) {
                out.append(out.isEmpty() ? "" : "+").append(temper.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return out.toString();
    }

    // ---- feedback: vanilla boss bars, messages, particles ----

    /** Ground level at the middle of the storm's centre chunk. */
    public static BlockPos surface(ServerLevel level, RecollectionStorm storm) {
        int x = storm.center().getMiddleBlockX();
        int z = storm.center().getMiddleBlockZ();
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static boolean inRange(ServerPlayer player, RecollectionStorm storm) {
        double dx = player.getX() - storm.center().getMiddleBlockX();
        double dz = player.getZ() - storm.center().getMiddleBlockZ();
        return player.level().dimension().equals(storm.dimension()) && dx * dx + dz * dz <= BAR_RANGE * BAR_RANGE;
    }

    private static void tell(ServerLevel level, RecollectionStorm storm, String key, boolean overlay) {
        for (ServerPlayer player : level.players()) {
            if (inRange(player, storm)) {
                player.sendSystemMessage(Component.translatable(key), overlay);
            }
        }
    }

    private static void updateBar(ServerLevel level, RecollectionStorm storm) {
        ServerBossEvent bar = BARS.computeIfAbsent(storm.id(), id -> {
            ServerBossEvent created = new ServerBossEvent(new UUID(0x5703_4D4E_0000_0000L, id), Component.translatable("mnemolith.storm.bar.gathering"),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
            created.setDarkenScreen(true);
            created.setCreateWorldFog(true);
            return created;
        });
        if (storm.phase() == RecollectionStorm.Phase.GATHERING) {
            bar.setName(Component.translatable("mnemolith.storm.bar.gathering"));
            bar.setColor(BossEvent.BossBarColor.PURPLE);
            bar.setProgress(Math.min(1.0F, storm.ticks() / (float) GATHER_TICKS));
        } else {
            bar.setName(Component.translatable("mnemolith.storm.bar.raging", living(level, storm).size()));
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setProgress(Math.max(0.0F, storm.wavesLeft() / (float) WAVES));
        }
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (player.isRemoved() || !inRange(player, storm)) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : level.players()) {
            if (inRange(player, storm) && !bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    private static void dropBar(RecollectionStorm storm) {
        ServerBossEvent bar = BARS.remove(storm.id());
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /** Server stopped: bars are not saved (the storms are). */
    public static void clearBars() {
        for (ServerBossEvent bar : BARS.values()) {
            bar.removeAllPlayers();
        }
        BARS.clear();
        lastWave = null;
        lastEnd = null;
    }

    /** While it gathers, the edge of the storm's area flickers once a second. */
    private static void ring(ServerLevel level, RecollectionStorm storm, BlockPos middle) {
        double radius = 16.0D * AREA + 8.0D;
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8.0D + storm.ticks() * 0.05D;
            level.sendParticles(ModParticles.PRESSURE_WARN.get(), middle.getX() + 0.5D + Math.cos(angle) * radius, middle.getY() + 1.5D,
                    middle.getZ() + 0.5D + Math.sin(angle) * radius, 1, 0.2D, 0.6D, 0.2D, 0.0D);
        }
    }
}
