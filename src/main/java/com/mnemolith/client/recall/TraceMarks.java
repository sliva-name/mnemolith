package com.mnemolith.client.recall;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.client.config.ClientConfig;
import com.mnemolith.network.TraceMarkPayload;
import com.mnemolith.particle.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Footsteps at a trace the server already chose to show. They last longer than a stage-1 flash.
 * Nothing here writes world state.
 */
public final class TraceMarks {
    private static final int CAP = 4;
    private static final List<Mark> MARKS = new ArrayList<>();

    private TraceMarks() {}

    public static void accept(TraceMarkPayload payload) {
        BlockPos pos = payload.pos();
        for (Mark mark : MARKS) {
            if (mark.pos.equals(pos)) {
                mark.age = 0;
                mark.ticks = Math.max(20, payload.ticks());
                return;
            }
        }
        if (MARKS.size() >= CAP) {
            MARKS.remove(0);
        }
        MARKS.add(new Mark(pos, Math.max(20, payload.ticks())));
    }

    public static void onClientTick(ClientTickEvent.Pre event) {
        if (MARKS.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        double density = ClientConfig.PARTICLE_DENSITY.get();
        for (int i = MARKS.size() - 1; i >= 0; i--) {
            Mark mark = MARKS.get(i);
            mark.age++;
            if (mark.age > mark.ticks) {
                MARKS.remove(i);
                continue;
            }
            if (level == null || density <= 0.0D || mark.age % 8 != 0) {
                continue;
            }
            int step = (mark.age / 8) % 4;
            double x = mark.pos.getX() + 0.35D + step * 0.35D;
            double z = mark.pos.getZ() + 0.5D;
            level.addParticle(ModParticles.STRIDER_TRAIL.get(), x, mark.pos.getY() + 0.05D, z, 0.0D, 0.01D, 0.0D);
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MARKS.clear();
    }

    private static final class Mark {
        private final BlockPos pos;
        private int ticks;
        private int age;

        private Mark(BlockPos pos, int ticks) {
            this.pos = pos;
            this.ticks = ticks;
        }
    }
}
