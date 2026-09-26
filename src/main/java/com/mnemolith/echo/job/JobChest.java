package com.mnemolith.echo.job;

import java.util.Map;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/** Walk to the bound chest and move stacks. Mining and farming deposit; building fetches. */
final class JobChest {
    private final EchoJob job;

    JobChest(EchoJob job) {
        this.job = job;
    }

    boolean needsDropOff(EchoEntity echo) {
        return EchoWork.freeMainSlots(echo.inventory()) <= CommonConfig.ECHO_DEPOSIT_FREE_SLOTS.get();
    }

    void goToChest(ServerLevel level, EchoEntity echo) {
        BlockPos chestPos = this.job.chest;
        if (chestPos == null) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.CHEST_UNAVAILABLE));
            return;
        }
        if (!level.isLoaded(chestPos)) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.UNLOADED));
            return;
        }
        if (this.job.mode == EchoJob.Mode.MINE) {
            this.job.setStatus(JobStatus.of(JobStatus.Kind.DEPOSIT, this.job.mined, 0));
        } else if (this.job.mode == EchoJob.Mode.FARM) {
            this.job.setStatus(JobStatus.of(JobStatus.Kind.DEPOSIT, this.job.farm.harvested, 0));
        }
        this.job.motion.startPath(level, echo, new EchoNav.Goal() {
            @Override
            public boolean reached(BlockPos feet) {
                Vec3 eye = new Vec3(feet.getX() + 0.5D, feet.getY() + 1.62D, feet.getZ() + 0.5D);
                return !feet.equals(chestPos) && eye.distanceToSqr(Vec3.atCenterOf(chestPos)) <= JobLimits.CHEST_REACH * JobLimits.CHEST_REACH;
            }

            @Override
            public double estimate(BlockPos feet) {
                return Math.max(0.0D, Math.sqrt(feet.distSqr(chestPos)) - 3.0D);
            }
        }, null, 0, true);
    }

    void atChest(ServerLevel level, EchoEntity echo) {
        BlockPos chestPos = this.job.chest;
        Container container = chestPos == null ? null : EchoWork.container(level, chestPos);
        if (chestPos == null || container == null || !EchoWork.mayOpen(level, echo, chestPos)) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.CHEST_UNAVAILABLE));
            return;
        }
        echo.lookAt(Vec3.atCenterOf(chestPos));
        echo.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, chestPos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        if (this.job.mode == EchoJob.Mode.MINE) {
            int moved = EchoWork.deposit(echo, container, Map.of());
            Mnemolith.LOGGER.info("Mnemolith echo deposit owner={} moved={} chest={}", echo.ownerName(), moved, chestPos.toShortString());
            if (this.needsDropOff(echo)) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.CHEST_FULL));
                return;
            }
            BlockPos target = this.job.mine.target;
            String detail = target == null
                    ? JobTexts.key(this.job.lesson.mining().get(0).block())
                    : JobTexts.key(level.getBlockState(target).getBlock());
            this.job.setStatus(new JobStatus(JobStatus.Kind.MINING, detail, this.job.mined, 0));
        } else if (this.job.mode == EchoJob.Mode.FARM) {
            int moved = EchoWork.depositFarm(echo, container, this.job.farm.seedItems(), 64);
            Mnemolith.LOGGER.info("Mnemolith echo farm deposit owner={} moved={} chest={}", echo.ownerName(), moved, chestPos.toShortString());
            if (this.needsDropOff(echo)) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.CHEST_FULL));
                return;
            }
            this.job.setStatus(new JobStatus(JobStatus.Kind.FARMING, this.job.farm.cropKey(), this.job.farm.harvested, 0));
        } else if (this.job.mode == EchoJob.Mode.BUILD) {
            Map<Item, Integer> wanted = this.job.build.wantedFromChest(level);
            for (Map.Entry<Item, Integer> entry : wanted.entrySet()) {
                entry.setValue(Math.max(0, entry.getValue() - JobTexts.count(echo.inventory(), entry.getKey())));
            }
            int taken = EchoWork.take(echo, container, wanted);
            Mnemolith.LOGGER.info("Mnemolith echo fetch owner={} taken={} chest={}", echo.ownerName(), taken, chestPos.toShortString());
        }
        level.playSound(null, chestPos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        this.job.motion.phase = JobMotion.Phase.SELECT;
    }
}
