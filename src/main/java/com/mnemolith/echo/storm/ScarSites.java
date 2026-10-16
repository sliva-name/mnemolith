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

    /** Tendings with a remembrance posy that mend a site into a memorial. */
    public static final int TENDINGS = 3;

    public enum Tend { NONE, TOO_SOON, TENDED, MENDED }

    private ScarSites() {}

    /**
     * {@code player} tends the heart at {@code heart} with {@code posy}: at most once per in-game day; each tending
     * spends a posy (not in creative) and gives up that day's old residue; the {@link #TENDINGS}th mends the site (the
     * heart becomes a memorial stone, the site a memorial). Public for QA.
     */
    public static Tend tend(ServerLevel level, ServerPlayer player, BlockPos heart, net.minecraft.world.item.ItemStack posy) {
        LevelChunk chunk = level.getChunkAt(heart);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        ScarSite site = memory == null ? null : memory.scar();
        if (site == null || site.memorial() || !level.getBlockState(heart).is(ModBlocks.SCAR_HEART.get())
                || !posy.is(com.mnemolith.content.ModItems.REMEMBRANCE_POSY.get())) {
            return Tend.NONE;
        }
        long now = level.getGameTime();
        long left = site.tendedAt() + RESEED_TICKS - now;
        if (left > 0L) {
            player.sendOverlayMessage(Component.translatable("mnemolith.mending.too_soon", Math.max(1L, (left + 1199L) / 1200L)));
            return Tend.TOO_SOON;
        }
        ScarSite tended = site.tend(now);
        if (!player.isCreative()) {
            posy.shrink(1);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, heart.getX() + 0.5D, heart.getY() + 1.0D, heart.getZ() + 0.5D,
                16, 0.4D, 0.6D, 0.4D, 0.01D);
        level.playSound(null, heart, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 0.6F);
        if (tended.tended() < TENDINGS) {
            memory.setScar(tended);
            chunk.markUnsaved();
            player.sendOverlayMessage(Component.translatable("mnemolith.mending.tended", tended.tended(), TENDINGS));
            return Tend.TENDED;
        }
        memory.setScar(tended.asMemorial());
        chunk.markUnsaved();
        level.setBlock(heart, ModBlocks.MEMORIAL_STONE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, heart.getX() + 0.5D, heart.getY() + 1.0D, heart.getZ() + 0.5D,
                40, 1.0D, 1.0D, 1.0D, 0.05D);
        // a mended site is memory kept for good: the shared kept cue
        com.mnemolith.particle.MemoryFeel.cue(level, com.mnemolith.particle.MemoryFeel.Kind.KEPT, heart, 12);
        for (ServerPlayer near : level.players()) {
            if (near.distanceToSqr(heart.getX() + 0.5D, heart.getY() + 0.5D, heart.getZ() + 0.5D) <= 32.0D * 32.0D) {
                near.sendSystemMessage(Component.translatable("mnemolith.mending.mended"), false);
            }
        }
        Mnemolith.LOGGER.info("Mnemolith scar site mended into a memorial at {}", heart.toShortString());
        return Tend.MENDED;
    }

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
        if (level.getBlockState(pos).is(ModBlocks.MEMORIAL_STONE.get())) {
            return; // the heart was just mended in place, not broken
        }
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || memory.scar() == null) {
            return;
        }
        boolean memorial = memory.scar().memorial();
        memory.setScar(null);
        chunk.markUnsaved();
        PressureSync.markDirty();
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 32.0D * 32.0D) {
                player.sendSystemMessage(Component.translatable(memorial ? "mnemolith.mending.forgotten" : "mnemolith.scar.healed"), true);
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
        if (site == null || level.getGameTime() - site.seededAt() < RESEED_TICKS) {
            return null;
        }
        if (site.memorial()) {
            grow(level, chunk, memory, site);
            return null;
        }
        if (!Residues.enabled()) {
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
        ResidueEntity residue = Residues.spawn(level, at, temper.tag(), Residues.OLD_STRENGTH, true);
        if (residue == null) {
            return null;
        }
        memory.setScar(site.withSeededAt(level.getGameTime()));
        chunk.markUnsaved();
        level.sendParticles(EchoGrafts.particle(temper), at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, 20, 0.5D, 0.5D, 0.5D, 0.02D);
        Mnemolith.LOGGER.debug("Mnemolith scar site seeded tag={} at {}", temper.tag().getSerializedName(), at.toShortString());
        return residue;
    }

    /** Forget-me-nots a memorial grows per day, how far from the stone, and how many may stand around it. */
    public static final int GROVE_PER_DAY = 2;
    public static final int GROVE_RADIUS = 6;
    public static final int GROVE_CAP = 12;
    /** Residues within this many blocks of a memorial stone rest instead of festering and do not lash. */
    public static final double CALM_RADIUS = 8.0D;

    /**
     * The memorial grove: up to {@link #GROVE_PER_DAY} forget-me-nots within {@link #GROVE_RADIUS} blocks of the stone,
     * on grass, dirt or moss, into air, while fewer than {@link #GROVE_CAP} stand there. Resets the site's day. Returns
     * how many grew. Public for QA (ignores nothing: callers check the day).
     */
    public static int grow(ServerLevel level, LevelChunk chunk, ChunkMemory memory, ScarSite site) {
        memory.setScar(site.withSeededAt(level.getGameTime()));
        chunk.markUnsaved();
        BlockPos stone = findBlock(level, chunk, ModBlocks.MEMORIAL_STONE.get());
        if (stone == null) {
            return 0;
        }
        int standing = 0;
        for (BlockPos pos : BlockPos.betweenClosed(stone.offset(-GROVE_RADIUS, -3, -GROVE_RADIUS), stone.offset(GROVE_RADIUS, 3, GROVE_RADIUS))) {
            if (level.getBlockState(pos).is(ModBlocks.FORGET_ME_NOT.get())) {
                standing++;
            }
        }
        int grown = 0;
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 24 && grown < GROVE_PER_DAY && standing + grown < GROVE_CAP; attempt++) {
            int x = stone.getX() + random.nextInt(GROVE_RADIUS * 2 + 1) - GROVE_RADIUS;
            int z = stone.getZ() + random.nextInt(GROVE_RADIUS * 2 + 1) - GROVE_RADIUS;
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(spot.getY() - stone.getY()) > 3 || !level.getBlockState(spot).isAir()
                    || !groveGround(level.getBlockState(spot.below()))
                    || !ModBlocks.FORGET_ME_NOT.get().defaultBlockState().canSurvive(level, spot)) {
                continue;
            }
            level.setBlock(spot, ModBlocks.FORGET_ME_NOT.get().defaultBlockState(), Block.UPDATE_ALL);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, x + 0.5D, spot.getY() + 0.5D, z + 0.5D, 4, 0.2D, 0.2D, 0.2D, 0.0D);
            grown++;
        }
        Mnemolith.LOGGER.debug("Mnemolith memorial grove at {} grew {} (standing {})", stone.toShortString(), grown, standing);
        return grown;
    }

    /** Grass, dirt, moss or hollow turf: ground a forget-me-not may grow on. */
    public static boolean groveGround(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.PODZOL)
                || state.is(ModBlocks.HOLLOW_TURF.get());
    }

    /** Whether a memorial stone stands within {@link #CALM_RADIUS} of {@code pos} (memorial sites in the 3x3 chunks). */
    public static boolean nearMemorial(ServerLevel level, BlockPos pos) {
        ChunkPos center = ChunkPos.containing(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getChunkSource().hasChunk(center.x() + dx, center.z() + dz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(center.x() + dx, center.z() + dz);
                ChunkMemory memory = LoadedChunkMemory.existing(chunk);
                if (memory == null || memory.scar() == null || !memory.scar().memorial()) {
                    continue;
                }
                BlockPos stone = findBlock(level, chunk, ModBlocks.MEMORIAL_STONE.get());
                if (stone != null && stone.distToCenterSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= CALM_RADIUS * CALM_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The heart block in this chunk, if any (only sections whose palette may hold it are walked). */
    public static @Nullable BlockPos findHeart(ServerLevel level, LevelChunk chunk) {
        return findBlock(level, chunk, ModBlocks.SCAR_HEART.get());
    }

    /** The first {@code block} in this chunk, if any (only sections whose palette may hold it are walked). */
    public static @Nullable BlockPos findBlock(ServerLevel level, LevelChunk chunk, Block block) {
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos pos = chunk.getPos();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) {
                continue;
            }
            int baseY = level.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(block)) {
                            return new BlockPos(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z);
                        }
                    }
                }
            }
        }
        return null;
    }
}
