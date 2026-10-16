package com.mnemolith.entity.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

/**
 * Hushed players: a hushling's hit leaves a player whose actions write no imprints for a while. Kept per player as
 * the game time the hush ends, in memory only (a hush is seconds long). Server thread only.
 */
public final class Hush {
    private static final Map<UUID, Long> UNTIL = new HashMap<>();

    private Hush() {}

    public static void hush(ServerLevel level, UUID player, int ticks) {
        UNTIL.merge(player, level.getGameTime() + ticks, Math::max);
    }

    public static boolean hushed(ServerLevel level, UUID player) {
        Long until = UNTIL.get(player);
        if (until == null) {
            return false;
        }
        if (level.getGameTime() >= until) {
            UNTIL.remove(player);
            return false;
        }
        return true;
    }

    /** Ticks of hush left; 0 when not hushed. */
    public static long remaining(ServerLevel level, UUID player) {
        return hushed(level, player) ? UNTIL.get(player) - level.getGameTime() : 0L;
    }

    public static void forget(UUID player) {
        UNTIL.remove(player);
    }
}
