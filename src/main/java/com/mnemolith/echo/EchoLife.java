package com.mnemolith.echo;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.MemoryFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/** Spawning, re-teaching, and the death of echo bodies. */
public final class EchoLife {
    private EchoLife() {}

    public enum SpawnResult {
        SPAWNED,
        DISABLED,
        NOT_YOURS,
        WRONG_DIMENSION,
        TOO_FAR,
        LIMIT,
        POSSESSING,
        EMPTY
    }

    public static SpawnResult canActivate(ServerPlayer player, EchoRecording recording) {
        if (!CommonConfig.ECHOES_ENABLED.get()) {
            return SpawnResult.DISABLED;
        }
        if (!recording.owner().equals(player.getUUID())) {
            return SpawnResult.NOT_YOURS;
        }
        if (recording.length() == 0) {
            return SpawnResult.EMPTY;
        }
        if (EchoPossession.isPossessing(player)) {
            return SpawnResult.POSSESSING;
        }
        if (!player.level().dimension().equals(recording.dimension())) {
            return SpawnResult.WRONG_DIMENSION;
        }
        double range = CommonConfig.ECHO_ACTIVATE_RANGE.get();
        if (player.position().distanceToSqr(recording.origin()) > range * range) {
            return SpawnResult.TOO_FAR;
        }
        return SpawnResult.SPAWNED;
    }

    /** Uses one filled recording from {@code stack}: a new echo appears at the recording's start and replays it. */
    public static SpawnResult activate(ServerPlayer player, ItemStack stack) {
        EchoRecording recording = stack.get(ModDataComponents.ECHO_RECORDING.get());
        if (recording == null) {
            return SpawnResult.EMPTY;
        }
        SpawnResult check = canActivate(player, recording);
        if (check == SpawnResult.SPAWNED && EchoRegistry.get(player.level().getServer()).count(player.getUUID()) >= EchoProgress.echoLimit(player)) {
            check = SpawnResult.LIMIT;
        }
        if (check != SpawnResult.SPAWNED) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.activate_" + check.name().toLowerCase(java.util.Locale.ROOT)), true);
            return check;
        }
        EchoEntity echo = spawn(player.level(), player, recording, stack.getOrDefault(ModDataComponents.ECHO_LESSON.get(), EchoLesson.NONE),
                stack.getOrDefault(ModDataComponents.ECHO_FARM.get(), FarmLesson.NONE));
        if (echo == null) {
            return SpawnResult.EMPTY;
        }
        stack.shrink(1);
        player.sendSystemMessage(Component.translatable("mnemolith.echo.activated", recording.seconds()), true);
        return SpawnResult.SPAWNED;
    }

    /** Creates a registered echo for {@code owner} and starts {@code recording}. The echo's inventory is empty. */
    public static @Nullable EchoEntity spawn(ServerLevel level, ServerPlayer owner, EchoRecording recording) {
        return spawn(level, owner, recording, EchoLesson.NONE);
    }

    /** As {@link #spawn(ServerLevel, ServerPlayer, EchoRecording)}, and the echo also learns {@code lesson} for jobs. */
    public static @Nullable EchoEntity spawn(ServerLevel level, ServerPlayer owner, EchoRecording recording, EchoLesson lesson) {
        return spawn(level, owner, recording, lesson, FarmLesson.NONE);
    }

    /** As above, with a stage 3 farming lesson too. */
    public static @Nullable EchoEntity spawn(ServerLevel level, ServerPlayer owner, EchoRecording recording, EchoLesson lesson, FarmLesson farm) {
        EchoEntity echo = ModEntities.ECHO.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (echo == null) {
            return null;
        }
        echo.setOwner(owner);
        echo.applyBonusHealth(EchoProgress.bonusHealth(owner));
        echo.setHealth(echo.getMaxHealth());
        echo.setGeneration(EchoRegistry.get(level.getServer()).put(owner.getUUID(), echo.getUUID()));
        echo.job().setLesson(lesson);
        echo.job().setFarmLesson(farm);
        echo.startReplay(recording);
        level.addFreshEntity(echo);
        level.playSound(null, echo.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 0.7F);
        MemoryFx.mob(level, com.mnemolith.particle.ModParticles.COMPOSE_SUCCESS.get(), echo.getX(), echo.getY() + 1.0D, echo.getZ(), 12);
        Mnemolith.LOGGER.info("Mnemolith echo spawned owner={} frames={} actions={} at {}", owner.getGameProfile().name(), recording.length(), recording.actions().size(), echo.blockPosition().toShortString());
        return echo;
    }

    /** Re-teaches an existing echo: it walks back to the new recording's start and replays it. Its items stay. */
    public static boolean teach(ServerPlayer player, EchoEntity echo, ItemStack stack) {
        EchoRecording recording = stack.get(ModDataComponents.ECHO_RECORDING.get());
        if (recording == null) {
            return false;
        }
        SpawnResult check = canActivate(player, recording);
        if (check != SpawnResult.SPAWNED) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.activate_" + check.name().toLowerCase(java.util.Locale.ROOT)), true);
            return false;
        }
        echo.job().setFarmLesson(stack.getOrDefault(ModDataComponents.ECHO_FARM.get(), FarmLesson.NONE));
        echo.teachLesson(stack.getOrDefault(ModDataComponents.ECHO_LESSON.get(), EchoLesson.NONE));
        echo.startReplay(recording);
        stack.shrink(1);
        player.sendSystemMessage(Component.translatable("mnemolith.echo.taught", recording.seconds()), true);
        return true;
    }

    /**
     * An echo body died (idle, or while possessed). Frees the owner's slot and shakes the chunk:
     * a death imprint where it fell plus the configured instability spike.
     */
    public static void onEchoBodyDied(ServerLevel level, @Nullable UUID owner, UUID echo, BlockPos pos, String ownerName) {
        EchoRegistry.get(level.getServer()).remove(owner, echo);
        int spike = CommonConfig.ECHO_DEATH_PRESSURE_SPIKE.get();
        int pressure = spike > 0 ? ImprintWriter.spike(level, pos, spike) : -1;
        ImprintWriter.write(level, pos, List.of(ImprintTag.DEATH), owner, false);
        Mnemolith.LOGGER.info("Mnemolith echo body died owner={} at {} spike={} pressure={}", ownerName, pos.toShortString(), spike, pressure);
    }
}
