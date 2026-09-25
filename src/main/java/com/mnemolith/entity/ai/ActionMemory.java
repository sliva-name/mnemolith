package com.mnemolith.entity.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.entity.MobTuning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Last whitelisted action per player, kept for five seconds. Server only. */
public final class ActionMemory {
    private static final Map<UUID, CopiedAction> LAST = new HashMap<>();

    private ActionMemory() {}

    public static void record(ServerPlayer player, CopiedActionKind kind, BlockPos pos, ItemStack stack) {
        LAST.put(player.getUUID(), new CopiedAction(kind, pos.immutable(), stack.copy(), player.level().getGameTime(), player.getUUID()));
    }

    public static Optional<CopiedAction> recent(ServerLevel level, ServerPlayer player) {
        CopiedAction action = LAST.get(player.getUUID());
        if (action == null) {
            return Optional.empty();
        }
        if (level.getGameTime() - action.gameTime() > MobTuning.ACTION_WINDOW_TICKS) {
            LAST.remove(player.getUUID());
            return Optional.empty();
        }
        return Optional.of(action);
    }

    /** Drops the player's last action (logout). Server thread only. */
    public static void forget(UUID player) {
        LAST.remove(player);
    }

    /** Drops every recorded action (server stopped). */
    public static void clearAll() {
        LAST.clear();
    }

    public record CopiedAction(CopiedActionKind kind, BlockPos pos, ItemStack stack, long gameTime, UUID player) {}
}
