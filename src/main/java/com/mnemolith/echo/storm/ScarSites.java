package com.mnemolith.echo.storm;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.network.PressureSync;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Scar sites: what a storm leaves where its residues merged. The chunk remembers the site ({@link ScarSite} on its
 * memory): no storm gathers within one chunk of it, and once per in-game day it seeds one old residue (strength 5) of
 * a temper that merged there, unless a mute stone silences the chunk. With mobGriefing on, the Scar also grows its
 * heart (breaking it heals the scar) and a ring of scar glass; blocks only ever go into air or replaceable plants,
 * never over a solid block. With mobGriefing off, the site is the chunk flag alone.
 */
public final class ScarSites {
    public static final long RESEED_TICKS = 24000L;
    public static final int GLASS_ATTEMPTS = 18;
    public static final int GLASS_MIN_RADIUS = 3;
    public static final int GLASS_MAX_RADIUS = 7;

    private ScarSites() {}

    /** Forms the site at the middle of {@code center}. Returns the heart position (placed or not). */
    public static BlockPos form(ServerLevel level, ChunkPos center, int tempers) {
        LevelChunk chunk = level.getChunk(center.x(), center.z());
        int x = center.getMiddleBlockX();
        int z = center.getMiddleBlockZ();
        BlockPos heart = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
        memory.setScar(new ScarSite(tempers, level.getGameTime()));
        chunk.markUnsaved();
        PressureSync.markDirty();
        boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        int glass = 0;
        if (griefing) {
            if (open(level, heart)) {
                level.setBlock(heart, ModBlocks.SCAR_HEART.get().defaultBlockState(), Block.UPDATE_ALL);
            }
            glass = growGlass(level, heart, level.getRandom());
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, heart.getX() + 0.5D, heart.getY() + 1.0D, heart.getZ() + 0.5D,
                60, 1.5D, 1.5D, 1.5D, 0.1D);
        Mnemolith.LOGGER.info("Mnemolith scar site formed at {} tempers={} griefing={} glass={}", heart.toShortString(), Storms.describe(tempers), griefing, glass);
        return heart;
    }

    /** Scar glass in a ring around the heart: 1 to 2 tall, only into open, loaded spots on solid ground. */
    private static int growGlass(ServerLevel level, BlockPos heart, RandomSource random) {
        int placed = 0;
        BlockState glass = ModBlocks.SCAR_GLASS.get().defaultBlockState();
        for (int i = 0; i < GLASS_ATTEMPTS; i++) {
            double angle = (i + random.nextDouble() * 0.6D) * (Math.PI * 2.0D / GLASS_ATTEMPTS);
            double radius = GLASS_MIN_RADIUS + random.nextDouble() * (GLASS_MAX_RADIUS - GLASS_MIN_RADIUS);
            int gx = heart.getX() + (int) Math.round(Math.cos(angle) * radius);
            int gz = heart.getZ() + (int) Math.round(Math.sin(angle) * radius);
            if (!level.getChunkSource().hasChunk(gx >> 4, gz >> 4)) {
                continue;
            }
            BlockPos spot = new BlockPos(gx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz), gz);
            if (Math.abs(spot.getY() - heart.getY()) > 4 || !open(level, spot)
                    || !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
                continue;
            }
            level.setBlock(spot, glass, Block.UPDATE_ALL);
            placed++;
            if (random.nextInt(3) == 0 && open(level, spot.above())) {
                level.setBlock(spot.above(), glass, Block.UPDATE_ALL);
                placed++;
            }
        }
        return placed;
    }

    /** Air or a replaceable plant, never a fluid: the Scar never overwrites a real block. */
    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** The heart was broken: the chunk forgets its site (storms may gather again; no more seeding). */
    public static void heal(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || memory.scar() == null) {
            return;
        }
        memory.setScar(null);
        chunk.markUnsaved();
        PressureSync.markDirty();
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 32.0D * 32.0D) {
                player.sendSystemMessage(Component.translatable("mnemolith.scar.healed"), true);
            }
        }
        Mnemolith.LOGGER.info("Mnemolith scar site healed at {}", pos.toShortString());
    }

    /**
     * Residue pulse in a Scar chunk: once per in-game day, unless muted or a residue is already there, the site seeds
     * one old residue (strength 5) of a temper that merged here, by its heart. Returns it, or null.
     */
    public static @Nullable ResidueEntity reseed(ServerLevel level, LevelChunk chunk, ChunkMemory memory) {
        ScarSite site = memory.scar();
        if (site == null || !Residues.enabled() || level.getGameTime() - site.seededAt() < RESEED_TICKS) {
            return null;
        }
        return seed(level, chunk, memory, site);
    }

    /** Seeds now, ignoring the day timer (QA). Still null when muted or crowded. */
    public static @Nullable ResidueEntity seed(ServerLevel level, LevelChunk chunk, ChunkMemory memory, ScarSite site) {
        BlockPos heart = findHeart(level, chunk);
        BlockPos at;
        if (heart != null) {
            at = Residues.airAbove(level, heart.above());
        } else {
            ChunkPos pos = chunk.getPos();
            int x = pos.getMiddleBlockX();
            int z = pos.getMiddleBlockZ();
            at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1, z);
        }
        if (LoadedChunkMemory.isMuted(level, at) || !Residues.roomFor(level, chunk.getPos(), at)) {
            return null;
        }
        List<Temper> merged = new ArrayList<>();
        for (Temper temper : Temper.values()) {
            if ((site.tempers() & (1 << temper.id())) != 0) {
                merged.add(temper);
            }
        }
        if (merged.isEmpty()) {
            merged.add(Temper.GRAVE);
        }
        Temper temper = merged.get(level.getRandom().nextInt(merged.size()));
        memory.setScar(site.withSeededAt(level.getGameTime()));
        chunk.markUnsaved();
        ResidueEntity residue = Residues.spawn(level, at, temper.tag(), Residues.OLD_STRENGTH, true);
        if (residue != null) {
            level.sendParticles(EchoGrafts.particle(temper), at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, 20, 0.5D, 0.5D, 0.5D, 0.02D);
            Mnemolith.LOGGER.info("Mnemolith scar site seeded tag={} at {}", temper.tag().getSerializedName(), at.toShortString());
        }
        return residue;
    }

    /** The heart block in this chunk, if any (only sections whose palette may hold it are walked). */
    public static @Nullable BlockPos findHeart(ServerLevel level, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos pos = chunk.getPos();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(ModBlocks.SCAR_HEART.get()))) {
                continue;
            }
            int baseY = level.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(ModBlocks.SCAR_HEART.get())) {
                            return new BlockPos(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z);
                        }
                    }
                }
            }
        }
        return null;
    }
}
