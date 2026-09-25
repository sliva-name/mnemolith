package com.mnemolith.echo.job;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A plain walker for stage 3 moves that are not part of a job's work loop: running from an attacker and the lens
 * orders (follow, return). It never digs. The search uses the same per-tick node budget as the job.
 */
public final class EchoMover {
    public enum Result {
        IDLE,
        RUNNING,
        ARRIVED,
        FAILED
    }

    private static final int STUCK_TICKS = 40;

    private EchoNav.@Nullable Goal goal;
    private EchoNav.@Nullable Search search;
    private List<EchoNav.Step> path = List.of();
    private int index;
    private int stuckTicks;
    private int replans;
    private double best = Double.MAX_VALUE;
    private final EchoNav.Walker walker = new EchoNav.Walker();
    private boolean active;

    public boolean active() {
        return this.active;
    }

    public void start(ServerLevel level, EchoEntity echo, EchoNav.Goal goal) {
        this.goal = goal;
        this.replans = 0;
        this.begin(level, echo);
    }

    private void begin(ServerLevel level, EchoEntity echo) {
        EchoNav.Goal target = this.goal;
        if (target == null) {
            this.stop(level, echo);
            return;
        }
        this.walker.reset(level, echo);
        this.path = List.of();
        this.index = 0;
        this.stuckTicks = 0;
        this.best = Double.MAX_VALUE;
        this.active = true;
        BlockPos start = echo.blockPosition();
        if (target.reached(start)) {
            this.search = null;
            return;
        }
        this.search = new EchoNav.Search(level, start, target, null, 0, CommonConfig.ECHO_PATH_BUDGET.get() * 16);
    }

    public void stop(ServerLevel level, EchoEntity echo) {
        this.active = false;
        this.search = null;
        this.path = List.of();
        this.goal = null;
        this.walker.reset(level, echo);
        echo.setMoveTarget(null);
    }

    public Result tick(ServerLevel level, EchoEntity echo) {
        if (!this.active) {
            return Result.IDLE;
        }
        EchoNav.Search current = this.search;
        if (current != null) {
            EchoNav.State state = current.step(CommonConfig.ECHO_PATH_BUDGET.get());
            if (state == EchoNav.State.RUNNING) {
                return Result.RUNNING;
            }
            this.search = null;
            if (state == EchoNav.State.FAILED) {
                this.stop(level, echo);
                return Result.FAILED;
            }
            this.path = current.path();
            this.index = 0;
            this.stuckTicks = 0;
            this.best = Double.MAX_VALUE;
        }
        if (this.index >= this.path.size()) {
            this.walker.reset(level, echo);
            echo.setMoveTarget(null);
            this.active = false;
            return Result.ARRIVED;
        }
        EchoNav.Step step = this.path.get(this.index);
        if (!level.isLoaded(step.feet())) {
            this.stop(level, echo);
            return Result.FAILED;
        }
        if (!this.walker.prepare(level, echo, step)) {
            // A door or gate on the way would not open: search again from here.
            return this.replan(level, echo);
        }
        Vec3 target = Vec3.atBottomCenterOf(step.feet());
        double dx = target.x - echo.getX();
        double dz = target.z - echo.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = target.y - echo.getY();
        if (horizontal < 0.3D && dy > -0.6D && dy < 0.6D) {
            this.walker.passed(level, echo, step);
            this.index++;
            this.stuckTicks = 0;
            this.best = Double.MAX_VALUE;
            return Result.RUNNING;
        }
        echo.setMoveTarget(target);
        double distance = horizontal + Math.abs(dy) * 0.5D;
        if (distance < this.best - 0.05D) {
            this.best = distance;
            this.stuckTicks = 0;
        } else if (++this.stuckTicks > STUCK_TICKS) {
            return this.replan(level, echo);
        }
        return Result.RUNNING;
    }

    private Result replan(ServerLevel level, EchoEntity echo) {
        echo.setMoveTarget(null);
        if (++this.replans > 3) {
            this.stop(level, echo);
            return Result.FAILED;
        }
        this.begin(level, echo);
        return Result.RUNNING;
    }
}
