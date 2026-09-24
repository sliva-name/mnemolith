package com.mnemolith.client.particle;

import com.mnemolith.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;

/** Client cap for custom memory particles. Dedicated servers do not load this class. */
final class MemoryFxBudget {
    private static final double NEAR = 24.0D * 24.0D;
    private static final double FAR = 48.0D * 48.0D;
    private static int tick = Integer.MIN_VALUE;
    private static int spawned;

    private MemoryFxBudget() {}

    static boolean allow(double x, double y, double z, RandomSource random) {
        if (!ClientConfig.IMPRINT_PARTICLES.get()) {
            return false;
        }
        double density = ClientConfig.PARTICLE_DENSITY.get();
        if (density <= 0.0D) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        double distance = player.distanceToSqr(x, y, z);
        if (distance > FAR) {
            return false;
        }
        if (distance > NEAR && random.nextDouble() > density) {
            return false;
        }
        int now = player.tickCount;
        if (now != tick) {
            tick = now;
            spawned = 0;
        }
        int cap = Math.max(1, (int) Math.round(48.0D * density));
        if (spawned >= cap) {
            return false;
        }
        spawned++;
        return true;
    }
}
