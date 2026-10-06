package com.mnemolith.recall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.echo.FakePlace;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.PastSelf;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.entity.mob.FractureStalker;
import com.mnemolith.event.ExpansionImprintEvents;
import com.mnemolith.network.PastVisionPayload;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "The world remembers you." A few large events of each player's life in this world ({@link LifeMoment}: a death, a
 * home, a big build, a hard fight) are kept in {@link LifeMoments}. When the owner comes back to such a place after a
 * long absence, the world may replay it there once: a {@link PastSelf} in their skin plays the moment, the screen edge
 * softens ({@link PastVisionPayload}) and one quiet line shows above the hotbar. Everything is owner-only by default.
 * <p>
 * Recording is event-driven. The return check runs every 40 ticks per player and only looks at that player's moments.
 */
public final class RememberYou {
    /** A scene never starts closer than this: it is meant to be seen from a few steps away. */
    public static final int MIN_DISTANCE = 4;
    private static final int CHECK_TICKS = 40;
    private static final int MAX_TALLIES = 3;
    private static final int LOOK_TARGETS = 8;
    private static final int WHISPER_RGB = 0xFFD6EA;

    private static final Map<UUID, PastSelf> SCENES = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Tally>> TALLIES = new ConcurrentHashMap<>();

    private RememberYou() {}

    public static boolean enabled() {
        return CommonConfig.REMEMBER_ENABLED.get();
    }

    public static @Nullable LifeMoments moments(ServerPlayer player) {
        return player.level() instanceof ServerLevel level ? LifeMoments.get(level.getServer()) : null;
    }

    /** Real, online, non-fake players in a server level. World events go through this; the QA suite calls past it. */
    public static boolean accept(ServerPlayer player) {
        return enabled() && !(player instanceof FakePlayer) && player.level() instanceof ServerLevel;
    }

    // ------------------------------------------------------------------ recording

    public static void onDeath(ServerPlayer player, DamageSource source) {
        if (!accept(player) || !CommonConfig.REMEMBER_RECORD_DEATH.get()) {
            return;
        }
        String detail = source.getEntity() != null
                ? BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntity().getType()).toString()
                : source.getMsgId();
        record(player, LifeMomentKind.DEATH, player.blockPosition(), player.getYRot(), itemId(player.getMainHandItem()), detail);
    }

    /** The player lay down in the bed at {@code bed} (its head block). */
    public static void onHome(ServerPlayer player, BlockPos bed) {
        if (!accept(player) || !CommonConfig.REMEMBER_RECORD_HOME.get()) {
            return;
        }
        record(player, LifeMomentKind.HOME, bed, player.getYRot(), "", "");
    }

    public static void onKill(ServerPlayer player, LivingEntity dead) {
        if (!accept(player) || !CommonConfig.REMEMBER_RECORD_BATTLE.get() || !notable(dead)) {
            return;
        }
        record(player, LifeMomentKind.BATTLE, dead.blockPosition(), player.getYRot(), itemId(player.getMainHandItem()),
                BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString());
    }

    public static void onPlace(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!accept(player) || !CommonConfig.REMEMBER_RECORD_BUILD.get() || FakePlace.skippingOwnerImprint()) {
            return;
        }
        tally(player, pos, state);
    }

    /** A boss, an elite, or a mob with at least {@code battleMinHealth} max health. Never a player. */
    public static boolean notable(LivingEntity dead) {
        if (dead instanceof Player) {
            return false;
        }
        if (dead instanceof ScarEntity || ExpansionImprintEvents.isBoss(dead)) {
            return true;
        }
        if (dead instanceof FractureStalker stalker && stalker.elite()) {
            return true;
        }
        return dead.getMaxHealth() >= CommonConfig.REMEMBER_BATTLE_MIN_HEALTH.get();
    }

    /** Writes one moment for {@code player}. Does not reject fake players: the QA suite calls this directly. */
    public static LifeMoments.@Nullable Added record(ServerPlayer player, LifeMomentKind kind, BlockPos pos, float yaw, String itemId, String detail) {
        if (!(player.level() instanceof ServerLevel level) || !enabled()) {
            return null;
        }
        LifeMoment moment = LifeMoment.fresh(kind, level.dimension().identifier().toString(), pos, yaw, itemId, detail, level.getGameTime());
        LifeMoments.Added added = LifeMoments.get(level.getServer()).add(player.getUUID(), moment, CommonConfig.REMEMBER_MAX_MOMENTS.get());
        Mnemolith.LOGGER.debug("Mnemolith remember {} {} at {} ({})", player.getGameProfile().name(), kind.getSerializedName(), pos.toShortString(), added);
        return added;
    }

    /**
     * Counts one placed block toward a nearby build. At {@code buildBlocks} blocks within {@code buildRadius} of the
     * build's first block, inside {@code buildWindowTicks}, a build is recorded at their centre with the most used block.
     * Returns true when this block completed a build. Not saved: a logout drops an unfinished tally.
     */
    public static boolean tally(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!(player.level() instanceof ServerLevel level) || !enabled() || state.isAir() || state.getBlock() == ModBlocks.REPLICATED_MOMENT.get()) {
            return false;
        }
        long now = level.getGameTime();
        String dimension = level.dimension().identifier().toString();
        int radius = CommonConfig.REMEMBER_BUILD_RADIUS.get();
        long window = CommonConfig.REMEMBER_BUILD_WINDOW.get();
        List<Tally> tallies = TALLIES.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        tallies.removeIf(tally -> now - tally.start > window);
        Tally match = null;
        for (Tally tally : tallies) {
            if (tally.dimension.equals(dimension) && tally.anchor.distSqr(pos) <= (double) radius * radius) {
                match = tally;
                break;
            }
        }
        if (match == null) {
            if (tallies.size() >= MAX_TALLIES) {
                tallies.remove(0);
            }
            match = new Tally(dimension, pos.immutable(), now);
            tallies.add(match);
        }
        match.add(pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (match.count < CommonConfig.REMEMBER_BUILD_BLOCKS.get()) {
            return false;
        }
        tallies.remove(match);
        BlockPos centre = new BlockPos((int) Math.floor(match.sumX / (double) match.count), (int) Math.floor(match.sumY / (double) match.count), (int) Math.floor(match.sumZ / (double) match.count));
        record(player, LifeMomentKind.BUILD, centre, player.getYRot(), match.mostUsed(), "");
        return true;
    }

    /** Blocks counted so far toward the player's nearest open build. QA only. */
    public static int tallied(ServerPlayer player) {
        int best = 0;
        for (Tally tally : TALLIES.getOrDefault(player.getUUID(), List.of())) {
            best = Math.max(best, tally.count);
        }
        return best;
    }

    // ------------------------------------------------------------------ replay

    public static void tick(ServerPlayer player) {
        if (!accept(player) || (player.tickCount + (player.getId() & 31)) % CHECK_TICKS != 0) {
            return;
        }
        consider(player);
    }

    /**
     * One return check: picks a moment the player has come back to, marks every moment around them as seen, then, if
     * nothing holds it back and the roll passes, stages the scene. Fake players are not rejected here (QA).
     */
    public static Optional<PastSelf> consider(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !enabled() || player.isDeadOrDying()) {
            return Optional.empty();
        }
        long now = level.getGameTime();
        Optional<LifeMoment> candidate = candidate(player, now);
        PastSelf body = null;
        if (candidate.isPresent() && ready(player, now) && player.getRandom().nextDouble() < CommonConfig.REMEMBER_SCENE_CHANCE.get()) {
            body = stage(player, candidate.get());
        }
        // Being here counts as a visit, whether or not the scene played.
        LifeMoments.get(level.getServer()).markNear(player.getUUID(), level.dimension().identifier().toString(), player.blockPosition(),
                CommonConfig.REMEMBER_SCENE_RADIUS.get(), now);
        return Optional.ofNullable(body);
    }

    /**
     * True when a scene may start for this player now: they can see it (not creative or spectator, no open menu, no
     * storm), none is running, and both their scene cooldown and the shared stage-1 wow cooldown have passed.
     */
    public static boolean ready(ServerPlayer player, long now) {
        LifeMoments moments = moments(player);
        if (moments == null || !LivingMemory.showing(player) || running(player.getUUID())) {
            return false;
        }
        if (now - moments.lastScene(player.getUUID()) < CommonConfig.REMEMBER_SCENE_COOLDOWN.get()) {
            return false;
        }
        return LivingMemory.cooldownReady(player);
    }

    /**
     * The moment of this player, in this dimension, between {@link #MIN_DISTANCE} and {@code sceneRadius} blocks away,
     * that the player has been away from for at least {@code awayTicks}, has replayed fewer than {@code maxReplays}
     * times, and whose chunk is not muted. The longest absence wins; ties go to the earlier kind (death first).
     */
    public static Optional<LifeMoment> candidate(ServerPlayer player, long now) {
        LifeMoments moments = moments(player);
        if (moments == null || !(player.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        String dimension = level.dimension().identifier().toString();
        int radius = CommonConfig.REMEMBER_SCENE_RADIUS.get();
        long away = CommonConfig.REMEMBER_AWAY_TICKS.get();
        int maxReplays = CommonConfig.REMEMBER_MAX_REPLAYS.get();
        BlockPos feet = player.blockPosition();
        LifeMoment best = null;
        for (LifeMoment moment : moments.of(player.getUUID())) {
            if (!dimension.equals(moment.dimension()) || moment.replays() >= maxReplays || now - moment.lastNear() < away) {
                continue;
            }
            double distance = feet.distSqr(moment.pos());
            if (distance > (double) radius * radius || distance < (double) MIN_DISTANCE * MIN_DISTANCE) {
                continue;
            }
            if (LoadedChunkMemory.isMuted(level, moment.pos())) {
                continue;
            }
            if (best == null || moment.lastNear() < best.lastNear()
                    || (moment.lastNear() == best.lastNear() && moment.kind().ordinal() < best.kind().ordinal())) {
                best = moment;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean running(UUID owner) {
        PastSelf scene = SCENES.get(owner);
        return scene != null && running(owner, scene.level().getGameTime());
    }

    /** {@link #running(UUID)} at a given game time (QA checks the overdue path without waiting). */
    public static boolean running(UUID owner, long now) {
        PastSelf scene = SCENES.get(owner);
        if (scene == null) {
            return false;
        }
        if (scene.isRemoved()) {
            SCENES.remove(owner);
            return false;
        }
        if (scene.overdue(now)) {
            // Its chunk stopped ticking before the scene ended: end it here so the owner's next scene is not blocked.
            scene.discard();
            SCENES.remove(owner);
            return false;
        }
        return true;
    }

    /**
     * Starts the scene for {@code moment} now, with no rolls and no cooldown checks (the QA command and suite call
     * this directly). Counts the replay, starts both cooldowns, sends the vision and the line to the owner only.
     */
    public static @Nullable PastSelf stage(ServerPlayer player, LifeMoment moment) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        PastSelf body = ModEntities.PAST_SELF.get().create(level, EntitySpawnReason.EVENT);
        if (body == null) {
            return null;
        }
        long now = level.getGameTime();
        BlockPos bed = null;
        Vec3 standAt;
        List<BlockPos> looks = List.of();
        boolean bedGone = false;
        switch (moment.kind()) {
            case HOME -> {
                BlockState state = level.getBlockState(moment.pos());
                if (state.getBlock() instanceof BedBlock) {
                    bed = moment.pos();
                    standAt = besideBed(level, moment.pos(), state);
                } else {
                    bedGone = true;
                    standAt = Vec3.atBottomCenterOf(standable(level, moment.pos()));
                }
            }
            case BUILD -> {
                BlockPos spot = standable(level, moment.pos());
                standAt = Vec3.atBottomCenterOf(spot);
                looks = buildLooks(level, spot, moment.itemId());
            }
            default -> standAt = Vec3.atBottomCenterOf(standable(level, moment.pos()));
        }
        boolean othersSee = CommonConfig.REMEMBER_OTHERS_SEE.get();
        body.stage(player, moment, standAt, bed, looks, othersSee);
        if (!level.addFreshEntity(body)) {
            return null;
        }
        LifeMoments moments = LifeMoments.get(level.getServer());
        moments.replace(player.getUUID(), moment, moment.replayed(now));
        moments.markScene(player.getUUID(), now);
        LivingMemory.markWow(player);
        SCENES.put(player.getUUID(), body);
        PacketDistributor.sendToPlayer(player, new PastVisionPayload(PastSelf.length(moment.kind()), moment.kind().ordinal()));
        player.connection.send(new ClientboundSoundPacket(ModSounds.ECHO_WAKE, SoundSource.NEUTRAL, standAt.x, standAt.y, standAt.z, 0.7F, 0.75F, level.getRandom().nextLong()));
        if (CommonConfig.REMEMBER_WHISPER.get()) {
            player.sendOverlayMessage(whisper(moment, bedGone));
        }
        Mnemolith.LOGGER.info("Mnemolith remember scene {} for {} at {} (replay {})", moment.kind().getSerializedName(), player.getGameProfile().name(), moment.pos().toShortString(), moment.replays() + 1);
        return body;
    }

    /** The quiet line for a scene. Not a tutorial: it only says what happened here. */
    public static Component whisper(LifeMoment moment, boolean bedGone) {
        Component line = switch (moment.kind()) {
            case DEATH -> Component.translatable(moment.count() > 1 ? "mnemolith.remember.death_again" : "mnemolith.remember.death");
            case HOME -> Component.translatable(bedGone ? "mnemolith.remember.home_gone" : "mnemolith.remember.home");
            case BUILD -> Component.translatable("mnemolith.remember.build");
            case BATTLE -> {
                Identifier id = Identifier.tryParse(moment.detail());
                EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                yield type == null
                        ? Component.translatable("mnemolith.remember.battle_plain")
                        : Component.translatable("mnemolith.remember.battle", type.getDescription());
            }
        };
        return line.copy().withStyle(style -> style.withItalic(true).withColor(WHISPER_RGB));
    }

    /** Drops a player's unsaved state (logout). Saved moments stay. */
    public static void forgetSession(UUID player) {
        TALLIES.remove(player);
        SCENES.remove(player);
    }

    public static void clearSessions() {
        TALLIES.clear();
        SCENES.clear();
    }

    // ------------------------------------------------------------------ places

    /** Beside the bed's foot, on the side that is free; else at the foot end; else on the bed itself. */
    private static Vec3 besideBed(ServerLevel level, BlockPos head, BlockState state) {
        Direction facing = state.hasProperty(BedBlock.FACING) ? state.getValue(BedBlock.FACING) : Direction.NORTH;
        BlockPos foot = head.relative(facing.getOpposite());
        for (BlockPos spot : List.of(foot.relative(facing.getClockWise()), foot.relative(facing.getCounterClockWise()),
                head.relative(facing.getClockWise()), head.relative(facing.getCounterClockWise()), foot.relative(facing.getOpposite()))) {
            if (open(level, spot) && !open(level, spot.below())) {
                return Vec3.atBottomCenterOf(spot);
            }
        }
        return new Vec3(head.getX() + 0.5D, head.getY() + 0.5625D, head.getZ() + 0.5D);
    }

    /** The nearest spot within a few blocks up or down where a body fits and stands on something. */
    static BlockPos standable(ServerLevel level, BlockPos pos) {
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, 4}) {
            BlockPos spot = pos.above(dy);
            if (open(level, spot) && open(level, spot.above()) && !open(level, spot.below())) {
                return spot;
            }
        }
        return pos;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Up to eight blocks of the build near {@code spot}, its main block first, for the figure to glance at. */
    private static List<BlockPos> buildLooks(ServerLevel level, BlockPos spot, String mainBlock) {
        List<BlockPos> main = new ArrayList<>();
        List<BlockPos> other = new ArrayList<>();
        int r = 5;
        for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-r, -2, -r), spot.offset(r, 5, r))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || pos.distSqr(spot) < 2.0D) {
                continue;
            }
            if (!open(level, pos.above()) && !open(level, pos.north()) && !open(level, pos.south()) && !open(level, pos.east()) && !open(level, pos.west())) {
                continue;
            }
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            (id.equals(mainBlock) ? main : other).add(pos.immutable());
        }
        Collections.shuffle(main, new java.util.Random(spot.asLong()));
        Collections.shuffle(other, new java.util.Random(spot.asLong() ^ 0x5DEECE66DL));
        List<BlockPos> looks = new ArrayList<>(LOOK_TARGETS);
        for (List<BlockPos> list : List.of(main, other)) {
            Iterator<BlockPos> it = list.iterator();
            while (looks.size() < LOOK_TARGETS && it.hasNext()) {
                looks.add(it.next());
            }
        }
        return looks;
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** One open build tally. */
    private static final class Tally {
        private final String dimension;
        private final BlockPos anchor;
        private final long start;
        private int count;
        private long sumX;
        private long sumY;
        private long sumZ;
        private final Map<String, Integer> blocks = new HashMap<>();

        private Tally(String dimension, BlockPos anchor, long start) {
            this.dimension = dimension;
            this.anchor = anchor;
            this.start = start;
        }

        private void add(BlockPos pos, String block) {
            this.count++;
            this.sumX += pos.getX();
            this.sumY += pos.getY();
            this.sumZ += pos.getZ();
            if (this.blocks.size() < 64 || this.blocks.containsKey(block)) {
                this.blocks.merge(block, 1, Integer::sum);
            }
        }

        private String mostUsed() {
            String best = "";
            int most = 0;
            for (Map.Entry<String, Integer> entry : this.blocks.entrySet()) {
                if (entry.getValue() > most) {
                    most = entry.getValue();
                    best = entry.getKey();
                }
            }
            return best;
        }
    }
}
