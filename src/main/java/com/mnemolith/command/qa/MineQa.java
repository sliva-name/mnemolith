package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.MemoryAvatar;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.EchoJobPayload;
import com.mnemolith.network.EchoNetwork;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith mineqa}. The echo mining job on the shapes players actually teach: a lesson of plain stone
 * (the target is also tunnel material) over a floor it must dig down into, and an ore lesson with a continuous
 * horizontal vein and a vein straight down under the echo. Every target in the radius must be mined, none skipped.
 */
public final class MineQa {
    private static final UUID OWNER = UUID.fromString("57575757-5757-5757-5757-575757575757");
    private static final int MAX_TICKS = 12000;
    private static int salt;

    private MineQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        BlockPos a = column(level, (spawn.getX() >> 4) - 40 - salt * 4, (spawn.getZ() >> 4) + 20);
        BlockPos b = column(level, (a.getX() >> 4) - 2, a.getZ() >> 4);
        tickColumn(level, a);
        tickColumn(level, b);
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(OWNER, "MineQaOwner"));
        MemoryAvatar.STAND_INS.put(OWNER, owner);
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        List<String> notes = new ArrayList<>();
        boolean digsDown = false, noSkip = false, oreLine = false, oreDown = false, nearDeep = false, nearShallow = false;
        try {
            // ---------- A: a stone lesson over a stone floor, radius 2 ----------
            BlockPos floor = a;
            box(level, floor, 5, -8, 6, Blocks.OBSIDIAN.defaultBlockState(), Blocks.STONE.defaultBlockState());
            List<BlockPos> stoneTargets = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -2; dy <= -1; dy++) {
                        stoneTargets.add(floor.offset(dx, dy, dz));
                    }
                }
            }
            Result ra = work(level, owner, floor, Blocks.STONE, 2, stoneTargets);
            int deepLeft = 0, upperLeft = 0;
            for (BlockPos p : stoneTargets) {
                if (!level.getBlockState(p).isAir()) {
                    if (p.getY() == floor.getY() - 2) deepLeft++; else upperLeft++;
                }
            }
            digsDown = deepLeft == 0;
            noSkip = upperLeft == 0 && deepLeft == 0;
            notes.add("stoneFloor " + ra.describe() + " leftTop=" + upperLeft + " leftDeep=" + deepLeft);

            // ---------- B: an iron lesson in solid stone: a line at the feet and a vein straight down ----------
            BlockPos pit = b;
            box(level, pit, 9, -10, 5, Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState());
            level.setBlock(pit, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pit.above(), Blocks.AIR.defaultBlockState(), 2);
            List<BlockPos> line = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                line.add(pit.offset(i, 0, 0));
            }
            List<BlockPos> down = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                down.add(pit.below(i));
            }
            List<BlockPos> ores = new ArrayList<>(line);
            ores.addAll(down);
            for (BlockPos p : ores) {
                level.setBlock(p, Blocks.IRON_ORE.defaultBlockState(), 2);
            }
            Result rb = work(level, owner, pit, Blocks.IRON_ORE, 8, ores);
            int lineLeft = (int) line.stream().filter(p -> !level.getBlockState(p).isAir()).count();
            int downLeft = (int) down.stream().filter(p -> !level.getBlockState(p).isAir()).count();
            oreLine = lineLeft == 0;
            oreDown = downLeft == 0;
            notes.add("ores " + rb.describe() + " lineLeft=" + lineLeft + " downLeft=" + downLeft);
            // ---------- C: a stone lesson at the default radius 16 over a large stone mass ----------
            // Two fixed heights inside a chunk section: the first 256 stone cells in section storage order are a flat
            // sheet at the section's bottom layer, 6 blocks under the echo (it gave up: unreachable) or right under
            // its feet (it only mined that layer, skipping the rest). The nearest cells must be taken first.
            nearDeep = bigStone(level, owner, a, -4, 6, notes);
            nearShallow = bigStone(level, owner, a, -8, 1, notes);
        } catch (RuntimeException e) {
            Mnemolith.LOGGER.error("Mnemolith mineqa failed", e);
            notes.add("exception " + e);
        } finally {
            cleanup(level, owner, a);
            cleanup(level, owner, b);
            MemoryAvatar.STAND_INS.remove(OWNER);
            releaseColumn(level, ChunkPos.containing(a));
            releaseColumn(level, ChunkPos.containing(b));
        }
        String[] names = {"stoneDigsDown", "stoneNoSkip", "oreLine", "oreDown", "nearFirstDeep", "nearFirstShallow"};
        return new QaReport("mineqa", names, new boolean[] {digsDown, noSkip, oreLine, oreDown, nearDeep, nearShallow}, notes).log();
    }

    private static final int BIG_MINED = 120;

    /**
     * A 37x37 stone mass 20 deep, the echo on top with the anchor {@code offset} blocks above a section's bottom.
     * After {@link #BIG_MINED} blocks the job must still be mining, every opened cell must be near the work point
     * (the nearest 256 lie within ~5 blocks; nothing farther than 6), and it must have dug at least 3 down.
     */
    private static boolean bigStone(ServerLevel level, FakePlayer owner, BlockPos a, int chunksWest, int offset, List<String> notes) {
        BlockPos surface = column(level, (a.getX() >> 4) + chunksWest, a.getZ() >> 4);
        int y = ((surface.getY() >> 4) << 4) + 16 + offset;
        if (y + 8 > level.getMaxY()) {
            y -= 32;
        }
        BlockPos big = new BlockPos(surface.getX(), y, surface.getZ());
        tickColumn(level, big);
        try {
            box(level, big, 18, -20, 6, Blocks.OBSIDIAN.defaultBlockState(), Blocks.STONE.defaultBlockState());
            Result r = work(level, owner, big, Blocks.STONE, 16, List.of(), BIG_MINED);
            int opened = 0, far = 0, deepest = 0;
            for (BlockPos p : BlockPos.betweenClosed(big.offset(-18, -20, -18), big.offset(18, -1, 18))) {
                if (level.getBlockState(p).isAir()) {
                    opened++;
                    if (p.distSqr(big) > 36) {
                        far++;
                    }
                    deepest = Math.min(deepest, p.getY() - big.getY());
                }
            }
            boolean ok = r.mined() >= BIG_MINED && "mining".equals(r.status()) && far == 0 && deepest <= -3;
            notes.add("nearFirst offset=" + offset + " status=" + r.status() + " mined=" + r.mined() + " ticks=" + r.ticks() + " opened=" + opened
                    + " farOpened=" + far + " deepest=" + deepest + " end=" + r.end());
            return ok;
        } finally {
            cleanup(level, owner, big);
            releaseColumn(level, ChunkPos.containing(big));
        }
    }

    private record Result(String status, int mined, int ticks, List<String> order, String end) {
        String describe() {
            return "status=" + this.status + " mined=" + this.mined + " ticks=" + this.ticks + " end=" + this.end + " order=" + this.order;
        }
    }

    /** Spawns an echo taught {@code block} at {@code at}, starts mining with {@code radius}, and drives it until it stops. */
    private static Result work(ServerLevel level, FakePlayer owner, BlockPos at, Block block, int radius, List<BlockPos> watch) {
        return work(level, owner, at, block, radius, watch, Integer.MAX_VALUE);
    }

    private static Result work(ServerLevel level, FakePlayer owner, BlockPos at, Block block, int radius, List<BlockPos> watch, int stopAfter) {
        EchoLesson lesson = new EchoLesson(20, 4, 0, 0, List.of(new EchoLesson.MineTarget(block, 4)), Optional.empty());
        EchoEntity echo = EchoLife.spawn(level, owner, plainRecording(level, at), lesson);
        if (echo == null) {
            return new Result("spawn failed", 0, 0, List.of(), "-");
        }
        echo.stopReplay();
        echo.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        echo.inventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        owner.snapTo(Vec3.atBottomCenterOf(at.above(3)));
        EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.RADIUS, BlockPos.ZERO, radius));
        EchoNetwork.applyJob(owner, EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.MODE_MINE));
        List<String> order = new ArrayList<>();
        boolean[] gone = new boolean[watch.size()];
        int ticks = 0;
        while (ticks < MAX_TICKS && echo.isAlive() && echo.job().mode() == EchoJob.Mode.MINE && echo.job().mined() < stopAfter) {
            level.tickNonPassenger(echo);
            ticks++;
            for (int i = 0; i < watch.size(); i++) {
                if (!gone[i] && level.getBlockState(watch.get(i)).isAir()) {
                    gone[i] = true;
                    BlockPos d = watch.get(i).subtract(at);
                    order.add(d.getX() + "," + d.getY() + "," + d.getZ());
                }
            }
        }
        BlockPos rel = echo.blockPosition().subtract(at);
        Result r = new Result(echo.job().status().kind().getSerializedName(), echo.job().mined(), ticks, order,
                rel.getX() + "," + rel.getY() + "," + rel.getZ());
        echo.discardSilently();
        return r;
    }

    /** Fills a (2r+1) square from {@code bottom} to -1 with {@code fill}, walls of {@code wall} one block outside, air from 0 to {@code top}. */
    private static void box(ServerLevel level, BlockPos c, int r, int bottom, int top, BlockState wall, BlockState fill) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                boolean edge = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
                for (int dy = bottom - 1; dy <= top; dy++) {
                    BlockState s;
                    if (dy == bottom - 1) s = wall;
                    else if (dy < 0) s = edge ? wall : fill;
                    else s = Blocks.AIR.defaultBlockState();
                    level.setBlock(c.offset(dx, dy, dz), s, 2);
                }
            }
        }
    }

    private static EchoRecording plainRecording(ServerLevel level, BlockPos base) {
        Vec3 origin = Vec3.atBottomCenterOf(base);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        return new EchoRecording(OWNER, "MineQaOwner", level.dimension(), origin, buffer.array(), List.of());
    }

    private static void cleanup(ServerLevel level, FakePlayer owner, BlockPos base) {
        if (EchoPossession.isPossessing(owner)) {
            EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
        }
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, new AABB(base).inflate(48.0D), e -> e.isOwnedBy(OWNER))) {
            echo.discardSilently();
        }
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(48.0D))) {
            drop.discard();
        }
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.getInventory().clearContent();
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
    }
}
