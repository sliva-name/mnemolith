package com.mnemolith.echo.job;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.particle.ModParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A moment replicant copying this job: undo a placed block, or make the echo stumble. */
final class JobMimic {
    private final EchoJob job;
    int mimicTicks;
    int mimicLeft;
    int mimicCount;
    int mimicPlaced;
    @Nullable UUID mimicBy;
    @Nullable BlockPos undoPos;
    @Nullable BlockState undoState;
    int undoDelay;
    int stumbleTicks;

    JobMimic(EchoJob job) {
        this.job = job;
    }

    boolean active() {
        return this.mimicTicks > 0;
    }

    /** True when this tick is spent stumbling. Decrements the counter the same way the old tick did. */
    boolean consumeStumble() {
        if (this.stumbleTicks <= 0) {
            return false;
        }
        this.stumbleTicks--;
        return true;
    }

    /**
     * A moment replicant copies this job for {@code ticks}. While it lasts, every other block the builder places is
     * pulled back by the replicant (the block item goes back into the echo, so the builder simply places it again),
     * and a miner stumbles for a moment after a block. At most {@code undoMax} such tricks per mimic.
     */
    void begin(UUID replicant, int ticks, int undoMax) {
        if (!this.job.isWorking() || ticks <= 0) {
            return;
        }
        this.mimicBy = replicant;
        this.mimicTicks = ticks;
        this.mimicLeft = undoMax;
        this.mimicCount = 0;
        this.mimicPlaced = 0;
        this.job.notice(JobStatus.of(JobStatus.Kind.MIMIC, 0, 0), ticks);
    }

    void tick(ServerLevel level, EchoEntity echo) {
        this.mimicTicks--;
        Entity by = this.mimicBy == null ? null : level.getEntity(this.mimicBy);
        if (by == null || !by.isAlive() || !this.job.isWorking() || (by instanceof MomentReplicant replicant && !replicant.isMimicking(echo))) {
            this.end();
            return;
        }
        BlockPos pos = this.undoPos;
        BlockState state = this.undoState;
        if (pos != null && state != null && --this.undoDelay <= 0) {
            this.undoPos = null;
            this.undoState = null;
            if (EchoHands.takeBack(level, echo, pos, state)) {
                this.mimicLeft--;
                this.mimicCount++;
                if (by instanceof LivingEntity living) {
                    living.swing(InteractionHand.MAIN_HAND);
                }
                level.sendParticles(ModParticles.REPLICANT_TELEGRAPH.get(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 10, 0.3D, 0.3D, 0.3D, 0.01D);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.6F, 0.6F);
                this.job.notice(JobStatus.of(JobStatus.Kind.MIMIC, this.mimicCount, 0), Math.max(40, this.mimicTicks));
                Mnemolith.LOGGER.info("Mnemolith replicant undid echo block owner={} at {} undone={}", echo.ownerName(), pos.toShortString(), this.mimicCount);
            }
        }
        if (this.mimicTicks <= 0) {
            this.end();
        }
    }

    private void end() {
        this.mimicTicks = 0;
        this.mimicBy = null;
        this.undoPos = null;
        this.undoState = null;
        if (this.job.notice != null && this.job.notice.kind() == JobStatus.Kind.MIMIC) {
            this.job.noticeTicks = Math.min(this.job.noticeTicks, 20);
        }
    }

    /** Called after a block the job placed; a mimicking replicant may pull it back a moment later. */
    void afterPlace(BlockPos pos, BlockState state) {
        if (this.mimicTicks <= 0 || this.mimicLeft <= 0 || this.undoPos != null) {
            return;
        }
        if (this.mimicPlaced++ % 2 == 0) {
            this.undoPos = pos.immutable();
            this.undoState = state;
            this.undoDelay = 10;
        }
    }

    /** Called after a block the job broke or harvested; a mimicking replicant makes the echo stumble. */
    void afterBreak() {
        if (this.mimicTicks <= 0 || this.mimicLeft <= 0) {
            return;
        }
        this.mimicLeft--;
        this.mimicCount++;
        this.stumbleTicks = 30;
        this.job.notice(JobStatus.of(JobStatus.Kind.MIMIC, this.mimicCount, 0), Math.max(40, this.mimicTicks));
    }
}
