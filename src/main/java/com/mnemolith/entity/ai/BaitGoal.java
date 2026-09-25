package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.Archivist;
import java.util.EnumSet;
import org.jspecify.annotations.Nullable;
import com.mnemolith.entity.MobTuning;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

public final class BaitGoal extends Goal {
    private final Archivist archivist;
    private @Nullable ItemEntity bait;

    public BaitGoal(Archivist archivist) {
        this.archivist = archivist;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!MobTuning.archivistEnabled() || this.archivist.isStunned() || this.archivist.isFleeing()) {
            return false;
        }
        this.bait = this.archivist.nearestBait();
        return this.bait != null;
    }

    @Override
    public void tick() {
        ItemEntity bait = this.bait;
        if (bait == null || !bait.isAlive()) {
            return;
        }
        if (MobTuning.sensorDue(this.archivist.tickCount, this.archivist.getNavigation().isDone())) {
            this.archivist.getNavigation().moveTo(bait, 1.1D);
        }
        this.archivist.getLookControl().setLookAt(bait, 30.0F, 30.0F);
        if (this.archivist.distanceToSqr(bait) < 2.0D) {
            bait.getItem().shrink(1);
            if (bait.getItem().isEmpty()) {
                bait.discard();
            }
            this.archivist.stunByBait();
            getServerLevel(this.archivist).sendParticles(ParticleTypes.HAPPY_VILLAGER, bait.getX(), bait.getY(), bait.getZ(), 6, 0.2D, 0.2D, 0.2D, 0.0D);
        }
    }
}

