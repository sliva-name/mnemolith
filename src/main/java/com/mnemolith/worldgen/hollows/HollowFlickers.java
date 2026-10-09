package com.mnemolith.worldgen.hollows;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
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

    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static long sent;

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
        if (player.getRandom().nextDouble() >= BASE_CHANCE * density) {
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

    /** Flickers sent since the server started (QA and perf notes). */
    public static long sentCount() {
        return sent;
    }

    public static void clear() {
        NEXT.clear();
        sent = 0;
    }
}
