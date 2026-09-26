package com.mnemolith.echo.residue;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.graft.EchoGraft;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Residual echoes: when a chunk is overloaded, its loudest graftable memory (silence, death, fire, fall, explosion)
 * can condense out of the chunk into a drifting fragment, a {@link ResidueEntity}. Condensing takes that imprint out
 * of the chunk (pressure drops), but the residue festers: every {@code residueFesterSeconds} it writes its memory
 * back, so a residue left alone slowly refills its chunk. The player chooses what to do with it:
 * <ul>
 *   <li><b>observe</b>: hold the raised lens on it for 3 seconds to read it (pinned for 10 s, tag discovered);</li>
 *   <li><b>capture</b>: the extraction needle on a pinned residue takes it as a residual shard (on an unread one it
 *       fails and lashes you);</li>
 *   <li><b>exploit</b>: leave it festering beside a needle to farm slips, feed it to your echo (an echo with the same
 *       temper nearby drinks it), or carry the shard and set it down where you want it;</li>
 *   <li><b>starve</b>: a mute stone or a calm chunk wears it down to nothing;</li>
 *   <li><b>let it be</b>: in a fractured chunk it acts its memory out.</li>
 * </ul>
 * All rules live here; the entity only ticks timers and asks this class.
 */
public final class Residues {
    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = 6;
    public static final int OLD_STRENGTH = 5;
    /** How far a residue drinks into a matching echo. */
    public static final double FEED_RANGE = 6.0D;
    /** Lens reading: range, ticks of steady gaze to read, and how long a read residue stays pinned. */
    public static final double READ_RANGE = 16.0D;
    public static final int READ_TICKS = 60;
    public static final int PIN_TICKS = 200;
    public static final int LASH_COOLDOWN_TICKS = 100;
    public static final int PULSE_TICKS = 200;
    public static final double NEARBY_RANGE = 48.0D;
    /** How far an archivist notices a residue it can archive. */
    public static final double ARCHIVIST_RANGE = 10.0D;
    public static final int MAX_ACT_OUT_ZOMBIES = 2;

    private Residues() {}

    public static boolean enabled() {
        return CommonConfig.RESIDUES_ENABLED.get();
    }

    public static boolean graftable(ImprintTag tag) {
        return Temper.of(tag) != null;
    }

    public static int festerTicks() {
        return CommonConfig.RESIDUE_FESTER_SECONDS.get() * 20;
    }

    /** Strength a condensed imprint gives: its intensity, clamped to 2..6. */
    public static int strengthOf(int intensity) {
        return Math.max(2, Math.min(MAX_STRENGTH, intensity));
    }

    // ---- formation ----

    /**
     * Player tick (every {@link #PULSE_TICKS}, spread by player id): the player's chunk may condense a residue, and an
     * observatory chunk seeds its one old residue.
     */
    public static void pulse(ServerLevel level, ServerPlayer player) {
        if (!enabled() || player.isSpectator() || (player.tickCount + player.getId()) % PULSE_TICKS != 0) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null) {
            return;
        }
        if (memory.observatory() && !memory.residueSeeded()) {
            trySeed(level, chunk);
            return;
        }
        PressureBand band = MemoryPressure.band(memory.cachedPressure());
        double chance = switch (band) {
            case OVERLOADED -> CommonConfig.RESIDUE_FORM_CHANCE.get();
            case FRACTURE -> CommonConfig.RESIDUE_FRACTURE_FORM_CHANCE.get();
            default -> 0.0D;
        };
        if (chance > 0.0D && level.getRandom().nextDouble() < chance) {
            condense(level, chunk);
        }
    }

    /**
     * Condenses the loudest graftable imprint of {@code chunk} into a residue (weight times intensity decides). The
     * imprint leaves the chunk. Null when the chunk is not overloaded, holds none, already has a residue, or the area
     * is crowded. Also the QA entry point.
     */
    public static @Nullable ResidueEntity condense(ServerLevel level, LevelChunk chunk) {
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || MemoryPressure.band(memory.cachedPressure()).ordinal() < PressureBand.OVERLOADED.ordinal()) {
            return null;
        }
        Imprint loudest = null;
        for (Imprint imprint : memory.imprintsCopy()) {
            if (graftable(imprint.tag()) && (loudest == null || imprint.pressureContribution() > loudest.pressureContribution())) {
                loudest = imprint;
            }
        }
        if (loudest == null || !roomFor(level, chunk.getPos(), loudest.origin())) {
            return null;
        }
        memory.removeImprint(loudest);
        MemoryPressure.recompute(chunk, memory);
        BlockPos at = airAbove(level, loudest.origin());
        ResidueEntity residue = spawn(level, at, loudest.tag(), strengthOf(loudest.intensity()), false);
        if (residue != null) {
            Mnemolith.LOGGER.info("Mnemolith residue condensed tag={} strength={} at {} pressure={}", loudest.tag().getSerializedName(),
                    residue.strength(), at.toShortString(), memory.cachedPressure());
        }
        return residue;
    }

    /** A chunk takes one residue at a time, and at most {@code residueMaxNearby} drift within 48 blocks. */
    public static boolean roomFor(ServerLevel level, ChunkPos chunk, BlockPos near) {
        AABB column = new AABB(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX() + 1, level.getMaxY(), chunk.getMaxBlockZ() + 1);
        if (!level.getEntitiesOfClass(ResidueEntity.class, column, ResidueEntity::isAlive).isEmpty()) {
            return false;
        }
        int nearby = level.getEntitiesOfClass(ResidueEntity.class, new AABB(near).inflate(NEARBY_RANGE), ResidueEntity::isAlive).size();
        return nearby < CommonConfig.RESIDUE_MAX_NEARBY.get();
    }

    public static @Nullable ResidueEntity spawn(ServerLevel level, BlockPos pos, ImprintTag tag, int strength, boolean old) {
        ResidueEntity residue = ModEntities.RESIDUE.get().create(level, EntitySpawnReason.EVENT);
        if (residue == null) {
            return null;
        }
        residue.snapTo(pos.getX() + 0.5D, pos.getY() + 0.25D, pos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
        residue.setup(tag, Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strength)), pos, old);
        if (!level.addFreshEntity(residue)) {
            return null;
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 0.9F, 0.55F);
        level.sendParticles(EchoGrafts.particle(residue.temper()), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 16, 0.3D, 0.6D, 0.3D, 0.02D);
        return residue;
    }

    /** The first air block at or above {@code pos} (at most 6 up), so a residue never starts inside stone. */
    public static BlockPos airAbove(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < 6; i++) {
            if (level.getBlockState(cursor).isAir() && level.getBlockState(cursor.above()).isAir()) {
                return cursor.immutable();
            }
            cursor.move(0, 1, 0);
        }
        return pos;
    }

    /** Seeds the observatory residue of {@code chunk} if it is an observatory chunk that has not seeded yet. */
    public static @Nullable ResidueEntity trySeed(ServerLevel level, LevelChunk chunk) {
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (!enabled() || memory == null || !memory.observatory() || memory.residueSeeded()) {
            return null;
        }
        return seedObservatory(level, chunk, memory);
    }

    /** An observatory chunk holds one old residue near its reel: strength 5, and it does not fade in a calm chunk. */
    private static @Nullable ResidueEntity seedObservatory(ServerLevel level, LevelChunk chunk, ChunkMemory memory) {
        memory.setResidueSeeded(true);
        chunk.markUnsaved();
        BlockPos reel = findReel(level, chunk);
        BlockPos at;
        if (reel != null) {
            at = airAbove(level, reel.above());
        } else {
            ChunkPos pos = chunk.getPos();
            int x = pos.getMiddleBlockX();
            int z = pos.getMiddleBlockZ();
            at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1, z);
        }
        Temper[] tempers = Temper.values();
        ImprintTag tag = tempers[level.getRandom().nextInt(tempers.length)].tag();
        ResidueEntity residue = spawn(level, at, tag, OLD_STRENGTH, true);
        Mnemolith.LOGGER.info("Mnemolith residue seeded observatory tag={} at {}", tag.getSerializedName(), at.toShortString());
        return residue;
    }

    private static @Nullable BlockPos findReel(ServerLevel level, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos pos = chunk.getPos();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(ModBlocks.COMPOSITION_REEL.get()))) {
                continue;
            }
            int baseY = level.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(ModBlocks.COMPOSITION_REEL.get())) {
                            return new BlockPos(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * A graft leaving an echo in an overloaded or fractured chunk condenses into a residue instead of an imprint (the
     * chunk has no room left to take it quietly). Strength follows the charges left: 2 at half a slip, 6 when full.
     */
    public static boolean condenseGraft(ServerLevel level, EchoGraft graft, BlockPos pos) {
        if (!enabled()) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || MemoryPressure.band(memory.cachedPressure()).ordinal() < PressureBand.OVERLOADED.ordinal()
                || LoadedChunkMemory.isMuted(level, pos)) {
            return false;
        }
        int capacity = EchoGrafts.capacity(graft.temper());
        int strength = 2 + (int) Math.round(4.0D * graft.charge() / Math.max(1, capacity));
        return spawn(level, airAbove(level, pos), graft.cast().tag(), strength, false) != null;
    }

    // ---- fester ----

    /** What one fester did; the QA reads it. */
    public enum Fester { STARVED, FED, FADED, HELD, WROTE, DISSOLVED }

    /**
     * One fester, in order: a mute stone starves it; a matching echo drinks it; a calm chunk lets it fade (an old
     * residue holds instead); otherwise it writes its memory into the chunk, and in a fracture also acts it out.
     */
    public static Fester fester(ServerLevel level, ResidueEntity residue) {
        BlockPos pos = residue.blockPosition();
        ImprintTag tag = residue.tag();
        Fester result;
        if (LoadedChunkMemory.isMuted(level, pos)) {
            result = weaken(level, residue, Fester.STARVED);
        } else if (feed(level, residue)) {
            result = weaken(level, residue, Fester.FED);
        } else {
            ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
            PressureBand band = memory == null ? PressureBand.CALM : MemoryPressure.band(memory.cachedPressure());
            if (band == PressureBand.CALM) {
                result = residue.isOld() ? Fester.HELD : weaken(level, residue, Fester.FADED);
            } else {
                ImprintWriter.write(level, pos, List.of(tag), null, false);
                result = Fester.WROTE;
                if (band == PressureBand.FRACTURE && CommonConfig.RESIDUE_ACTS_OUT.get()) {
                    actOut(level, residue);
                }
            }
        }
        Mnemolith.LOGGER.debug("Mnemolith residue fester tag={} strength={} -> {}", tag.getSerializedName(), residue.strength(), result);
        return result;
    }

    private static Fester weaken(ServerLevel level, ResidueEntity residue, Fester why) {
        int left = residue.strength() - 1;
        if (left < MIN_STRENGTH) {
            dissolve(level, residue);
            return Fester.DISSOLVED;
        }
        residue.setStrength(left);
        return why;
    }

    public static void dissolve(ServerLevel level, ResidueEntity residue) {
        level.sendParticles(EchoGrafts.particle(residue.temper()), residue.getX(), residue.getY() + 0.8D, residue.getZ(), 24, 0.4D, 0.6D, 0.4D, 0.04D);
        level.playSound(null, residue.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 0.8F, 1.4F);
        Mnemolith.LOGGER.info("Mnemolith residue dissolved tag={} at {}", residue.tag().getSerializedName(), residue.blockPosition().toShortString());
        residue.discard();
    }

    /** An echo grafted with the same temper within 6 blocks, not yet full, drinks half a slip from the residue. */
    private static boolean feed(ServerLevel level, ResidueEntity residue) {
        Temper temper = residue.temper();
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, residue.getBoundingBox().inflate(FEED_RANGE),
                e -> e.isAlive() && EchoGrafts.enabled() && e.graftTemper() == temper)) {
            if (EchoGrafts.topUp(echo, Math.max(1, EchoGrafts.slipCharge(temper) / 2))) {
                level.sendParticles(EchoGrafts.particle(temper), echo.getX(), echo.getY() + 1.0D, echo.getZ(), 10, 0.3D, 0.5D, 0.3D, 0.02D);
                return true;
            }
        }
        return false;
    }

    /** A fractured chunk lets the residue play its memory out. Fire and blasts respect mobGriefing. */
    public static void actOut(ServerLevel level, ResidueEntity residue) {
        BlockPos pos = residue.blockPosition();
        boolean griefing = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
        switch (residue.tag()) {
            case DEATH -> {
                int zombies = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, residue.getBoundingBox().inflate(12.0D)).size();
                if (zombies < MAX_ACT_OUT_ZOMBIES) {
                    net.minecraft.world.entity.EntityTypes.ZOMBIE.spawn(level, groundBelow(level, pos), EntitySpawnReason.EVENT);
                }
            }
            case FIRE -> {
                if (griefing) {
                    BlockPos ground = groundBelow(level, pos.offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4));
                    if (level.getBlockState(ground).isAir()) {
                        level.setBlockAndUpdate(ground, net.minecraft.world.level.block.BaseFireBlock.getState(level, ground));
                    }
                }
            }
            case EXPLOSION -> level.explode(residue, residue.getX(), residue.getY() + 0.5D, residue.getZ(), 1.5F,
                    griefing ? net.minecraft.world.level.Level.ExplosionInteraction.MOB : net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            case FALL -> {
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, residue.getBoundingBox().inflate(8.0D), Residues::affects)) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.LEVITATION, 40, 0));
                }
            }
            case SILENCE -> {
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, residue.getBoundingBox().inflate(8.0D), Residues::affects)) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 120, 0));
                }
            }
            default -> {}
        }
        Mnemolith.LOGGER.info("Mnemolith residue acted out tag={} at {}", residue.tag().getSerializedName(), pos.toShortString());
    }

    /** The first air block with a solid block under it, searching down at most 8 blocks. */
    private static BlockPos groundBelow(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < 8; i++) {
            if (level.getBlockState(cursor).isAir() && level.getBlockState(cursor.below()).isFaceSturdy(level, cursor.below(), net.minecraft.core.Direction.UP)) {
                return cursor.immutable();
            }
            cursor.move(0, -1, 0);
        }
        return pos;
    }

    public static boolean affects(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    // ---- lash ----

    /** The unread residue strikes {@code player} with its memory. */
    public static void lash(ServerLevel level, ResidueEntity residue, ServerPlayer player) {
        switch (residue.tag()) {
            case FIRE -> player.igniteForSeconds(3.0F);
            case FALL -> {
                player.push(0.0D, 0.9D, 0.0D);
                player.hurtMarked = true;
            }
            case DEATH -> {
                player.hurtServer(level, level.damageSources().magic(), 3.0F);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 60, 0));
            }
            case EXPLOSION -> level.explode(residue, player.getX(), player.getY() + 0.2D, player.getZ(), 1.0F, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            case SILENCE -> {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 60, 0));
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 80, 0));
            }
            default -> {}
        }
        level.sendParticles(EchoGrafts.particle(residue.temper()), player.getX(), player.getY() + 1.0D, player.getZ(), 12, 0.3D, 0.5D, 0.3D, 0.05D);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.0F, 0.5F);
    }

    // ---- observe ----

    /** True when {@code player} holds the raised lens and looks straight at {@code residue} with a clear line. */
    public static boolean reading(ServerPlayer player, ResidueEntity residue) {
        if (player.level() != residue.level() || !com.mnemolith.content.item.ChronicleLensItem.isFocusing(player)
                || player.distanceToSqr(residue) > READ_RANGE * READ_RANGE) {
            return false;
        }
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 to = residue.getBoundingBox().getCenter().subtract(eye);
        double length = to.length();
        if (length < 1.0E-3D) {
            return true;
        }
        return player.getViewVector(1.0F).dot(to.scale(1.0D / length)) >= 0.98D && player.hasLineOfSight(residue);
    }

    /** Three seconds of steady reading: the residue is pinned (slow, harmless, glowing) and its tag is discovered. */
    public static void pinned(ServerLevel level, ResidueEntity residue, @Nullable ServerPlayer reader) {
        residue.pin(PIN_TICKS);
        level.playSound(null, residue.blockPosition(), com.mnemolith.audio.ModSounds.LENS_FOCUS.get(), SoundSource.PLAYERS, 0.9F, 0.7F);
        level.sendParticles(EchoGrafts.particle(residue.temper()), residue.getX(), residue.getY() + 0.8D, residue.getZ(), 20, 0.4D, 0.6D, 0.4D, 0.0D);
        if (reader != null) {
            com.mnemolith.imprint.DiscoveryNotes.noteTag(reader, residue.tag());
            reader.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("mnemolith.residue.read",
                    net.minecraft.network.chat.Component.translatable(residue.tag().translationKey()), residue.strength()));
        }
    }

    // ---- capture ----

    /** A residual shard holding {@code residue}: an imprint cast whose intensity is the strength. */
    public static ItemStack shardOf(ServerLevel level, ResidueEntity residue) {
        return shard(residue.tag(), residue.strength(), residue.origin(), level.getGameTime());
    }

    public static ItemStack shard(ImprintTag tag, int strength, BlockPos origin, long time) {
        ItemStack stack = new ItemStack(ModItems.RESIDUAL_SHARD.get());
        stack.set(ModDataComponents.IMPRINT_CAST.get(), new ImprintCast(tag, strength, origin.immutable(), Optional.empty(), Imprint.contextHash(tag, origin, time), time));
        return stack;
    }

    public static boolean isShard(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.RESIDUAL_SHARD.get()) && stack.get(ModDataComponents.IMPRINT_CAST.get()) != null;
    }

    /**
     * The needle on a residue. Unread, it slips off and the residue lashes out. Read (pinned), it comes away as a
     * residual shard, at twice the needle's usual wear.
     */
    public static boolean capture(ServerPlayer player, ResidueEntity residue, ItemStack needle) {
        ServerLevel level = (ServerLevel) residue.level();
        int cooldown = CommonConfig.EXTRACTION_COOLDOWN_TICKS.get();
        if (cooldown > 0 && player.getCooldowns().isOnCooldown(needle)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("mnemolith.message.extract_cooldown"), true);
            return false;
        }
        if (!residue.isPinned()) {
            lash(level, residue, player);
            residue.lashed();
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("mnemolith.residue.slipped"), true);
            return false;
        }
        ItemStack shard = shardOf(level, residue);
        if (!player.getInventory().add(shard)) {
            player.drop(shard, false);
        }
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(needle, cooldown);
        }
        int cost = CommonConfig.EXTRACTION_DURABILITY_COST.get() * 2;
        if (cost > 0) {
            needle.hurtAndBreak(cost, level, player, item -> {});
        }
        com.mnemolith.imprint.DiscoveryNotes.noteTag(player, residue.tag());
        level.playSound(null, residue.blockPosition(), com.mnemolith.audio.ModSounds.EXTRACT.get(), SoundSource.PLAYERS, 1.0F, 0.6F);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("mnemolith.residue.captured",
                net.minecraft.network.chat.Component.translatable(residue.tag().translationKey()), residue.strength()), true);
        Mnemolith.LOGGER.info("Mnemolith residue captured tag={} strength={} by {}", residue.tag().getSerializedName(), residue.strength(), player.getGameProfile().name());
        residue.discard();
        return true;
    }

    /**
     * A possessed body with an empty hand absorbs the residue: the body takes the lash (the player feels it), then
     * wears the memory as its graft. It replaces any graft the body had (released as usual).
     */
    public static boolean absorb(ServerPlayer player, ResidueEntity residue) {
        ServerLevel level = (ServerLevel) residue.level();
        if (!EchoGrafts.enabled()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("mnemolith.graft.disabled"), true);
            return false;
        }
        lash(level, residue, player);
        ImprintCast cast = shardOf(level, residue).get(ModDataComponents.IMPRINT_CAST.get());
        if (cast == null || !EchoGrafts.absorbIntoPossessed(player, cast, shardCharge(residue.temper(), residue.strength()))) {
            return false;
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("mnemolith.residue.absorbed",
                net.minecraft.network.chat.Component.translatable(residue.temper().key())), true);
        Mnemolith.LOGGER.info("Mnemolith residue absorbed by possessed {} tag={}", player.getGameProfile().name(), residue.tag().getSerializedName());
        residue.discard();
        return true;
    }

    /** Charges a shard or residue of {@code strength} gives a graft: full capacity from strength 4, one slip at 2. */
    public static int shardCharge(Temper temper, int strength) {
        int capacity = EchoGrafts.capacity(temper);
        return Math.max(1, capacity * Math.max(2, Math.min(4, strength)) / 4);
    }

    /** A residual shard used on a block: the residue comes back out there, strength kept. */
    public static @Nullable ResidueEntity release(ServerLevel level, ItemStack shard, BlockPos pos) {
        ImprintCast cast = shard.get(ModDataComponents.IMPRINT_CAST.get());
        if (cast == null || !graftable(cast.tag()) || !enabled()) {
            return null;
        }
        return spawn(level, airAbove(level, pos), cast.tag(), cast.intensity(), false);
    }
}
