package com.mnemolith.echo.job;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Flee from an attacker, then resume the same job once the echo has been calm long enough. */
final class JobAlarm {
    private final EchoJob job;
    boolean alarmed;
    int calmTicks;
    @Nullable Vec3 threat;

    JobAlarm(EchoJob job) {
        this.job = job;
    }

    /**
     * A hostile mob hurt the echo. A working echo drops what it was doing, runs {@code echoFleeDistance} blocks away
     * from the attacker and waits; it never hits back. The job keeps its mode, so it resumes after a calm spell.
     */
    void onAttacked(ServerLevel level, EchoEntity echo, LivingEntity attacker) {
        if (!this.job.isWorking()) {
            return;
        }
        this.calmTicks = 0;
        this.threat = attacker.position();
        if (!this.alarmed) {
            this.alarmed = true;
            this.job.release(echo);
            this.job.mimic.stumbleTicks = 0;
            this.job.setStatus(JobStatus.of(JobStatus.Kind.ATTACKED));
            Mnemolith.LOGGER.info("Mnemolith echo attacked owner={} by={} at {}", echo.ownerName(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()), echo.blockPosition().toShortString());
            this.flee(level, echo);
        } else if (!this.job.mover.active()) {
            this.flee(level, echo);
        }
    }

    private void flee(ServerLevel level, EchoEntity echo) {
        Vec3 from = this.threat == null ? echo.position() : this.threat;
        BlockPos origin = BlockPos.containing(from);
        int distance = CommonConfig.ECHO_FLEE_DISTANCE.get();
        this.job.mover.start(level, echo, new EchoNav.Goal() {
            @Override
            public boolean reached(BlockPos feet) {
                return feet.distSqr(origin) >= (double) distance * distance;
            }

            @Override
            public double estimate(BlockPos feet) {
                return Math.max(0.0D, distance - Math.sqrt(feet.distSqr(origin)));
            }
        });
    }

    void tick(ServerLevel level, EchoEntity echo) {
        this.calmTicks++;
        EchoMover.Result result = this.job.mover.tick(level, echo);
        if (result == EchoMover.Result.RUNNING) {
            return;
        }
        if (this.calmTicks % 10 != 0) {
            return;
        }
        Mob hunter = EchoJob.hunter(level, echo);
        if (hunter != null) {
            // Still hunted: keep out of its way, and do not count this as calm.
            this.threat = hunter.position();
            if (hunter.distanceToSqr(echo) < 36.0D) {
                this.flee(level, echo);
            }
            this.calmTicks = Math.min(this.calmTicks, CommonConfig.ECHO_FLEE_SAFE_TICKS.get() / 2);
            return;
        }
        if (this.calmTicks >= CommonConfig.ECHO_FLEE_SAFE_TICKS.get()) {
            this.resume(echo);
        }
    }

    private void resume(EchoEntity echo) {
        this.alarmed = false;
        this.threat = null;
        this.calmTicks = 0;
        echo.setMoveTarget(null);
        this.job.restartPhase();
        this.job.setStatus(this.job.workingStatus());
        Mnemolith.LOGGER.info("Mnemolith echo resumed owner={} mode={} at {}", echo.ownerName(), this.job.mode.getSerializedName(), echo.blockPosition().toShortString());
    }
}
