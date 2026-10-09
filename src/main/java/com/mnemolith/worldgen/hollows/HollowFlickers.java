package com.mnemolith.worldgen.hollows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.content.item.RecolliteLensItem;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Memory flickers: once a second, each Overworld player standing in Memory Hollows may see a short ghostly replay
 * of an imprint held by a chunk nearby. The server picks the spot and the scene; clients draw it
 * ({@code client.hollows.FlickerRenderer}). No scan: per player one biome read, then for the spot one height read, one
 * biome read and the chunk's imprint list.
 */
public final class HollowFlickers {
    public static final int WALK = 0;
    public static final int WORK = 1;
    public static final int FALL = 2;
    public static final int KNEEL = 3;
    public static final int FLARE = 4;
    public static final int SCENES = 5;
    /** Not a scene: sent in place of one to tell clients the flicker at {@code pos} was caught and should dissolve. */
    public static final int CAUGHT = -1;

    /** Checked every this many ticks. */
    public static final int PERIOD = 20;
    /** Chance per check at density 1.0 (density 4 makes it certain). */
    public static final double BASE_CHANCE = 0.25D;
    /** Ticks after a flicker before the same player can get another. */
    public static final int COOLDOWN = 60;
    public static final int MIN_DISTANCE = 6;
    public static final int MAX_DISTANCE = 20;
    /** Players within this many blocks of the spot see the same flicker. */
    public static final double AUDIENCE = 48.0D;

    /** Ticks a flicker can be caught after it appears: the client draws it for 80 ticks, plus a little latency. */
    public static final int CATCH_WINDOW = 90;
    /** How far from the player a flicker can be caught: with a chronicle lens, and with a recollite lens. */
    public static final double LENS_REACH = 5.0D;
    public static final double RECOLLITE_REACH = 9.0D;
    /** Flicker chance multiplier for a player holding a recollite lens. */
    public static final double RECOLLITE_CHANCE = 1.5D;
    private static final int MAX_ACTIVE = 256;
    static final net.minecraft.resources.Identifier CAUGHT_ADVANCEMENT =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "flicker_caught");

    /** A flicker that is still showing: where, which imprint tag ordinal (-1: none) and until which game tick. */
    public record Active(BlockPos pos, int tag, long until) {}

    /** What a catch attempt did. */
    public enum Catch {
        /** No flicker showing within reach. */
        NONE,
        /** The nearest flicker replays no imprint: there is nothing to hold. */
        FAINT,
        /** The flicker's imprint is no longer in its chunk, or the player had no room for the slip. */
        GONE,
        CAUGHT
    }

    /** A catch attempt and, when caught, the tag of the slip. */
    public record CatchResult(Catch kind, @Nullable ImprintTag tag) {
        static final CatchResult NOTHING = new CatchResult(Catch.NONE, null);
    }

    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static long sent;
    private static long caught;

    private HollowFlickers() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD != 7) {
            return;
        }
        double density = CommonConfig.HOLLOW_FLICKER_DENSITY.get();
        if (density <= 0.0D) {
            return;
        }
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            offer(level, player, density, now);
        }
    }

    /** One roll for one player: cooldown, chance, biome, spot. Returns whether a flicker was sent. */
    public static boolean offer(ServerLevel level, ServerPlayer player, double density, long now) {
        if (density <= 0.0D || player.isSpectator()) {
            return false;
        }
        Long next = NEXT.get(player.getUUID());
        if (next != null && now < next) {
            return false;
        }
        double chance = BASE_CHANCE * density * (RecolliteLensItem.holds(player) ? RECOLLITE_CHANCE : 1.0D);
        if (player.getRandom().nextDouble() >= chance) {
            return false;
        }
        if (!Hollows.is(level.getBiome(player.blockPosition()))) {
            return false;
        }
        HollowFlickerPayload payload = pick(level, player.blockPosition(), player.getRandom());
        if (payload == null) {
            return false;
        }
        NEXT.put(player.getUUID(), now + COOLDOWN);
        send(level, payload);
        return true;
    }

    public static void send(ServerLevel level, HollowFlickerPayload payload) {
        BlockPos at = payload.pos();
        PacketDistributor.sendToPlayersNear(level, null, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, AUDIENCE, payload);
        if (payload.scene() == CAUGHT) {
            return;
        }
        long now = level.getGameTime();
        ACTIVE.removeIf(active -> active.until() < now);
        if (ACTIVE.size() >= MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(new Active(at.immutable(), payload.tag(), now + CATCH_WINDOW));
        sent++;
        Mnemolith.LOGGER.debug("Mnemolith hollow flicker at {} scene={} tag={}", at.toShortString(), payload.scene(), payload.tag());
    }

    /** A spot 6 to 20 blocks from {@code around}, on loaded ground inside the biome, and its scene; null when none. */
    public static @Nullable HollowFlickerPayload pick(ServerLevel level, BlockPos around, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
        int x = around.getX() + Mth.floor(Math.cos(angle) * distance);
        int z = around.getZ() + Mth.floor(Math.sin(angle) * distance);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos spot = new BlockPos(x, y, z);
        if (!level.getFluidState(spot.below()).isEmpty() || !Hollows.is(level.getBiome(spot))) {
            return null;
        }
        LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
        ImprintTag tag = imprintAt(LoadedChunkMemory.existing(chunk), random);
        float yaw = random.nextFloat() * 360.0F;
        return new HollowFlickerPayload(spot, yaw, scene(tag), tag == null ? -1 : tag.ordinal());
    }

    /** One of the chunk's imprints, the louder ones likelier; null when the chunk holds none. */
    public static @Nullable ImprintTag imprintAt(@Nullable ChunkMemory memory, RandomSource random) {
        if (memory == null || memory.imprintCount() == 0) {
            return null;
        }
        int count = memory.imprintCount();
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += Math.max(1, memory.imprintAt(i).intensity());
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < count; i++) {
            roll -= Math.max(1, memory.imprintAt(i).intensity());
            if (roll < 0) {
                return memory.imprintAt(i).tag();
            }
        }
        return memory.imprintAt(count - 1).tag();
    }

    /** The scene a tag plays. No imprint: a quiet walk. */
    public static int scene(@Nullable ImprintTag tag) {
        if (tag == null) {
            return WALK;
        }
        return switch (tag) {
            case FALL, EXPLOSION -> FALL;
            case DEATH, BOSS, SILENCE, SCULK -> KNEEL;
            case FIRE, LIGHTNING -> FLARE;
            case BUILD, REDSTONE, TRADE -> WORK;
            case PATH, PLAYER, PORTAL -> WALK;
        };
    }

    /**
     * Needle in one hand, lens in the other: the nearest flicker still showing within the lens's reach is pulled into a
     * slip of its tag. The imprint comes out of the flicker's chunk as an extraction would take it, so each flicker is
     * caught once and a chunk is not a free slip source.
     */
    public static CatchResult tryCatch(ServerLevel level, ServerPlayer player) {
        if (!ChronicleLensItem.isHeld(player)) {
            return CatchResult.NOTHING;
        }
        double reach = RecolliteLensItem.holds(player) ? RECOLLITE_REACH : LENS_REACH;
        long now = level.getGameTime();
        Active nearest = null;
        double best = reach * reach;
        for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext();) {
            Active active = it.next();
            if (active.until() < now) {
                it.remove();
                continue;
            }
            double d = player.distanceToSqr(active.pos().getX() + 0.5D, active.pos().getY() + 0.9D, active.pos().getZ() + 0.5D);
            if (d <= best) {
                best = d;
                nearest = active;
            }
        }
        if (nearest == null) {
            return CatchResult.NOTHING;
        }
        if (nearest.tag() < 0 || nearest.tag() >= ImprintTag.values().length) {
            return new CatchResult(Catch.FAINT, null);
        }
        ImprintTag tag = ImprintTag.values()[nearest.tag()];
        if (ImprintWriter.catchFlicker(level, nearest.pos(), player, tag).isEmpty()) {
            ACTIVE.remove(nearest);
            return new CatchResult(Catch.GONE, tag);
        }
        ACTIVE.remove(nearest);
        send(level, new HollowFlickerPayload(nearest.pos(), 0.0F, CAUGHT, nearest.tag()));
        caught++;
        net.minecraft.advancements.AdvancementHolder advancement = level.getServer().getAdvancements().get(CAUGHT_ADVANCEMENT);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "caught");
        }
        Mnemolith.LOGGER.debug("Mnemolith hollow flicker caught at {} tag={} by {}", nearest.pos().toShortString(), tag, player.getName().getString());
        return new CatchResult(Catch.CAUGHT, tag);
    }

    /** Flickers still showing (QA). */
    public static List<Active> active() {
        return List.copyOf(ACTIVE);
    }

    /** Flickers caught since the server started (QA). */
    public static long caughtCount() {
        return caught;
    }

    /** Flickers sent since the server started (QA and perf notes). */
    public static long sentCount() {
        return sent;
    }

    public static void clear() {
        NEXT.clear();
        ACTIVE.clear();
        sent = 0;
        caught = 0;
    }
}
