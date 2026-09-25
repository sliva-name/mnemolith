package com.mnemolith.echo.job;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.MemoryAvatar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Lens orders: stay, follow the owner, or walk back to the work point and resume. */
final class JobOrders {
    private final EchoJob job;
    EchoJob.Order order = EchoJob.Order.NONE;
    int orderRetry;

    JobOrders(EchoJob job) {
        this.job = job;
    }

    boolean active() {
        return this.order != EchoJob.Order.NONE;
    }

    /**
     * The owner gave a lens order. STAY pauses whatever the echo does (the job keeps its mode), FOLLOW walks after the
     * owner, RETURN walks back to the work point and then resumes the job. Returns false with a stop status when there
     * is no point to return to.
     */
    boolean command(ServerLevel level, EchoEntity echo, EchoJob.Order order) {
        if (order == EchoJob.Order.NONE) {
            return false;
        }
        if (this.job.mode == EchoJob.Mode.REPLAY) {
            this.job.mode = EchoJob.Mode.IDLE;
        }
        this.job.alarm.alarmed = false;
        this.job.alarm.threat = null;
        this.job.alarm.calmTicks = 0;
        this.job.mimic.stumbleTicks = 0;
        this.job.mover.stop(level, echo);
        this.job.release(echo);
        this.orderRetry = 0;
        if (order == EchoJob.Order.RETURN && this.job.workPoint() == null) {
            this.order = EchoJob.Order.NONE;
            this.job.setStatus(JobStatus.of(JobStatus.Kind.NO_POINT));
            this.job.dirty = true;
            return false;
        }
        this.order = order;
        this.job.setStatus(JobStatus.of(switch (order) {
            case STAY -> JobStatus.Kind.STAY;
            case FOLLOW -> JobStatus.Kind.FOLLOW;
            default -> JobStatus.Kind.RETURNING;
        }));
        this.job.dirty = true;
        return true;
    }

    void tick(ServerLevel level, EchoEntity echo) {
        switch (this.order) {
            case STAY -> {
                if (this.job.mover.active()) {
                    this.job.mover.stop(level, echo);
                }
                echo.setMoveTarget(null);
            }
            case FOLLOW -> this.tickFollow(level, echo);
            case RETURN -> this.tickReturn(level, echo);
            default -> this.order = EchoJob.Order.NONE;
        }
    }

    private void tickFollow(ServerLevel level, EchoEntity echo) {
        ServerPlayer owner = echo.ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(echo.ownerId());
        if (owner == null && echo.ownerId() != null) {
            owner = MemoryAvatar.STAND_INS.get(echo.ownerId());
        }
        double lost = CommonConfig.ECHO_FOLLOW_LOST_DISTANCE.get();
        if (owner == null || owner.level() != level || !owner.isAlive() || owner.distanceToSqr(echo) > lost * lost) {
            this.job.mover.stop(level, echo);
            this.order = EchoJob.Order.STAY;
            this.job.setStatus(JobStatus.of(JobStatus.Kind.LOST_OWNER));
            Mnemolith.LOGGER.info("Mnemolith echo lost its owner owner={} at {}", echo.ownerName(), echo.blockPosition().toShortString());
            return;
        }
        double distance = owner.distanceToSqr(echo);
        if (distance <= 9.0D) {
            if (this.job.mover.active()) {
                this.job.mover.stop(level, echo);
            }
            if (echo.tickCount % 5 == 0) {
                echo.lookAt(owner.getEyePosition());
            }
            return;
        }
        if (this.orderRetry > 0) {
            this.orderRetry--;
        }
        BlockPos target = owner.blockPosition();
        if (!this.job.mover.active() || (echo.tickCount % 20 == 0 && this.orderRetry == 0)) {
            this.job.mover.start(level, echo, new EchoNav.Goal() {
                @Override
                public boolean reached(BlockPos feet) {
                    return feet.distSqr(target) <= 5.0D;
                }

                @Override
                public double estimate(BlockPos feet) {
                    return Math.max(0.0D, Math.sqrt(feet.distSqr(target)) - 2.0D);
                }
            });
        }
        EchoMover.Result result = this.job.mover.tick(level, echo);
        if (result == EchoMover.Result.FAILED) {
            // No way to the owner right now: wait a little and try again (it keeps its FOLLOW status).
            this.orderRetry = 40;
        }
    }

    private void tickReturn(ServerLevel level, EchoEntity echo) {
        BlockPos point = this.job.workPoint();
        if (point == null) {
            this.job.mover.stop(level, echo);
            this.order = EchoJob.Order.NONE;
            this.job.setStatus(JobStatus.of(JobStatus.Kind.NO_POINT));
            return;
        }
        if (!this.job.mover.active()) {
            if (echo.blockPosition().distSqr(point) <= 9.0D) {
                this.arrived(level, echo);
                return;
            }
            this.job.mover.start(level, echo, new EchoNav.Goal() {
                @Override
                public boolean reached(BlockPos feet) {
                    return feet.distSqr(point) <= 9.0D;
                }

                @Override
                public double estimate(BlockPos feet) {
                    return Math.max(0.0D, Math.sqrt(feet.distSqr(point)) - 3.0D);
                }
            });
        }
        EchoMover.Result result = this.job.mover.tick(level, echo);
        if (result == EchoMover.Result.ARRIVED) {
            this.arrived(level, echo);
        } else if (result == EchoMover.Result.FAILED) {
            this.job.mover.stop(level, echo);
            this.order = EchoJob.Order.STAY;
            this.job.setStatus(JobStatus.of(JobStatus.Kind.UNREACHABLE));
        }
    }

    private void arrived(ServerLevel level, EchoEntity echo) {
        this.job.mover.stop(level, echo);
        this.order = EchoJob.Order.NONE;
        if (this.job.hasWorkMode()) {
            this.job.restartPhase();
            this.job.setStatus(this.job.workingStatus());
        } else {
            this.job.setStatus(JobStatus.of(JobStatus.Kind.AT_POINT));
        }
        Mnemolith.LOGGER.info("Mnemolith echo back at its point owner={} mode={} at {}", echo.ownerName(), this.job.mode.getSerializedName(), echo.blockPosition().toShortString());
    }
}
