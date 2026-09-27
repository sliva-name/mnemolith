package com.mnemolith.recall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.block.ArchiveVaultBlockEntity;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.ModEffects;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.RecallGhostPayload;
import com.mnemolith.vault.ArchiveVaults;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Stage 3. A place from this play can condense into an offer: a residue, a silhouette that walks toward it or aside
 * from it, and an echo that leans the same way. Mute, harvest, store and leave each close that place.
 * The server decides. Nothing here writes chat.
 */
public final class UseMemory {
    /** How close a player must be to the false place before the silhouette becomes a replicant. */
    private static final double STRIKE_SQR = 16.0D;
    /** The silhouette stays quiet once the player is already this close to where it points. */
    private static final double GUIDE_QUIET_SQR = 36.0D;
    /** A refused spot blocks another offer within two blocks. */
    private static final double REFUSED_SQR = 4.0D;
    private static final int REFUSED_CAP = 16;

    private static final Map<UUID, Long> GUIDE_AT = new HashMap<>();

    private UseMemory() {}

    public static boolean enabled() {
        return CommonConfig.USE_ENABLED.get();
    }

    public static void tick(ServerPlayer player) {
        if (!enabled() || !live(player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.hasEffect(ModEffects.UNRECORDED)) {
            return;
        }
        int phase = player.tickCount + (player.getId() & 31);
        if (phase % 40 != 0) {
            return;
        }
        if (open(player) == null) {
            form(player);
        }
        Legend legend = open(player);
        if (legend == null) {
            return;
        }
        if (!legend.dimension().equals(dimension(level))) {
            if (legend.witnessed()) {
                leave(player);
            }
            return;
        }
        witness(player);
        if (depart(player)) {
            return;
        }
        legend = open(player);
        if (legend == null || LoadedChunkMemory.isMuted(level, legend.pos())) {
            return;
        }
        approach(player);
        condense(player);
        guide(player);
    }

    public static void forgetSession(UUID player) {
        GUIDE_AT.remove(player);
    }

    public static void clearSessions() {
        GUIDE_AT.clear();
    }

    public static void forget(ServerPlayer player) {
        forgetSession(player.getUUID());
        if (player.level() instanceof ServerLevel level && level.getServer() != null) {
            Legend legend = open(player);
            if (legend != null) {
                discardResidues(level, player.getUUID(), legend.pos());
            }
            Legends.get(level.getServer()).forget(player.getUUID());
        }
    }

    /** The open offer, or null. A closed one stays saved but is not this. */
    public static @Nullable Legend open(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return null;
        }
        Legend legend = Legends.get(level.getServer()).sheet(player.getUUID()).current().orElse(null);
        return legend != null && legend.choice().open() ? legend : null;
    }

    public static boolean soured(ServerPlayer player) {
        return sheet(player).soured();
    }

    public static boolean spent(ServerPlayer player, AnchorKind kind) {
        return (sheet(player).spent() & bit(kind)) != 0;
    }

    public static int sealed(ServerPlayer player) {
        return sheet(player).sealed();
    }

    /** Dominant recorded gesture, or -1 when the log is empty. */
    public static int voiceOf(ServerPlayer player) {
        int[] counts = new int[GestureKind.values().length];
        for (Gesture gesture : LivingMemory.log(player).gestures()) {
            int ordinal = gesture.kind().ordinal();
            if (ordinal >= 0 && ordinal < counts.length) {
                counts[ordinal]++;
            }
        }
        int best = -1;
        int amount = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > amount) {
                amount = counts[i];
                best = i;
            }
        }
        return best;
    }

    /** Where a lie points: {@code lieOffset} blocks horizontally along {@code yaw}. A true offer stays on {@code pos}. */
    public static BlockPos aside(BlockPos pos, float yaw) {
        int offset = Math.max(2, CommonConfig.USE_LIE_OFFSET.get());
        Direction direction = Direction.fromYRot(yaw);
        if (!direction.getAxis().isHorizontal()) {
            direction = Direction.EAST;
        }
        return pos.relative(direction, offset);
    }

    /**
     * Condenses the strongest remaining anchor into an offer, if this play has enough of them and none is open.
     * A lie is rolled. The QA suite uses {@link #offer} so the roll is not part of the check.
     */
    public static @Nullable Legend form(ServerPlayer player) {
        if (!enabled() || !(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return null;
        }
        if (open(player) != null) {
            return open(player);
        }
        if (Investigate.size(player) < Math.max(1, CommonConfig.USE_MIN_ANCHORS.get())) {
            return null;
        }
        Anchor anchor = pick(player, level);
        if (anchor == null) {
            return null;
        }
        boolean lie = roll(player, anchor);
        return place(player, level, anchor.kind(), anchor.pos(), anchor.yaw(), lie);
    }

    /** Writes one open offer without the anchor search. Used by the QA suite and by a gamemaster demonstration. */
    public static @Nullable Legend offer(ServerPlayer player, AnchorKind kind, BlockPos pos, float yaw, boolean lie) {
        if (!enabled() || !(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return null;
        }
        return place(player, level, kind, pos, yaw, lie);
    }

    /** Spawns the legendary residue at the true place when the player is close and the chunk is not muted. */
    public static boolean condense(ServerPlayer player) {
        if (!enabled() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Legend legend = open(player);
        if (legend == null || !legend.dimension().equals(dimension(level))) {
            return false;
        }
        if (!level.isLoaded(legend.pos()) || LoadedChunkMemory.isMuted(level, legend.pos())) {
            return false;
        }
        double range = CommonConfig.USE_CONDENSE_RANGE.get();
        if (player.blockPosition().distSqr(legend.pos()) > range * range) {
            return false;
        }
        boolean spawned = false;
        if (trueResidue(level, player.getUUID(), legend.pos()) == null) {
            ImprintTag tag = ImprintTag.byOrdinal(legend.tag());
            int strength = CommonConfig.USE_RESIDUE_STRENGTH.get();
            ResidueEntity residue = Residues.spawn(level, Residues.airAbove(level, legend.pos()), tag, strength, false);
            if (residue != null) {
                residue.markLegend(player.getUUID());
                spawned = true;
                Mnemolith.LOGGER.info("Mnemolith legend condensed tag={} at {}", tag.getSerializedName(), legend.pos().toShortString());
            }
        }
        if (legend.lie()) {
            wash(player, level, legend);
        }
        return spawned;
    }

    /** A lie also leaves a pale residue at the false place. It cannot be kept. The true residue stays full color. */
    private static void wash(ServerPlayer player, ServerLevel level, Legend legend) {
        if (!level.isLoaded(legend.guide()) || washedNear(level, player.getUUID(), legend.guide()) != null) {
            return;
        }
        ImprintTag tag = ImprintTag.byOrdinal(legend.tag());
        int strength = CommonConfig.USE_RESIDUE_STRENGTH.get();
        ResidueEntity residue = Residues.spawn(level, Residues.airAbove(level, legend.guide()), tag, strength, false);
        if (residue == null) {
            return;
        }
        residue.markLegend(player.getUUID());
        residue.markWashed();
    }

    /**
     * A lie, and the player is standing on the false place: one replicant. A true offer does not strike.
     * Returns whether a replicant was spawned this call.
     */
    public static boolean approach(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Legend legend = open(player);
        if (legend == null || !legend.lie() || legend.struck() || !legend.dimension().equals(dimension(level))) {
            return false;
        }
        if (player.blockPosition().distSqr(legend.guide()) > STRIKE_SQR) {
            return false;
        }
        MomentReplicant replicant = MobSpawns.summonReplicant(level, legend.guide());
        if (replicant == null) {
            return false;
        }
        save(player, legend.withStruck());
        Mnemolith.LOGGER.info("Mnemolith legend lie struck at {}", legend.guide().toShortString());
        return true;
    }

    /** Marks the offer seen, once the player has stood near the true place. */
    public static boolean witness(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Legend legend = open(player);
        if (legend == null || legend.witnessed() || !legend.dimension().equals(dimension(level))) {
            return false;
        }
        double range = CommonConfig.USE_WITNESS_RANGE.get();
        if (player.blockPosition().distSqr(legend.pos()) > range * range) {
            return false;
        }
        save(player, legend.withWitnessed());
        return true;
    }

    /** Leaving a witnessed offer refuses that spot and makes the next one more likely to lie. */
    public static boolean depart(ServerPlayer player) {
        Legend legend = open(player);
        if (legend == null || !legend.witnessed() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!legend.dimension().equals(dimension(level))) {
            return leave(player);
        }
        double range = CommonConfig.USE_LEAVE_RANGE.get();
        if (player.blockPosition().distSqr(legend.pos()) <= range * range) {
            return false;
        }
        return leave(player);
    }

    public static boolean leave(ServerPlayer player) {
        return close(player, Choice.LEFT);
    }

    /** World path: fake players and spectators do not spend an offer by placing a mute stone. */
    public static boolean onMute(ServerPlayer player, BlockPos stone) {
        if (!live(player)) {
            return false;
        }
        return mute(player, stone);
    }

    /** A mute stone that hushes the offer's chunk spends it. The residue, if it had condensed, is gone. */
    public static boolean mute(ServerPlayer player, BlockPos stone) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Legend legend = open(player);
        if (legend == null || !legend.dimension().equals(dimension(level))) {
            return false;
        }
        int radius = Math.max(0, CommonConfig.MUTE_RADIUS_CHUNKS.get());
        ChunkPos offer = ChunkPos.containing(legend.pos());
        ChunkPos placed = ChunkPos.containing(stone);
        if (Math.abs(offer.x() - placed.x()) > radius || Math.abs(offer.z() - placed.z()) > radius) {
            return false;
        }
        return close(player, Choice.MUTED);
    }

    /** The needle took a legendary residue. An ordinary residue, and a washed lie, are ignored. */
    public static boolean onHarvest(ServerPlayer player, ResidueEntity residue) {
        if (!enabled() || residue.washed() || residue.legendOwner() == null || !residue.legendOwner().equals(player.getUUID())) {
            return false;
        }
        Legend legend = open(player);
        if (legend == null) {
            return false;
        }
        return close(player, Choice.TAKEN);
    }

    /**
     * Sneak with an empty hand on your legendary residue, and a vault with room nearby: the memory leaves the world
     * and sits in the vault. No vault, no store.
     */
    public static boolean store(ServerPlayer player, ResidueEntity residue) {
        if (!enabled() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (residue.washed() || residue.legendOwner() == null || !residue.legendOwner().equals(player.getUUID()) || open(player) == null) {
            return false;
        }
        double range = CommonConfig.USE_STORE_RANGE.get();
        BlockPos vaultPos = ArchiveVaults.nearestOpen(level, residue.blockPosition(), range);
        if (vaultPos == null || !(level.getBlockEntity(vaultPos) instanceof ArchiveVaultBlockEntity vault)) {
            return false;
        }
        long now = level.getGameTime();
        Imprint imprint = new Imprint(
                residue.tag(),
                residue.strength(),
                residue.origin(),
                java.util.Optional.of(player.getUUID()),
                Imprint.contextHash(residue.tag(), residue.origin(), now),
                now);
        if (!ArchiveVaults.keep(level, vaultPos, vault, imprint)) {
            return false;
        }
        if (!close(player, Choice.STORED)) {
            return false;
        }
        residue.discard();
        return true;
    }

    /** An echo learns the owner's recorded bearing, unless a costly choice has sealed it. */
    public static void stamp(EchoEntity echo, ServerPlayer owner) {
        if (!enabled()) {
            return;
        }
        int sealed = sealed(owner);
        int size = LivingMemory.log(owner).size();
        if (sealed >= 0 && size <= sealed) {
            echo.setBearing(-1);
            return;
        }
        echo.setBearing(voiceOf(owner));
    }

    /** An idle echo with a bearing looks toward the offer, true place or lie. */
    public static void glance(EchoEntity echo) {
        if (!enabled() || echo.bearing() < 0 || echo.ownerId() == null || !(echo.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getServer().getPlayerList().getPlayer(echo.ownerId()) instanceof ServerPlayer owner)) {
            return;
        }
        Legend legend = open(owner);
        if (legend == null || !legend.dimension().equals(dimension(level))) {
            return;
        }
        if (LoadedChunkMemory.isMuted(level, legend.pos())) {
            return;
        }
        double range = CommonConfig.USE_GUIDE_RANGE.get();
        if (echo.blockPosition().distSqr(legend.guide()) > range * range) {
            return;
        }
        echo.lookAt(Vec3.atBottomCenterOf(legend.guide()));
    }

    private static @Nullable Legend place(ServerPlayer player, ServerLevel level, AnchorKind kind, BlockPos pos, float yaw, boolean lie) {
        Legend previous = open(player);
        if (previous != null) {
            discardResidues(level, player.getUUID(), previous.pos());
        }
        int voice = voiceOf(player);
        BlockPos guide = lie ? aside(pos, yaw) : pos.immutable();
        Legend legend = new Legend(
                player.getUUID(),
                dimension(level),
                pos,
                guide,
                kind,
                tagFor(kind, voice).ordinal(),
                voice,
                lie,
                Choice.OPEN,
                false,
                false,
                level.getGameTime());
        Legends.Sheet sheet = sheet(player);
        Legends.get(level.getServer()).put(player.getUUID(), new Legends.Sheet(
                java.util.Optional.of(legend), sheet.spent(), sheet.soured(), sheet.sealed(), sheet.refused()));
        bear(player, level);
        Mnemolith.LOGGER.info("Mnemolith legend source={} lie={} at {}", kind.getSerializedName(), lie, pos.toShortString());
        return legend;
    }

    private static boolean close(ServerPlayer player, Choice choice) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return false;
        }
        Legend legend = open(player);
        if (legend == null) {
            return false;
        }
        Legends.Sheet sheet = sheet(player);
        int spent = sheet.spent();
        boolean soured = sheet.soured();
        int sealed = sheet.sealed();
        List<Legends.Spot> refused = Legends.trim(sheet.refused(), new Legends.Spot(legend.dimension(), legend.pos()), REFUSED_CAP);
        if (choice == Choice.LEFT) {
            soured = true;
        } else if (choice.spendsKind()) {
            spent |= bit(legend.source());
            sealed = LivingMemory.log(player).size();
            quiet(player, level);
        }
        discardResidues(level, player.getUUID(), legend.pos());
        Legends.get(level.getServer()).put(player.getUUID(), new Legends.Sheet(
                java.util.Optional.of(legend.withChoice(choice)), spent, soured, sealed, refused));
        Mnemolith.LOGGER.info("Mnemolith legend choice={} source={} at {}", choice.getSerializedName(), legend.source().getSerializedName(), legend.pos().toShortString());
        return true;
    }

    private static void save(ServerPlayer player, Legend legend) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return;
        }
        Legends.Sheet sheet = sheet(player);
        Legends.get(level.getServer()).put(player.getUUID(), new Legends.Sheet(
                java.util.Optional.of(legend), sheet.spent(), sheet.soured(), sheet.sealed(), sheet.refused()));
    }

    private static @Nullable Anchor pick(ServerPlayer player, ServerLevel level) {
        if (level.getServer() == null) {
            return null;
        }
        long now = level.getGameTime();
        long maxAge = CommonConfig.INVESTIGATE_MAX_AGE.get();
        int saturated = CommonConfig.SATURATED_THRESHOLD.get();
        Legends.Sheet sheet = sheet(player);
        Anchor loud = null;
        Anchor fracture = null;
        Anchor death = null;
        Anchor echo = null;
        Anchor mute = null;
        Anchor trace = null;
        for (Anchor anchor : PlayAnchors.get(level.getServer()).of(player.getUUID())) {
            if ((sheet.spent() & bit(anchor.kind())) != 0 || refused(sheet, anchor)) {
                continue;
            }
            if (!anchor.kind().sticky() && maxAge > 0L && now - anchor.gameTime() > maxAge) {
                continue;
            }
            switch (anchor.kind()) {
                case LOUD -> {
                    if (anchor.pressure() >= saturated && (loud == null || anchor.pressure() > loud.pressure())) {
                        loud = anchor;
                    }
                }
                case FRACTURE -> {
                    if (fracture == null || anchor.gameTime() > fracture.gameTime()) {
                        fracture = anchor;
                    }
                }
                case DEATH -> death = anchor;
                case ECHO -> echo = anchor;
                case MUTE -> mute = anchor;
                case FLASH, RECALL -> {
                    if (trace == null || anchor.gameTime() > trace.gameTime()) {
                        trace = anchor;
                    }
                }
                default -> {
                }
            }
        }
        if (loud != null) {
            return loud;
        }
        if (fracture != null) {
            return fracture;
        }
        if (death != null) {
            return death;
        }
        if (echo != null) {
            return echo;
        }
        if (mute != null) {
            return mute;
        }
        return trace;
    }

    private static boolean refused(Legends.Sheet sheet, Anchor anchor) {
        for (Legends.Spot spot : sheet.refused()) {
            if (spot.dimension().equals(anchor.dimension()) && spot.pos().distSqr(anchor.pos()) <= REFUSED_SQR) {
                return true;
            }
        }
        return false;
    }

    private static boolean roll(ServerPlayer player, Anchor anchor) {
        double chance = sheet(player).soured() ? CommonConfig.USE_LIE_AFTER_LEAVE.get() : CommonConfig.USE_LIE_CHANCE.get();
        if (anchor.distorted()) {
            chance = Math.max(chance, CommonConfig.USE_LIE_AFTER_LEAVE.get());
        }
        return player.getRandom().nextDouble() < chance;
    }

    private static ImprintTag tagFor(AnchorKind kind, int voice) {
        return switch (kind) {
            case DEATH -> ImprintTag.DEATH;
            case MUTE -> ImprintTag.SILENCE;
            case FRACTURE -> ImprintTag.EXPLOSION;
            case ECHO -> ImprintTag.FIRE;
            default -> tagForVoice(voice);
        };
    }

    private static ImprintTag tagForVoice(int voice) {
        if (voice < 0 || voice >= GestureKind.values().length) {
            return ImprintTag.FIRE;
        }
        return switch (GestureKind.values()[voice]) {
            case ATTACK -> ImprintTag.DEATH;
            case PLACE -> ImprintTag.FIRE;
            case USE -> ImprintTag.SILENCE;
            case FALL -> ImprintTag.FALL;
        };
    }

    private static void guide(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || player instanceof FakePlayer) {
            return;
        }
        Legend legend = open(player);
        if (legend == null) {
            return;
        }
        double range = CommonConfig.USE_GUIDE_RANGE.get();
        double distance = player.blockPosition().distSqr(legend.guide());
        if (distance > range * range || distance <= GUIDE_QUIET_SQR) {
            return;
        }
        long now = level.getGameTime();
        long last = GUIDE_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now - last < CommonConfig.USE_GUIDE_COOLDOWN.get()) {
            return;
        }
        GUIDE_AT.put(player.getUUID(), now);
        BlockPos end = legend.guide();
        BlockPos start = player.blockPosition();
        List<BlockPos> trail = new ArrayList<>(3);
        for (int i = 1; i <= 3; i++) {
            trail.add(new BlockPos(
                    start.getX() + (end.getX() - start.getX()) * i / 4,
                    start.getY(),
                    start.getZ() + (end.getZ() - start.getZ()) * i / 4));
        }
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        int kind = legend.voice() < 0 ? 0 : legend.voice();
        PacketDistributor.sendToPlayer(player, new RecallGhostPayload(end, yaw, 0.0F, kind, legend.lie(), trail));
    }

    private static void bear(ServerPlayer player, ServerLevel level) {
        double range = CommonConfig.USE_GUIDE_RANGE.get();
        AABB box = player.getBoundingBox().inflate(range);
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, box, echo -> player.getUUID().equals(echo.ownerId()))) {
            stamp(echo, player);
        }
    }

    private static void quiet(ServerPlayer player, ServerLevel level) {
        double range = CommonConfig.USE_GUIDE_RANGE.get();
        AABB box = player.getBoundingBox().inflate(Math.max(range, 64.0D));
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, box, echo -> player.getUUID().equals(echo.ownerId()))) {
            echo.setBearing(-1);
        }
    }

    private static @Nullable ResidueEntity trueResidue(ServerLevel level, UUID owner, BlockPos pos) {
        AABB box = new AABB(pos).inflate(reach());
        for (ResidueEntity residue : level.getEntitiesOfClass(ResidueEntity.class, box, entity -> owner.equals(entity.legendOwner()) && !entity.washed())) {
            return residue;
        }
        return null;
    }

    private static @Nullable ResidueEntity washedNear(ServerLevel level, UUID owner, BlockPos pos) {
        AABB box = new AABB(pos).inflate(6.0D);
        for (ResidueEntity residue : level.getEntitiesOfClass(ResidueEntity.class, box, entity -> owner.equals(entity.legendOwner()) && entity.washed())) {
            return residue;
        }
        return null;
    }

    private static void discardResidues(ServerLevel level, UUID owner, BlockPos pos) {
        AABB box = new AABB(pos).inflate(reach());
        for (ResidueEntity residue : level.getEntitiesOfClass(ResidueEntity.class, box, entity -> owner.equals(entity.legendOwner()))) {
            residue.discard();
        }
    }

    /** Far enough to include the false place when a lie offset is at its config maximum. */
    private static double reach() {
        return Math.max(24.0D, CommonConfig.USE_LIE_OFFSET.get() + 8.0D);
    }

    private static Legends.Sheet sheet(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            return Legends.Sheet.EMPTY;
        }
        return Legends.get(level.getServer()).sheet(player.getUUID());
    }

    private static int bit(AnchorKind kind) {
        return 1 << kind.ordinal();
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /** Fake players and spectators do not form or spend offers through the player tick or a placed mute stone. */
    private static boolean live(ServerPlayer player) {
        return !(player instanceof FakePlayer) && !player.isSpectator();
    }
}
