package com.mnemolith.echo.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Grid path search for a working echo, spread over ticks. Nodes are feet positions. Moves: walk to a side, step up one,
 * step down one, drop down up to three, and diagonal walks around no corners. A cell that is not open may be dug when
 * a {@link Digger} allows it (natural stone, deepslate, dirt), up to a fixed number of dug cells per path.
 * <p>
 * Every block read checks that the chunk is loaded first; an unloaded chunk counts as a wall, so a search never loads
 * chunks.
 * <p>
 * Stage 3 adds: wading through shallow water (feet in water on a solid bottom, head in air), climbing ladders and vines
 * straight up and down, and walking straight through wooden doors and fence gates along their passage axis (the
 * {@link Walker} opens them with the owner's fake player, so protection events apply, and closes them behind).
 */
public final class EchoNav {
    public static final int MAX_DROP = 3;
    private static final float DIG_COST = 4.0F;
    private static final float WATER_COST = 2.0F;
    private static final float DOOR_COST = 1.0F;

    private EchoNav() {}

    public interface Goal {
        boolean reached(BlockPos feet);

        /** Admissible-ish estimate of the remaining cost. */
        double estimate(BlockPos feet);
    }

    public interface Digger {
        boolean canDig(BlockPos pos, BlockState state);
    }

    /** One move of a path: where the feet end up, and which cells must be dug first. */
    public record Step(BlockPos feet, List<BlockPos> dig) {}

    public enum State {
        RUNNING,
        FOUND,
        FAILED
    }

    private static final class Node {
        final long pos;
        float g;
        float f;
        int dug;
        @Nullable Node parent;
        List<BlockPos> dig = List.of();
        boolean closed;

        Node(long pos) {
            this.pos = pos;
        }
    }

    /** One search. Call {@link #step(int)} each tick until it is no longer running. */
    public static final class Search {
        private final ServerLevel level;
        private final Goal goal;
        private final @Nullable Digger digger;
        private final int maxDug;
        private final int maxNodes;
        private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));
        private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
        private int expanded;
        private State state = State.RUNNING;
        private List<Step> path = List.of();
        private it.unimi.dsi.fastutil.longs.@Nullable LongSet avoid;

        public Search(ServerLevel level, BlockPos start, Goal goal, @Nullable Digger digger, int maxDug, int maxNodes) {
            this.level = level;
            this.goal = goal;
            this.digger = maxDug > 0 ? digger : null;
            this.maxDug = maxDug;
            this.maxNodes = maxNodes;
            Node first = new Node(start.asLong());
            first.f = (float) goal.estimate(start);
            this.nodes.put(first.pos, first);
            this.open.add(first);
        }

        public State state() {
            return this.state;
        }

        /** Cells (doors or gates that would not open) the search must not pass. */
        public Search avoid(it.unimi.dsi.fastutil.longs.@Nullable LongSet cells) {
            this.avoid = cells == null || cells.isEmpty() ? null : cells;
            return this;
        }

        public List<Step> path() {
            return this.path;
        }

        public int expanded() {
            return this.expanded;
        }

        /** Expands up to {@code budget} nodes. */
        public State step(int budget) {
            int work = 0;
            while (this.state == State.RUNNING && work < budget) {
                Node node = this.open.poll();
                if (node == null || this.expanded >= this.maxNodes) {
                    this.state = State.FAILED;
                    break;
                }
                if (node.closed) {
                    continue;
                }
                node.closed = true;
                this.expanded++;
                work++;
                BlockPos pos = BlockPos.of(node.pos);
                if (this.goal.reached(pos)) {
                    this.finish(node);
                    break;
                }
                this.expand(node, pos);
            }
            return this.state;
        }

        private void finish(Node end) {
            List<Step> steps = new ArrayList<>();
            for (Node node = end; node != null && node.parent != null; node = node.parent) {
                steps.add(new Step(BlockPos.of(node.pos), node.dig));
            }
            Collections.reverse(steps);
            this.path = steps;
            this.state = State.FOUND;
        }

        private void expand(Node node, BlockPos p) {
            // Inside a doorway only straight moves along its passage are possible.
            Direction.Axis doorway = doorAxis(this.level, p);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (doorway != null && d.getAxis() != doorway) {
                    continue;
                }
                BlockPos side = p.relative(d);
                // Walk to the side (also into shallow water, a ladder cell or through a doorway on the same axis).
                this.tryMove(node, side, new BlockPos[] {side, side.above()}, 1.0F, d.getAxis());
                if (doorway != null) {
                    continue;
                }
                // Step up one (needs head room above the start to jump).
                this.tryMove(node, side.above(), new BlockPos[] {side.above(), side.above(2), p.above(2)}, 1.6F, null);
                // Step down one, keeping head room for the move.
                this.tryMove(node, side.below(), new BlockPos[] {side.below(), side, side.above()}, 1.3F, null);
                // Walk off an edge and drop.
                if (cell(this.level, side, null) == OPEN && cell(this.level, side.above(), null) == OPEN && !supported(this.level, side)) {
                    for (int k = 1; k <= MAX_DROP; k++) {
                        BlockPos land = side.below(k);
                        if (cell(this.level, land, null) != OPEN) {
                            break;
                        }
                        if (supported(this.level, land) && !this.avoided(land)) {
                            this.offer(node, land, List.of(), 1.0F + 0.5F * k);
                            break;
                        }
                    }
                }
            }
            // Ladders and vines: straight up while the feet stay on the climbable column, straight down onto it.
            if (climbable(this.level, p)) {
                BlockPos up = p.above();
                if (climbable(this.level, up) && cell(this.level, up, null) == OPEN && cell(this.level, up.above(), null) == OPEN) {
                    this.offer(node, up, List.of(), 1.5F);
                }
            }
            BlockPos down = p.below();
            if (climbable(this.level, down) && cell(this.level, down, null) == OPEN) {
                this.offer(node, down, List.of(), 1.2F);
            }
            if (doorway != null) {
                return;
            }
            // Diagonals: only through open corners, never digging.
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    BlockPos n = p.offset(dx, 0, dz);
                    BlockPos a = p.offset(dx, 0, 0);
                    BlockPos b = p.offset(0, 0, dz);
                    if (floor(this.level, n.below()) && open2(n) && open2(a) && open2(b)) {
                        this.offer(node, n, List.of(), 1.414F);
                    }
                }
            }
        }

        private boolean avoided(BlockPos pos) {
            return this.avoid != null && this.avoid.contains(pos.asLong());
        }

        private boolean open2(BlockPos feet) {
            return cell(this.level, feet, null) == OPEN && cell(this.level, feet.above(), null) == OPEN;
        }

        /**
         * {@code walkAxis} is set for a plain walk to the side: only then may the feet cell be shallow water and the
         * cells be a doorway whose passage runs along that axis.
         */
        private void tryMove(Node from, BlockPos feet, BlockPos[] clear, float cost, Direction.@Nullable Axis walkAxis) {
            if (!supported(this.level, feet)) {
                return;
            }
            List<BlockPos> dig = null;
            for (int i = 0; i < clear.length; i++) {
                BlockPos c = clear[i];
                if (this.avoided(c)) {
                    return;
                }
                int kind = cell(this.level, c, this.digger);
                if (kind == BLOCKED) {
                    return;
                }
                if (kind == WATER) {
                    // Shallow water only: the feet may wade, the head stays in the air.
                    if (i != 0 || walkAxis == null) {
                        return;
                    }
                    cost += WATER_COST;
                    continue;
                }
                if (kind == DOOR) {
                    if (walkAxis == null || doorAxis(this.level, c) != walkAxis) {
                        return;
                    }
                    cost += DOOR_COST * 0.5F;
                    continue;
                }
                if (kind == DIG) {
                    if (dig == null) {
                        dig = new ArrayList<>(3);
                    }
                    dig.add(c.immutable());
                }
            }
            int dugCount = dig == null ? 0 : dig.size();
            if (from.dug + dugCount > this.maxDug) {
                return;
            }
            this.offer(from, feet, dig == null ? List.of() : dig, cost + dugCount * DIG_COST);
        }

        private void offer(Node from, BlockPos feet, List<BlockPos> dig, float cost) {
            long key = feet.asLong();
            float g = from.g + cost;
            Node node = this.nodes.get(key);
            if (node == null) {
                node = new Node(key);
                this.nodes.put(key, node);
            } else if (node.closed || node.g <= g) {
                return;
            }
            node.g = g;
            node.f = g + (float) this.goal.estimate(feet);
            node.parent = from;
            node.dig = dig;
            node.dug = from.dug + dig.size();
            this.open.add(node);
        }
    }

    /**
     * Per-walk helper for doors and gates. {@link #prepare} runs before the body moves into a step: it opens a closed
     * wooden door or fence gate on the step with the owner's fake player (a protection mod that cancels the right-click
     * refuses it, and the cell is avoided on the next search). {@link #passed} closes what it opened once the body is
     * out of the doorway. Doors that were already open are left alone.
     */
    public static final class Walker {
        private final List<BlockPos> opened = new ArrayList<>();
        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet refused = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        private int doorsOpened;

        /** Doorways that would not open (the next search avoids them). */
        public it.unimi.dsi.fastutil.longs.LongOpenHashSet refused() {
            return this.refused;
        }

        /** Doors and gates opened since the walker was made (QA). */
        public int doorsOpened() {
            return this.doorsOpened;
        }

        /** Closes what it opened, unless the body still stands in it. */
        public void reset(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo) {
            this.closeBehind(level, echo);
        }

        /** A new job loop: forget refused doorways (the owner may have changed the protection). */
        public void forget() {
            this.refused.clear();
        }

        public boolean prepare(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo, Step step) {
            for (BlockPos cell : new BlockPos[] {step.feet(), step.feet().above()}) {
                if (!level.isLoaded(cell)) {
                    continue;
                }
                BlockState state = level.getBlockState(cell);
                if (!doorway(state) || isOpenDoorway(state)) {
                    continue;
                }
                BlockPos base = state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        && state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                        ? cell.below() : cell.immutable();
                if (!EchoWork.mayOpen(level, echo, base)) {
                    this.refused.add(base.asLong());
                    this.refused.add(base.above().asLong());
                    return false;
                }
                setOpen(level, echo, base, true);
                this.opened.add(base);
                this.doorsOpened++;
            }
            return true;
        }

        public void passed(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo, Step step) {
            this.closeBehind(level, echo);
        }

        private void closeBehind(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo) {
            if (this.opened.isEmpty()) {
                return;
            }
            net.minecraft.world.phys.AABB body = echo.getBoundingBox().inflate(0.05D);
            this.opened.removeIf(base -> {
                if (!level.isLoaded(base)) {
                    return true;
                }
                if (body.intersects(new net.minecraft.world.phys.AABB(base).expandTowards(0.0D, 1.0D, 0.0D))) {
                    return false;
                }
                setOpen(level, echo, base, false);
                return true;
            });
        }
    }

    /** Wooden doors (not iron: those need redstone) and fence gates. */
    public static boolean doorway(BlockState state) {
        return state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                || state.is(BlockTags.FENCE_GATES) && state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    private static boolean isOpenDoorway(BlockState state) {
        return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
    }

    /** The axis a body walks along to pass the doorway at {@code pos}, or null when it is not a doorway. */
    public static Direction.@Nullable Axis doorAxis(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!doorway(state)) {
            return null;
        }
        return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getAxis();
    }

    /** Opens or closes a doorway (lower half for doors), with the vanilla sound and game event. Never fights redstone. */
    static void setOpen(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo, BlockPos base, boolean open) {
        BlockState state = level.getBlockState(base);
        if (!doorway(state) || isOpenDoorway(state) == open) {
            return;
        }
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
            return;
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door) {
            door.setOpen(echo, level, state, base, open);
        } else if (state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock gate) {
            level.setBlock(base, state.setValue(net.minecraft.world.level.block.FenceGateBlock.OPEN, open), 10);
            level.playSound(null, base, open ? gate.openSound : gate.closeSound, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F,
                    level.getRandom().nextFloat() * 0.1F + 0.9F);
            level.gameEvent(echo, open ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN : net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE, base);
        }
    }

    /** Ladders, vines and the like a body can climb (not scaffolding, which it would stand on). */
    public static boolean climbable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.CLIMBABLE) && !state.is(Blocks.SCAFFOLDING) && state.getFluidState().isEmpty();
    }

    /** A body can stay at these feet: a floor below, or a climbable block to hold on to. */
    public static boolean supported(ServerLevel level, BlockPos feet) {
        return floor(level, feet.below()) || climbable(level, feet);
    }

    public static final int OPEN = 0;
    public static final int DIG = 1;
    public static final int BLOCKED = 2;
    /** Stage 3: water a body can wade in with its feet (never with its head). */
    public static final int WATER = 3;
    /** Stage 3: a wooden door or fence gate, passable straight along its axis. */
    public static final int DOOR = 4;

    /** Whether a body can occupy the cell (feet or head), could dig it, or neither. Unloaded counts as blocked. */
    public static int cell(ServerLevel level, BlockPos pos, @Nullable Digger digger) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return BLOCKED;
        }
        BlockState state = level.getBlockState(pos);
        if (doorway(state)) {
            return DOOR;
        }
        if (!state.getFluidState().isEmpty()) {
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && state.getCollisionShape(level, pos).isEmpty() && !dangerous(state)) {
                return WATER;
            }
            return BLOCKED;
        }
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return dangerous(state) ? BLOCKED : OPEN;
        }
        if (state.is(BlockTags.CLIMBABLE) && !state.is(Blocks.SCAFFOLDING)) {
            // A ladder is a thin panel at the back of its cell: the body fits in front of it.
            return OPEN;
        }
        if (digger != null && digger.canDig(pos, state)) {
            return DIG;
        }
        return BLOCKED;
    }

    /** A block a body can stand on: full-height collision top, no fluid, nothing that burns or slows. */
    public static boolean floor(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.CACTUS)
                || state.is(BlockTags.CLIMBABLE) && !state.is(Blocks.SCAFFOLDING) || doorway(state)) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        double top = shape.max(Direction.Axis.Y);
        return top >= 0.9D && top <= 1.0D;
    }

    private static boolean dangerous(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.LAVA);
    }
}
