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
 * In an overloaded house the dream turns into a short nightmare.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class SleepEvents {
    private SleepEvents() {}

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // The world remembers the bed as a home, however short the night.
        player.getSleepingPos().ifPresent(bed -> com.mnemolith.recall.RememberYou.onHome(player, bed));
        // Punching out of bed early skips consolidation.
        if (event.wakeImmediately()) {
            return;
        }
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
