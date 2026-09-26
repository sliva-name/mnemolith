package com.mnemolith.gametest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import com.mnemolith.Mnemolith;
import com.mnemolith.command.qa.QaSupport;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Real server players for the live tests. Each one joins through {@link net.minecraft.server.players.PlayerList#placeNewPlayer}
 * on an in-memory connection, so it is in {@code level.players()} like a connected client, and is ticked every game
 * tick the way the network layer ticks a player ({@link ServerPlayer#doTick}), so {@code PlayerTickEvent} and item use
 * run for real. Players and forced chunks are removed when the live batch ends, pass or fail.
 */
public final class LivePlayers {
    private static final List<ServerPlayer> JOINED = new ArrayList<>();
    private static final List<ChunkPos> FORCED = new ArrayList<>();

    private LivePlayers() {}

    /** Joins a survival player named {@code name} standing at {@code at}, ticked every tick until the test times out. */
    public static ServerPlayer join(GameTestHelper helper, String name, Vec3 at) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("mnemolith-gametest:" + name).getBytes(StandardCharsets.UTF_8)), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new TestPlayer(server, level, profile, cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // Negotiated as a NeoForge client with every Mnemolith channel, as a real modded client would be.
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        // What the client's "loaded" packet does: without it the player is invulnerable for 60 ticks.
        player.connection.markClientLoaded();
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
        JOINED.add(player);
        helper.onEachTick(() -> {
            if (!player.isRemoved() && !player.hasDisconnected()) {
                player.doTick();
            }
        });
        return player;
    }

    /** Keeps the chunk column of {@code pos} entity-ticking until the batch ends. */
    public static void force(ServerLevel level, BlockPos pos) {
        QaSupport.tickColumn(level, pos);
        FORCED.add(ChunkPos.containing(pos));
    }

    /** Standing position on the surface of chunk ({@code chunkX}, {@code chunkZ}) at its centre. */
    public static BlockPos surface(ServerLevel level, int chunkX, int chunkZ) {
        return QaSupport.column(level, chunkX, chunkZ);
    }

    static void cleanUp(ServerLevel level) {
        var list = level.getServer().getPlayerList();
        for (ServerPlayer player : JOINED) {
            if (list.getPlayer(player.getUUID()) == player) {
                list.remove(player);
            }
        }
        JOINED.clear();
        for (ChunkPos chunk : FORCED) {
            QaSupport.discardResidues(level, chunk.getMiddleBlockPosition(level.getSeaLevel()));
            AABB column = new AABB(chunk.getMinBlockX() - 16, level.getMinY(), chunk.getMinBlockZ() - 16, chunk.getMaxBlockX() + 17, level.getMaxY(), chunk.getMaxBlockZ() + 17);
            for (net.minecraft.world.entity.Entity entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, column,
                    e -> e instanceof com.mnemolith.entity.echo.ScarEntity || e instanceof net.minecraft.world.entity.monster.zombie.Zombie)) {
                entity.discard();
            }
            QaSupport.releaseColumn(level, chunk);
        }
        FORCED.clear();
        Mnemolith.LOGGER.info("Mnemolith gametest live batch cleaned up");
    }

    /**
     * A plain server player. Its own class only tells NeoForge's config sync that this connection never went through
     * the configuration phase (it skips non-vanilla player classes, as it does for vanilla's game test players).
     */
    private static final class TestPlayer extends ServerPlayer {
        TestPlayer(net.minecraft.server.MinecraftServer server, ServerLevel level, GameProfile profile, CommonListenerCookie cookie) {
            super(server, level, profile, cookie.clientInformation());
        }
    }

    /**
     * A live batch's environment: the teardown removes every joined player and forced chunk and ends any storm left.
     * Storm batches ({@code storms} true) each get their own environment, so they run one after another: storms are
     * capped per dimension ({@code maxStormsPerDimension}, 1 by default), and tests in one batch run side by side.
     */
    public record Environment(boolean storms) implements TestEnvironmentDefinition<Unit> {
        public static final MapCodec<Environment> CODEC = com.mojang.serialization.Codec.BOOL.optionalFieldOf("storms", false)
                .xmap(Environment::new, Environment::storms);

        public Environment() {
            this(false);
        }

        /** Without {@code storms}, natural storms are paused for the batch (long fractures must not start one). */
        @Override
        public Unit setup(ServerLevel level) {
            com.mnemolith.echo.storm.Storms.pauseNatural(!this.storms);
            return Unit.INSTANCE;
        }

        @Override
        public void teardown(ServerLevel level, Unit saveData) {
            cleanUp(level);
            com.mnemolith.echo.storm.StormData data = com.mnemolith.echo.storm.StormData.get(level.getServer());
            for (com.mnemolith.echo.storm.RecollectionStorm storm : data.storms()) {
                com.mnemolith.echo.storm.Storms.finish(level, storm, com.mnemolith.echo.storm.Storms.End.DISABLED);
            }
            com.mnemolith.echo.storm.Storms.pauseNatural(false);
        }

        @Override
        public MapCodec<Environment> codec() {
            return CODEC;
        }
    }
}
