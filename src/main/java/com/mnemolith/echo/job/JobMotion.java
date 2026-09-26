package com.mnemolith.echo.job;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Shared walk, path search, and dig loop. Mine, build, and farm all use it; arriving and finishing a dig
 * hand the step back to the mode that asked.
 */
final class JobMotion {
    enum Phase {
        START,
        SCAN,
        SELECT,
        PATH,
        WALK,
        DIG,
        TO_CHEST,
        WAIT
    }

    enum DigFor {
        TUNNEL,
        TARGET,
        CLEAR,
        /** Stage 3: take back a misfired block, then place the right one. */
        FIX
    }

    private final EchoJob job;
    Phase phase = Phase.START;
    int waitTicks;
    int placeCooldown;
    EchoNav.@Nullable Search search;
    List<EchoNav.Step> path = List.of();
    int pathIndex;
    int replans;
    double bestDistance;
    int stuckTicks;
    boolean pathToChest;
    @Nullable BlockPos digPos;
    DigFor digFor = DigFor.TARGET;
    int digTicks;
    int digTotal;
    /** Opens and closes doors and gates on job walks (stage 3 navigation). */
    final EchoNav.Walker walker = new EchoNav.Walker();
    int unreachableInRow;

    JobMotion(EchoJob job) {
        this.job = job;
    }

    /** Drops the in-progress search. Saved progress (mined, harvested, the blueprint) stays. */
    void restart() {
        this.phase = Phase.START;
        this.search = null;
        this.path = List.of();
        this.pathIndex = 0;
        this.digPos = null;
        this.walker.forget();
        this.unreachableInRow = 0;
        this.replans = 0;
        this.waitTicks = 0;
    }

    void clearRoute() {
        this.search = null;
        this.path = List.of();
    }

    void startPath(ServerLevel level, EchoEntity echo, EchoNav.Goal goal, EchoNav.@Nullable Digger digger, int maxDug, boolean toChest) {
        BlockPos start = echo.blockPosition();
        if (goal.reached(start) && echo.onGround()) {
            this.path = List.of();
            this.pathIndex = 0;
            this.pathToChest = toChest;
            this.arrive(level, echo);
            return;
        }
        int budget = CommonConfig.ECHO_PATH_BUDGET.get();
        this.search = new EchoNav.Search(level, start, goal, digger, maxDug, budget * 16).avoid(this.walker.refused())
                .maxDrop(com.mnemolith.echo.graft.EchoGrafts.maxDrop(echo));
        this.pathToChest = toChest;
        this.phase = Phase.PATH;
    }

    void tickPath(ServerLevel level, EchoEntity echo) {
        EchoNav.Search current = this.search;
        if (current == null) {
            this.phase = Phase.SELECT;
            return;
        }
        EchoNav.State state = current.step(CommonConfig.ECHO_PATH_BUDGET.get());
        if (state == EchoNav.State.RUNNING) {
            return;
        }
        this.search = null;
        if (state == EchoNav.State.FAILED) {
            Mnemolith.LOGGER.debug("Mnemolith echo path failed owner={} expanded={}", echo.ownerName(), current.expanded());
            this.onUnreachable(level, echo);
            return;
        }
        this.path = current.path();
        this.pathIndex = 0;
        this.stuckTicks = 0;
        this.bestDistance = Double.MAX_VALUE;
        this.phase = Phase.WALK;
    }

    private void onUnreachable(ServerLevel level, EchoEntity echo) {
        echo.setMoveTarget(null);
        if (this.pathToChest) {
            this.job.build.fetching = false;
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.CHEST_UNAVAILABLE));
            return;
        }
        this.unreachableInRow++;
        if (this.job.mode == EchoJob.Mode.MINE && this.job.mine.target != null) {
            this.job.mine.refused.add(this.job.mine.target.asLong());
            this.job.mine.target = null;
        } else if (this.job.mode == EchoJob.Mode.BUILD && this.job.build.buildTarget != null) {
            this.job.build.skipped.add(this.job.build.buildTarget.offset().asLong());
            this.job.build.buildTarget = null;
        } else if (this.job.mode == EchoJob.Mode.FARM && this.job.farm.farmTarget != null) {
            this.job.farm.farmRefused.add(this.job.farm.farmTarget.asLong());
            this.job.farm.farmTarget = null;
        }
        this.digFor = DigFor.TARGET;
        this.phase = Phase.SELECT;
    }

    void tickWalk(ServerLevel level, EchoEntity echo) {
        if (this.pathIndex >= this.path.size()) {
            echo.setMoveTarget(null);
            this.arrive(level, echo);
            return;
        }
        EchoNav.Step step = this.path.get(this.pathIndex);
        if (!level.isLoaded(step.feet())) {
            this.onUnreachable(level, echo);
            return;
        }
        // Clear the cells of this move first (tunnel).
        for (BlockPos cell : step.dig()) {
            int kind = EchoNav.cell(level, cell, null);
            if (kind == EchoNav.OPEN) {
                continue;
            }
            BlockState state = level.getBlockState(cell);
            if (!EchoWork.canTunnel(level, echo, cell, state)) {
                this.replan(level, echo);
                return;
            }
            echo.setMoveTarget(null);
            this.beginDig(level, echo, cell, DigFor.TUNNEL);
            return;
        }
        if (!this.walker.prepare(level, echo, step)) {
            // A door or gate on the way would not open (protection): search again around it.
            this.replan(level, echo);
            return;
        }
        Vec3 goal = Vec3.atBottomCenterOf(step.feet());
        double dx = goal.x - echo.getX();
        double dz = goal.z - echo.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = goal.y - echo.getY();
        if (horizontal < 0.3D && dy > -0.6D && dy < 0.6D) {
            this.walker.passed(level, echo, step);
            this.pathIndex++;
            this.stuckTicks = 0;
            this.bestDistance = Double.MAX_VALUE;
            if (this.pathIndex >= this.path.size()) {
                echo.setMoveTarget(null);
                this.arrive(level, echo);
            }
            return;
        }
        echo.setMoveTarget(goal);
        double distance = horizontal + Math.abs(dy) * 0.5D;
        if (distance < this.bestDistance - 0.05D) {
            this.bestDistance = distance;
            this.stuckTicks = 0;
        } else if (++this.stuckTicks > JobLimits.STUCK_TICKS) {
            this.replan(level, echo);
        }
    }

    private void replan(ServerLevel level, EchoEntity echo) {
        echo.setMoveTarget(null);
        if (++this.replans > 3) {
            this.replans = 0;
            this.onUnreachable(level, echo);
            return;
        }
        // Search again from where the echo stands, to the same goal.
        this.phase = Phase.SELECT;
        if (this.pathToChest) {
            this.job.chests.goToChest(level, echo);
        } else if (this.job.mode == EchoJob.Mode.MINE && this.job.mine.target != null) {
            this.job.mine.candidates.add(0, this.job.mine.target);
            this.job.mine.target = null;
        } else if (this.job.mode == EchoJob.Mode.BUILD) {
            this.job.build.buildTarget = null;
        } else if (this.job.mode == EchoJob.Mode.FARM) {
            this.job.farm.farmTarget = null;
        }
    }

    private void arrive(ServerLevel level, EchoEntity echo) {
        echo.setMoveTarget(null);
        this.replans = 0;
        if (this.pathToChest) {
            this.pathToChest = false;
            this.phase = Phase.TO_CHEST;
            return;
        }
        if (this.job.mode == EchoJob.Mode.FARM) {
            this.job.farm.act(level, echo);
            return;
        }
        if (this.job.mode == EchoJob.Mode.MINE) {
            BlockPos pos = this.job.mine.target;
            if (pos == null) {
                this.phase = Phase.SELECT;
                return;
            }
            this.beginDig(level, echo, pos, DigFor.TARGET);
        } else if (this.job.mode == EchoJob.Mode.BUILD) {
            EchoLesson.Entry entry = this.job.build.buildTarget;
            if (entry == null) {
                this.phase = Phase.SELECT;
                return;
            }
            if (this.digFor == DigFor.CLEAR) {
                this.beginDig(level, echo, entry.offset(), DigFor.CLEAR);
                return;
            }
            if (this.digFor == DigFor.FIX) {
                this.digFor = DigFor.TARGET;
                BlockState wrong = this.job.strain.misfired.remove(entry.offset().asLong());
                if (wrong != null && !EchoHands.takeBack(level, echo, entry.offset(), wrong)) {
                    // Someone protects or changed it: leave it, the builder reports it as blocked.
                    this.job.build.blocked.add(entry.offset().asLong());
                    this.job.build.buildTarget = null;
                    this.phase = Phase.SELECT;
                    return;
                }
                this.job.notice(JobStatus.of(JobStatus.Kind.MISFIRE, "fix"), 40);
            }
            this.job.build.placeBuildTarget(level, echo);
        }
    }

    private void beginDig(ServerLevel level, EchoEntity echo, BlockPos pos, DigFor why) {
        BlockState state = level.getBlockState(pos);
        if (!EchoWork.safeToBreak(level, echo, pos)) {
            this.digRefused(level, echo, pos, why);
            return;
        }
        int tool = EchoWork.bestTool(echo.inventory(), state);
        if (tool < 0 && EchoWork.needsTool(state)) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.NO_TOOL, EchoWork.toolKind(state)));
            return;
        }
        this.digPos = pos.immutable();
        this.digFor = why;
        this.digTicks = 0;
        ItemStack stack = tool >= 0 ? echo.inventory().getItem(tool) : ItemStack.EMPTY;
        // Memory grafts: a hushed echo digs slower, a volatile one faster.
        this.digTotal = Math.max(2, (int) Math.round(EchoWork.breakTicks(level, pos, state, stack) * com.mnemolith.echo.graft.EchoGrafts.digFactor(echo)));
        this.phase = Phase.DIG;
        echo.lookAt(Vec3.atCenterOf(pos));
    }

    void tickDig(ServerLevel level, EchoEntity echo) {
        BlockPos pos = this.digPos;
        if (pos == null) {
            this.phase = Phase.SELECT;
            return;
        }
        echo.setMoveTarget(null);
        echo.lookAt(Vec3.atCenterOf(pos));
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !level.isLoaded(pos)) {
            level.destroyBlockProgress(echo.getId(), pos, -1);
            this.digPos = null;
            this.afterDig(level, echo, pos, false);
            return;
        }
        this.digTicks++;
        if (this.digTicks % 5 == 1) {
            echo.swing(InteractionHand.MAIN_HAND);
        }
        level.destroyBlockProgress(echo.getId(), pos, Math.min(9, this.digTicks * 10 / Math.max(1, this.digTotal)));
        if (this.digTicks < this.digTotal) {
            return;
        }
        if (this.digFor == DigFor.TARGET && this.job.strain.misfire(echo)) {
            if (this.job.strain.misfires++ % 2 == 1 && this.job.mine.breakExtra(level, echo, pos)) {
                this.job.strain.misfireNotice(echo, "extra", pos);
            } else {
                // Fumbled: the dig starts over.
                this.digTicks = 0;
                this.job.strain.misfireNotice(echo, "skip", pos);
                return;
            }
        }
        level.destroyBlockProgress(echo.getId(), pos, -1);
        this.digPos = null;
        if (!EchoWork.safeToBreak(level, echo, pos)) {
            this.digRefused(level, echo, pos, this.digFor);
            return;
        }
        int tool = EchoWork.bestTool(echo.inventory(), state);
        if (tool < 0 && EchoWork.needsTool(state)) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.NO_TOOL, EchoWork.toolKind(state)));
            return;
        }
        boolean plunge = EchoWork.supports(echo, pos) && EchoWork.dropBelow(level, pos) > com.mnemolith.echo.graft.EchoGrafts.NORMAL_DROP;
        EchoHands.JobBreak result = EchoHands.breakForJob(level, echo, pos, tool);
        if (result.outcome() != EchoHands.Outcome.DONE) {
            this.digRefused(level, echo, pos, this.digFor);
            return;
        }
        com.mnemolith.echo.graft.EchoGrafts.afterDig(echo);
        if (plunge) {
            com.mnemolith.echo.graft.EchoGrafts.onPlungeDig(echo);
        }
        if (result.toolBroke()) {
            Mnemolith.LOGGER.info("Mnemolith echo tool broke owner={} at {}", echo.ownerName(), pos.toShortString());
        }
        this.afterDig(level, echo, pos, result.toolBroke());
    }

    private void afterDig(ServerLevel level, EchoEntity echo, BlockPos pos, boolean toolBroke) {
        switch (this.digFor) {
            case TUNNEL -> this.phase = Phase.WALK;
            case CLEAR -> {
                this.digFor = DigFor.TARGET;
                this.job.build.placeBuildTarget(level, echo);
            }
            case TARGET -> {
                this.job.mined++;
                this.unreachableInRow = 0;
                this.job.mine.target = null;
                BlockState sample = this.job.mine.targetState;
                this.job.setStatus(new JobStatus(JobStatus.Kind.MINING, sample == null ? "" : JobTexts.key(sample.getBlock()), this.job.mined, 0));
                this.phase = Phase.SELECT;
                this.job.mimic.afterBreak();
                this.job.strain.onWorkAction(level, echo, pos);
            }
            default -> {
            }
        }
        if (toolBroke && this.job.mode == EchoJob.Mode.MINE) {
            // Keep going only if another fitting tool is left for what it mines.
            BlockState sample = this.job.mine.targetState;
            if (sample != null && EchoWork.needsTool(sample) && EchoWork.bestTool(echo.inventory(), sample) < 0) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.TOOL_BROKE));
            }
        }
    }

    private void digRefused(ServerLevel level, EchoEntity echo, BlockPos pos, DigFor why) {
        switch (why) {
            case TARGET -> {
                this.job.mine.refused.add(pos.asLong());
                this.job.mine.target = null;
                this.phase = Phase.SELECT;
            }
            case TUNNEL -> this.replan(level, echo);
            case CLEAR, FIX -> {
                this.digFor = DigFor.TARGET;
                this.job.build.blocked.add(pos.asLong());
                this.job.build.buildTarget = null;
                this.phase = Phase.SELECT;
            }
        }
    }
}
