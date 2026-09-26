package com.mnemolith.command.qa;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import com.mnemolith.Mnemolith;
import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.Composition;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.ai.PathLedger;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.world.LoadedChunkMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Shared chunk, player, and replicant helpers for the checklist, smoke, mpsmoke, and perf commands. */
public final class QaSupport {
    private QaSupport() {}

    static boolean walk(ServerLevel level, FakePlayer player, BlockPos pos) {
        BlockPos next = pos.offset(16, 0, 0);
        level.getChunkAt(pos);
        level.getChunkAt(next);
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        PathLedger.note(player);
        player.setPos(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D);
        PathLedger.note(player);
        return hasTag(level, pos, ImprintTag.PATH) || hasTag(level, next, ImprintTag.PATH);
    }

    static boolean writeTag(ServerLevel level, BlockPos pos, ImprintTag tag) {
        clear(level, pos);
        return ImprintWriter.tryWrite(level, pos, tag, null, false) && hasTag(level, pos, tag);
    }

    /** Two slips in a temporary container, composed as if at {@code pos}. */
    static ComposeResult compose(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, ImprintTag first, ImprintTag second) {
        SimpleContainer container = new SimpleContainer(3);
        container.setItem(0, ImprintSlips.of(first, pos));
        container.setItem(1, ImprintSlips.of(second, pos));
        return Composition.compose(level, pos, player, container);
    }

    static boolean resourceContains(ServerLevel level, String path, String needle) {
        Identifier id = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
            return text.indexOf(needle) >= 0;
        } catch (IOException ex) {
            return false;
        }
    }

    static boolean holdsTag(ServerPlayer player, ImprintTag tag) {
        return ImprintSlips.holdsTag(player, tag);
    }

    public static boolean hasTag(ServerLevel level, BlockPos pos, ImprintTag tag) {
        ChunkMemory memory = memory(level, pos);
        return memory != null && memory.tags().contains(tag);
    }

    static ChunkMemory memory(ServerLevel level, BlockPos pos) {
        return LoadedChunkMemory.existing(level.getChunkAt(pos));
    }

    public static void clear(ServerLevel level, BlockPos pos) {
        LoadedChunkMemory.clear(level.getChunkAt(pos));
    }

    static void reset(ServerPlayer player) {
        player.getInventory().clearContent();
        player.setData(ModAttachments.DISCOVERY.get(), new Discovery());
    }

    public static BlockPos column(ServerLevel level, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        // Load the chunk first: the heightmap of a chunk that is not loaded reads as the bottom of the world, and the
        // sea-level fallback below then puts the site wherever sea level happens to be (inside a hill, or under a
        // superflat world's floor).
        level.getChunk(chunkX, chunkZ);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinY()) {
            y = level.getSeaLevel();
        }
        return new BlockPos(x, Math.min(level.getMaxY() - 2, y + 1), z);
    }

    static int replicantCount(ServerLevel level, BlockPos pos) {
        return replicants(level, pos).size();
    }

    public static void discardReplicants(ServerLevel level, BlockPos pos) {
        for (MomentReplicant replicant : replicants(level, pos)) {
            replicant.discard();
        }
    }

    /** Removes residual echoes in the chunk column of {@code pos} (and one chunk around), so suites stay isolated. */
    public static void discardResidues(ServerLevel level, BlockPos pos) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos).inflate(24.0D, 64.0D, 24.0D);
        for (com.mnemolith.entity.echo.ResidueEntity residue : level.getEntitiesOfClass(com.mnemolith.entity.echo.ResidueEntity.class, box)) {
            residue.discard();
        }
    }

    /**
     * Loads the column as entity-ticking. A plain {@code getChunk} leaves far columns hidden from entity queries,
     * and the tracking promotion is queued on the server thread after the chunk future completes.
     */
    public static void tickColumn(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = ChunkPos.containing(pos);
        var future = level.getChunkSource().addTicketAndLoadWithRadius(TicketType.FORCED, chunk, 2);
        var server = level.getServer();
        server.managedBlock(future::isDone);
        long deadline = System.nanoTime() + 2_000_000_000L;
        server.managedBlock(() -> level.isPositionEntityTicking(pos) || System.nanoTime() > deadline);
    }

    /**
     * Lets the light engine finish the updates queued by blocks a check just carved or placed. Checks run inside one
     * server tick, so without this a freshly flattened area keeps its old light (0 where there was stone), and
     * light-dependent blocks such as crops pop or refuse to be planted. Bounded by a 3 second deadline.
     */
    static void settleLight(ServerLevel level, BlockPos pos) {
        var engine = level.getChunkSource().getLightEngine();
        engine.tryScheduleUpdate();
        var future = engine.waitForPendingTasks(pos.getX() >> 4, pos.getZ() >> 4);
        long deadline = System.nanoTime() + 3_000_000_000L;
        level.getServer().managedBlock(() -> future.isDone() || System.nanoTime() > deadline);
    }

    public static void releaseColumn(ServerLevel level, ChunkPos chunk) {
        level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, chunk, 2);
    }

    static List<MomentReplicant> replicants(ServerLevel level, BlockPos pos) {
        return MomentReplicant.inColumn(level, pos);
    }

    static int count(boolean... flags) {
        int passed = 0;
        for (boolean flag : flags) {
            if (flag) {
                passed++;
            }
        }
        return passed;
    }
}
