package com.mnemolith.entity.ai;

import com.mnemolith.entity.mob.Archivist;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

public final class StunGoal extends Goal {
    private final Archivist archivist;

    public StunGoal(Archivist archivist) {
        this.archivist = archivist;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return this.archivist.stunTicks > 0;
    }
}

