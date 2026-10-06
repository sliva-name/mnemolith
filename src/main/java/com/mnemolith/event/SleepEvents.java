package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/**
 * After a full sleep the bed chunk cools: instability drops and quiet imprints fade.
 * In an overloaded house the dream turns into a short nightmare. A lie-down that ends with "Leave Bed" before
 * morning, or being shaken awake, does neither (see {@link #sleptThrough}).
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class SleepEvents {
    private SleepEvents() {}

    /**
     * True when a wake-up ends a real night's sleep: not shaken awake, asleep long enough to count (the vanilla five
     * seconds a night skip needs), and either the level itself woke everyone (the night was skipped) or it is already
     * morning. Vanilla wakes with {@code (wakeImmediately, updateLevel)}: a night skip {@code (false, false)}, morning
     * in bed {@code (false, true)} once it is bright, the "Leave Bed" button {@code (false, true)} at any time, and
     * damage, a teleport, a missing bed or a disconnect {@code (true, ...)}. So the time of day is what tells the
     * button apart from morning.
     */
    public static boolean sleptThrough(boolean wakeImmediately, boolean updateLevel, boolean longEnough, boolean bright) {
        return !wakeImmediately && longEnough && (!updateLevel || bright);
    }

    /** {@link #sleptThrough(boolean, boolean, boolean, boolean)} for a player waking now. Call before the bed is left. */
    public static boolean sleptThrough(ServerPlayer player, boolean wakeImmediately, boolean updateLevel) {
        return sleptThrough(wakeImmediately, updateLevel, player.isSleepingLongEnough(), player.level().isBrightOutside());
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Only a whole night counts, for the home and for consolidation alike. The "Leave Bed" button wakes the player
        // the same way morning does (not forcefully), so wakeImmediately alone let a lie-down at night cool the chunk.
        boolean wholeNight = sleptThrough(player, event.wakeImmediately(), event.updateLevel());
        if (!wholeNight) {
            return;
        }
        // The world remembers the bed as a home only after a whole night in it, not a lie-down.
        player.getSleepingPos().ifPresent(bed -> com.mnemolith.recall.RememberYou.onHome(player, bed));
        if (!CommonConfig.SLEEP_CONSOLIDATION.get()) {
            return;
        }
        BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
        LevelChunk chunk = level.getChunkAt(bed);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null) {
            return;
        }
        long now = level.getGameTime();
        int cooled = memory.coolInstability(CommonConfig.SLEEP_COOL_AMOUNT.get());
        int faded = 0;
        int passes = CommonConfig.SLEEP_FADE_PASSES.get();
        for (int i = 0; i < passes; i++) {
            if (memory.fadeQuiet(now, CommonConfig.QUIET_FADE_TICKS.get())) {
                faded++;
            } else {
                break;
            }
        }
        PressureBand band = MemoryPressure.recompute(chunk, memory);
        boolean nightmare = CommonConfig.SLEEP_NIGHTMARE.get()
                && (band == PressureBand.OVERLOADED || band == PressureBand.FRACTURE);
        if (nightmare) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false));
            MobSpawns.trySpawnReplicant(level, bed.above());
            player.sendSystemMessage(Component.translatable("mnemolith.sleep.nightmare"), true);
        } else if (cooled > 0 || faded > 0) {
            player.sendSystemMessage(Component.translatable("mnemolith.sleep.cooled"), true);
        }
    }
}
