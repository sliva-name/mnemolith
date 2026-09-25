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
 */
public final class EchoNav {
    public static final int MAX_DROP = 3;
    private static final float DIG_COST = 4.0F;

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
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos side = p.relative(d);
                // Walk to the side.
                this.tryMove(node, side, new BlockPos[] {side, side.above()}, 1.0F);
                // Step up one (needs head room above the start to jump).
                this.tryMove(node, side.above(), new BlockPos[] {side.above(), side.above(2), p.above(2)}, 1.6F);
                // Step down one, keeping head room for the move.
                this.tryMove(node, side.below(), new BlockPos[] {side.below(), side, side.above()}, 1.3F);
                // Walk off an edge and drop.
                if (cell(this.level, side, null) == OPEN && cell(this.level, side.above(), null) == OPEN && !floor(this.level, side.below())) {
                    for (int k = 1; k <= MAX_DROP; k++) {
                        BlockPos land = side.below(k);
                        if (cell(this.level, land, null) != OPEN) {
                            break;
                        }
                        if (floor(this.level, land.below())) {
                            this.offer(node, land, List.of(), 1.0F + 0.5F * k);
                            break;
                        }
                    }
                }
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

        private boolean open2(BlockPos feet) {
            return cell(this.level, feet, null) == OPEN && cell(this.level, feet.above(), null) == OPEN;
        }

        private void tryMove(Node from, BlockPos feet, BlockPos[] clear, float cost) {
            if (!floor(this.level, feet.below())) {
                return;
            }
            List<BlockPos> dig = null;
            for (BlockPos c : clear) {
                int kind = cell(this.level, c, this.digger);
                if (kind == BLOCKED) {
                    return;
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
     * Per-walk helper for things a step needs besides walking (stage 3 navigation hooks). {@link #prepare} runs before
     * the body moves into a step and may refuse it; {@link #passed} runs once the body stands on it.
     */
    public static final class Walker {
        public void reset(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo) {}

        public boolean prepare(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo, Step step) {
            return true;
        }

        public void passed(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo, Step step) {}
    }

    public static final int OPEN = 0;
    public static final int DIG = 1;
    public static final int BLOCKED = 2;

    /** Whether a body can occupy the cell (feet or head), could dig it, or neither. Unloaded counts as blocked. */
    public static int cell(ServerLevel level, BlockPos pos, @Nullable Digger digger) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return BLOCKED;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return BLOCKED;
        }
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return dangerous(state) ? BLOCKED : OPEN;
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
        if (!state.getFluidState().isEmpty() || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.CACTUS)) {
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
