package com.mnemolith.echo;

import com.mnemolith.audio.ModSounds;

import com.mnemolith.event.EchoActivatedEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.content.block.EchoHomeBlockEntity;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.MemoryFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        if (check == SpawnResult.LIMIT) {
            int limit = EchoProgress.echoLimit(player);
            String key = limit < CommonConfig.ECHO_MAX_PER_PLAYER_CAP.get()
                    ? "mnemolith.echo.activate_limit_chorus"
                    : "mnemolith.echo.activate_limit";
            player.sendSystemMessage(Component.translatable(key, limit), true);
            return check;
        }
        if (check != SpawnResult.SPAWNED) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.activate_" + check.name().toLowerCase(java.util.Locale.ROOT)), true);
            return check;
        }
        EchoEntity echo = spawn(player.level(), player, recording, stack.getOrDefault(ModDataComponents.ECHO_LESSON.get(), EchoLesson.NONE),
                stack.getOrDefault(ModDataComponents.ECHO_FARM.get(), FarmLesson.NONE),
                stack.getOrDefault(ModDataComponents.ECHO_LUMBER.get(), LumberLesson.NONE),
                stack.getOrDefault(ModDataComponents.ECHO_CARE.get(), CareLesson.NONE));
        if (echo == null) {
            return SpawnResult.EMPTY;
        }
        stack.shrink(1);
        player.sendSystemMessage(Component.translatable("mnemolith.echo.activated", recording.seconds()), true);
        NeoForge.EVENT_BUS.post(new EchoActivatedEvent(player, echo, recording));
        return SpawnResult.SPAWNED;
    }

    /** Creates a registered echo for {@code owner} and starts {@code recording}. The echo's inventory is empty. */
    public static @Nullable EchoEntity spawn(ServerLevel level, ServerPlayer owner, EchoRecording recording) {
        return spawn(level, owner, recording, EchoLesson.NONE);
    }

    /** As {@link #spawn(ServerLevel, ServerPlayer, EchoRecording)}, and the echo also learns {@code lesson} for jobs. */
    public static @Nullable EchoEntity spawn(ServerLevel level, ServerPlayer owner, EchoRecording recording, EchoLesson lesson) {
        return spawn(level, owner, recording, lesson, FarmLesson.NONE, LumberLesson.NONE, CareLesson.NONE);
    }

    /** As above, with a stage 3 farming lesson too. */
    public static @Nullable EchoEntity spawn(ServerLevel level, ServerPlayer owner, EchoRecording recording, EchoLesson lesson, FarmLesson farm) {
        return spawn(level, owner, recording, lesson, farm, LumberLesson.NONE, CareLesson.NONE);
    }

    /** As above, with O1 lumber and care lessons. */
    public static @Nullable EchoEntity spawn(ServerLevel level, ServerPlayer owner, EchoRecording recording, EchoLesson lesson, FarmLesson farm,
            LumberLesson lumber, CareLesson care) {
        EchoEntity echo = ModEntities.ECHO.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (echo == null) {
            return null;
        }
        echo.setOwner(owner);
        com.mnemolith.recall.UseMemory.stamp(echo, owner);
        echo.applyBonusHealth(EchoProgress.bonusHealth(owner));
        echo.setHealth(echo.getMaxHealth());
        echo.setGeneration(EchoRegistry.get(level.getServer()).put(owner.getUUID(), echo.getUUID()));
        echo.job().setLesson(lesson);
        echo.job().setFarmLesson(farm);
        echo.job().setLumberLesson(lumber);
        echo.job().setCareLesson(care);
        echo.startReplay(recording);
        level.addFreshEntity(echo);
        level.playSound(null, echo.blockPosition(), ModSounds.ECHO_WAKE.get(), SoundSource.PLAYERS, 0.9F, 0.7F);
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
        echo.job().setLumberLesson(stack.getOrDefault(ModDataComponents.ECHO_LUMBER.get(), LumberLesson.NONE));
        echo.job().setCareLesson(stack.getOrDefault(ModDataComponents.ECHO_CARE.get(), CareLesson.NONE));
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

    /** Despawns an idle owned echo into the pedestal (O3). Frees the live registry slot. */
    public static boolean house(ServerPlayer player, EchoEntity echo, EchoHomeBlockEntity home) {
        if (!echo.isOwnedBy(player) || echo.isReplaying() || !echo.isAlive()) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.home.busy"), true);
            return false;
        }
        if (home.isFull()) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.home.full", EchoHomeBlockEntity.CAP), true);
            return false;
        }
        StoredEcho stored = StoredEcho.capture(echo);
        if (!home.offer(stored)) {
            return false;
        }
        EchoRegistry.get(player.level().getServer()).remove(player.getUUID(), echo.getUUID());
        echo.discardSilently();
        player.level().playSound(null, home.getBlockPos(), ModSounds.ECHO_POSSESS.get(), SoundSource.BLOCKS, 0.8F, 0.6F);
        player.sendSystemMessage(Component.translatable("mnemolith.echo.home.housed",
                stored.customName().orElse(Component.translatable("entity.mnemolith.echo.named", stored.ownerName()))), true);
        return true;
    }

    /** Wakes the first housed echo onto {@code at} (O3). */
    public static boolean wake(ServerPlayer player, EchoHomeBlockEntity home, BlockPos at) {
        // The player's own first echo, whoever else sleeps in front of it.
        var taken = home.takeFirstOwnedBy(player.getUUID());
        if (taken.isEmpty()) {
            if (home.isEmpty()) {
                player.sendSystemMessage(Component.translatable("mnemolith.echo.home.empty"), true);
            } else {
                player.sendSystemMessage(Component.translatable("mnemolith.echo.not_yours", home.housed().get(0).ownerName()), true);
            }
            return false;
        }
        StoredEcho stored = taken.get();
        if (EchoRegistry.get(player.level().getServer()).count(player.getUUID()) >= EchoProgress.echoLimit(player)) {
            home.offer(stored);
            player.sendSystemMessage(Component.translatable("mnemolith.echo.activate_limit", EchoProgress.echoLimit(player)), true);
            return false;
        }
        ServerLevel level = player.level();
        EchoEntity echo = ModEntities.ECHO.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (echo == null) {
            home.offer(stored);
            return false;
        }
        echo.setUUID(stored.echo());
        echo.setOwner(player);
        com.mnemolith.recall.UseMemory.stamp(echo, player);
        echo.applyBonusHealth(stored.bonusHealth());
        echo.setHealth(Math.min(stored.health(), echo.getMaxHealth()));
        echo.setGeneration(EchoRegistry.get(level.getServer()).put(player.getUUID(), echo.getUUID()));
        echo.job().setLesson(stored.lesson());
        echo.job().setFarmLesson(stored.farm());
        echo.job().setLumberLesson(stored.lumber());
        echo.job().setCareLesson(stored.care());
        echo.setRole(stored.role());
        stored.graft().ifPresent(echo::setGraft);
        if (stored.scarred()) {
            echo.setScarred(true);
        }
        stored.customName().ifPresent(echo::setCustomName);
        for (SlotStack slot : stored.inventory()) {
            if (slot.slot() >= 0 && slot.slot() < echo.inventory().getContainerSize()) {
                echo.inventory().setItem(slot.slot(), slot.stack().copy());
            }
        }
        echo.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        level.addFreshEntity(echo);
        level.playSound(null, at, ModSounds.ECHO_WAKE.get(), SoundSource.BLOCKS, 0.9F, 1.2F);
        MemoryFx.mob(level, com.mnemolith.particle.ModParticles.COMPOSE_SUCCESS.get(), echo.getX(), echo.getY() + 1.0D, echo.getZ(), 10);
        player.sendSystemMessage(Component.translatable("mnemolith.echo.home.woken", echo.getDisplayName()), true);
        return true;
    }
}
