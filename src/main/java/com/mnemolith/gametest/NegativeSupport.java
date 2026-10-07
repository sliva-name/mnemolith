package com.mnemolith.gametest;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.command.qa.QaSupport;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.MemoryAvatar;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Shared set-up for the negative tests: a cleared stone pad in a lane of its own (well clear of the suites' lanes and of
 * {@link RegressionTests}' lane 60), fixed fake players, and a real echo that belongs to one of them. Everything a test
 * creates is listed here and removed by {@link #cleanUp}, pass or fail.
 */
final class NegativeSupport implements AutoCloseable {
    private final GameTestHelper helper;
    private final ServerLevel level;
    private final int lane;
    private final List<ChunkPos> forced = new ArrayList<>();
    private final List<EchoEntity> echoes = new ArrayList<>();
    private final List<UUID> owners = new ArrayList<>();

    NegativeSupport(GameTestHelper helper, int lane) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.lane = lane;
    }

    ServerLevel level() {
        return this.level;
    }

    /** A 13x13 stone floor with six blocks of air above it, in chunk row {@code chunkOffset} of this lane. Memory cleared. */
    BlockPos pad(int chunkOffset) {
        BlockPos test = this.helper.absolutePos(BlockPos.ZERO).offset(0, 0, this.lane * SuiteTests.LANE_BLOCKS + chunkOffset * 64);
        BlockPos origin = QaSupport.column(this.level, test.getX() >> 4, test.getZ() >> 4);
        QaSupport.tickColumn(this.level, origin);
        this.forced.add(ChunkPos.containing(origin));
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                this.level.setBlock(origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
                for (int dy = 0; dy <= 5; dy++) {
                    this.level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
        LoadedChunkMemory.clear(this.level.getChunkAt(origin));
        return origin;
    }

    /** A fake player with a fixed id derived from {@code name}, survival, empty inventory, no possession, standing at {@code at}. */
    FakePlayer player(String name, BlockPos at) {
        UUID id = UUID.nameUUIDFromBytes(("mnemolith-negative:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(this.level, new GameProfile(id, name));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        player.containerMenu = player.inventoryMenu;
        player.stopUsingItem();
        player.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        EchoRegistry.get(this.level.getServer()).forget(id);
        this.owners.add(id);
        return player;
    }

    /** A live echo owned by {@code owner}, standing still at {@code at} (no replay), with {@code lesson}. */
    EchoEntity echo(ServerPlayer owner, BlockPos at, EchoLesson lesson) {
        MemoryAvatar.STAND_INS.put(owner.getUUID(), owner);
        EchoEntity echo = com.mnemolith.echo.EchoLife.spawn(this.level, owner, recording(owner, at), lesson);
        this.helper.assertTrue(echo != null, "could not spawn an echo for " + owner.getGameProfile().name());
        echo.stopReplay();
        echo.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        this.echoes.add(echo);
        return echo;
    }

    void track(EchoEntity echo) {
        this.echoes.add(echo);
    }

    /** One second of standing still at {@code at}. */
    EchoRecording recording(ServerPlayer owner, BlockPos at) {
        Vec3 origin = Vec3.atBottomCenterOf(at);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        return new EchoRecording(owner.getUUID(), owner.getGameProfile().name(), this.level.dimension(), origin, buffer.array(), List.of());
    }

    /** What the network layer hands a payload handler: only {@code player()} is answered. */
    static IPayloadContext context(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[] {IPayloadContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> player;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "NegativeSupport.context(" + player.getGameProfile().name() + ")";
                    default -> throw new UnsupportedOperationException("payload context: " + method.getName());
                });
    }

    @Override
    public void close() {
        for (EchoEntity echo : this.echoes) {
            if (!echo.isRemoved()) {
                echo.discard();
            }
        }
        this.echoes.clear();
        for (UUID owner : this.owners) {
            EchoRegistry.get(this.level.getServer()).forget(owner);
            MemoryAvatar.STAND_INS.remove(owner);
        }
        this.owners.clear();
        for (ChunkPos chunk : this.forced) {
            QaSupport.releaseColumn(this.level, chunk);
        }
        this.forced.clear();
    }
}
