package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.Archivist;
import java.util.EnumSet;
import org.jspecify.annotations.Nullable;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class FleeGoal extends Goal {
    private final Archivist archivist;
    private @Nullable BlockPos nest;

    public FleeGoal(Archivist archivist) {
        this.archivist = archivist;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.archivist.isStunned()) {
            return false;
        }
        if (this.archivist.isFleeing()) {
            return true;
        }
        return this.archivist.getLastHurtByMob() != null && this.archivist.tickCount - this.archivist.getLastHurtByMobTimestamp() < 40;
    }

    @Override
    public void start() {
        this.archivist.setAction(MobActions.FLEE);
    }

    @Override
    public void tick() {
        this.archivist.setAction(MobActions.FLEE);
        if (!MobTuning.sensorDue(this.archivist.tickCount, this.nest == null || this.archivist.getNavigation().isDone())) {
            return;
        }
        ServerLevel level = getServerLevel(this.archivist);
        this.nest = PathLedger.higherPressure(level, this.archivist.blockPosition());
        if (this.nest == null) {
            Vec3 look = this.archivist.getLookAngle();
            this.nest = BlockPos.containing(this.archivist.getX() + look.x * 8.0D, this.archivist.getY(), this.archivist.getZ() + look.z * 8.0D);
        }
        this.archivist.getNavigation().moveTo(this.nest.getX() + 0.5D, this.nest.getY(), this.nest.getZ() + 0.5D, 1.3D);
    }
}

