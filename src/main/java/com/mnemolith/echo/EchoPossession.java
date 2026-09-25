package com.mnemolith.echo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;
import com.mnemolith.entity.echo.EchoShell;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.EchoStatePayload;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.particle.ModParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Possession is a swap, decided on the server.
 * <p>
 * In: the real inventory, health, food, effects and experience move into the player's {@code echo_possession}
 * attachment, a shell is left standing, the echo's items and health move into the player, and the echo entity is
 * removed. Out: the player's current items become a new echo where they stand, the real state is restored from the
 * attachment, and the player returns to the shell. Items are always moved, never copied, inside one server tick.
 */
public final class EchoPossession {
    private EchoPossession() {}

    public enum Result {
        POSSESSED,
        DISABLED,
        ALREADY,
        NOT_YOURS,
        GONE,
        FAR,
        BUSY
    }

    public enum Reason {
        KEY,
        LOGOUT,
        DIMENSION,
        SERVER_STOP,
        RECOVER,
        BODY_DIED,
        SHELL_KILLED
    }

    public static PossessionState state(ServerPlayer player) {
        return player.getData(ModAttachments.ECHO_POSSESSION.get());
    }

    public static boolean isPossessing(ServerPlayer player) {
        return state(player).isActive();
    }

    public static @Nullable UUID shellOf(ServerPlayer player) {
        PossessionState.Data data = state(player).data();
        return data == null ? null : data.anchor().shell();
    }

    public static Result possess(ServerPlayer player, EchoEntity echo) {
        if (!CommonConfig.ECHOES_ENABLED.get()) {
            return Result.DISABLED;
        }
        if (isPossessing(player)) {
            return Result.ALREADY;
        }
        if (!echo.isOwnedBy(player) || player instanceof FakePlayer && !echo.isOwnedBy(player)) {
            return Result.NOT_YOURS;
        }
        if (!echo.isAlive() || echo.isRemoved()) {
            return Result.GONE;
        }
        double range = CommonConfig.ECHO_POSSESS_RANGE.get() + 2.0D;
        if (echo.level() != player.level() || echo.distanceToSqr(player) > range * range) {
            return Result.FAR;
        }
        if (player.isSpectator() || !player.isAlive() || player.isSleeping()) {
            return Result.BUSY;
        }
        ServerLevel level = player.level();
        player.stopRiding();
        player.stopUsingItem();
        settleMenus(player);
        if (EchoRecorder.isRecording(player)) {
            EchoRecorder.finish(player, "possess");
        }

        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        List<SlotStack> real = SlotStack.drain(inventory);
        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(new MobEffectInstance(effect));
        }
        PossessionState.Real realState = new PossessionState.Real(real, selected, player.getHealth(), player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(), effects, player.experienceLevel, player.experienceProgress, player.totalExperience);

        EchoShell shell = ModEntities.ECHO_SHELL.get().create(level, EntitySpawnReason.TRIGGERED);
        UUID shellId = shell == null ? UUID.randomUUID() : shell.getUUID();
        PossessionState.Anchor anchor = new PossessionState.Anchor(level.dimension(), player.position(), player.getYRot(), player.getXRot(), shellId);
        PossessionState.Body body = new PossessionState.Body(echo.getUUID(), echo.getMaxHealth(), Optional.ofNullable(echo.recording()), Optional.of(echo.job().save()));
        player.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState(new PossessionState.Data(realState, anchor, body)));

        if (shell != null) {
            shell.setOwner(player);
            shell.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            shell.setYHeadRot(player.getYHeadRot());
            shell.setYBodyRot(player.yBodyRot);
            var maxHealth = shell.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(player.getMaxHealth());
            }
            shell.setHealth(Math.max(1.0F, player.getHealth()));
            level.addFreshEntity(shell);
        }

        EchoInventory echoItems = echo.inventory();
        for (int i = 0; i < EchoInventory.SIZE; i++) {
            inventory.setItem(i, echoItems.removeItemNoUpdate(i));
        }
        inventory.setSelectedSlot(echo.selectedSlot());
        player.removeAllEffects();
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setHealth(Math.max(1.0F, Math.min(echo.getHealth(), player.getMaxHealth())));
        setExperience(player, 0, 0.0F, 0);
        player.clearFire();
        player.resetFallDistance();

        EchoRegistry.get(level.getServer()).retire(player.getUUID(), echo.getUUID());
        double x = echo.getX();
        double y = echo.getY();
        double z = echo.getZ();
        float yRot = echo.getYRot();
        float xRot = echo.getXRot();
        echo.discardSilently();
        teleport(player, level, x, y, z, yRot, xRot);
        sendHeldSlot(player);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.5F);
        MemoryFx.mob(level, ModParticles.IMPRINT_SHIMMER.get(), x, y + 1.0D, z, 16);
        sync(player);
        player.sendSystemMessage(Component.translatable("mnemolith.echo.possessed"), true);
        Mnemolith.LOGGER.info("Mnemolith echo possess player={} realItems={} echo={} shell={}", player.getGameProfile().name(), SlotStack.count(real), echo.getUUID(), shellId);
        return Result.POSSESSED;
    }

    /** Swaps back. Returns false when the player was not possessing. Safe to call from any exit path. */
    public static boolean unpossess(ServerPlayer player, Reason reason) {
        PossessionState state = state(player);
        PossessionState.Data data = state.data();
        if (data == null) {
            return false;
        }
        // Clear first: the teleport below may fire travel events that look at this state.
        player.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        player.stopRiding();
        player.stopUsingItem();
        settleMenus(player);
        ServerLevel here = player.level();
        BlockPos bodyPos = player.blockPosition();
        Inventory inventory = player.getInventory();
        int bodySelected = inventory.getSelectedSlot();
        List<SlotStack> bodyItems = SlotStack.drain(inventory);
        int bodyCount = SlotStack.count(bodyItems);
        float bodyHealth = player.getHealth();
        int gainedXp = Math.max(0, player.totalExperience);
        EchoRegistry registry = EchoRegistry.get(here.getServer());
        registry.remove(player.getUUID(), data.body().echo());

        if (reason == Reason.BODY_DIED) {
            for (SlotStack stack : bodyItems) {
                drop(here, player.getX(), player.getY(), player.getZ(), stack.stack());
            }
            EchoLife.onEchoBodyDied(here, player.getUUID(), data.body().echo(), bodyPos, player.getGameProfile().name());
        } else {
            spawnBody(here, player, data, bodyItems, bodySelected, bodyHealth);
        }

        ServerLevel anchorLevel = here.getServer().getLevel(data.anchor().dimension());
        if (anchorLevel == null) {
            anchorLevel = here.getServer().overworld();
        }
        Entity shell = anchorLevel.getEntity(data.anchor().shell());
        if (shell != null) {
            shell.discard();
        }

        PossessionState.Real real = data.real();
        for (SlotStack stack : real.items()) {
            if (stack.slot() >= 0 && stack.slot() < inventory.getContainerSize() && inventory.getItem(stack.slot()).isEmpty()) {
                inventory.setItem(stack.slot(), stack.stack().copy());
            } else if (!inventory.add(stack.stack().copy())) {
                drop(anchorLevel, data.anchor().pos().x, data.anchor().pos().y, data.anchor().pos().z, stack.stack().copy());
            }
        }
        if (Inventory.isHotbarSlot(real.selected())) {
            inventory.setSelectedSlot(real.selected());
        }
        player.removeAllEffects();
        for (MobEffectInstance effect : real.effects()) {
            player.addEffect(new MobEffectInstance(effect));
        }
        player.getFoodData().setFoodLevel(real.food());
        player.getFoodData().setSaturation(real.saturation());
        player.setHealth(Math.max(1.0F, Math.min(real.health(), player.getMaxHealth())));
        setExperience(player, real.xpLevel(), real.xpProgress(), real.xpTotal());
        if (gainedXp > 0) {
            player.giveExperiencePoints(gainedXp);
        }
        player.clearFire();
        player.resetFallDistance();
        teleport(player, anchorLevel, data.anchor().pos().x, data.anchor().pos().y, data.anchor().pos().z, data.anchor().yRot(), data.anchor().xRot());
        sendHeldSlot(player);
        anchorLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
        sync(player);
        if (reason != Reason.LOGOUT && reason != Reason.SERVER_STOP) {
            player.sendSystemMessage(Component.translatable("mnemolith.echo.returned_" + reason.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
        Mnemolith.LOGGER.info("Mnemolith echo unpossess player={} reason={} bodyItems={} realItems={}", player.getGameProfile().name(), reason, bodyCount, SlotStack.count(real.items()));
        return true;
    }

    private static void spawnBody(ServerLevel level, ServerPlayer player, PossessionState.Data data, List<SlotStack> items, int selected, float health) {
        EchoEntity echo = ModEntities.ECHO.get().create(level, EntitySpawnReason.TRIGGERED);
        if (echo == null) {
            for (SlotStack stack : items) {
                drop(level, player.getX(), player.getY(), player.getZ(), stack.stack());
            }
            return;
        }
        echo.setOwner(player.getUUID(), player.getGameProfile().name(), ResolvableProfile.createResolved(player.getGameProfile()));
        echo.applyConfiguredHealth();
        echo.setHealth(Math.max(1.0F, Math.min(health, echo.getMaxHealth())));
        echo.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        echo.setYHeadRot(player.getYHeadRot());
        echo.setYBodyRot(player.yBodyRot);
        data.body().recording().ifPresent(echo::keepRecording);
        data.body().job().ifPresent(echo::restoreJobIdle);
        EchoInventory inventory = echo.inventory();
        List<ItemStack> overflow = new ArrayList<>();
        for (SlotStack stack : items) {
            if (stack.slot() >= 0 && stack.slot() < EchoInventory.SIZE) {
                inventory.setItem(stack.slot(), stack.stack());
            } else {
                overflow.add(stack.stack());
            }
        }
        for (ItemStack stack : overflow) {
            inventory.insert(stack);
            if (!stack.isEmpty()) {
                drop(level, player.getX(), player.getY(), player.getZ(), stack);
            }
        }
        echo.setSelectedSlot(selected);
        echo.setGeneration(EchoRegistry.get(level.getServer()).put(player.getUUID(), echo.getUUID()));
        if (!level.addFreshEntity(echo)) {
            EchoRegistry.get(level.getServer()).remove(player.getUUID(), echo.getUUID());
            for (int i = 0; i < EchoInventory.SIZE; i++) {
                ItemStack stack = inventory.removeItemNoUpdate(i);
                if (!stack.isEmpty()) {
                    drop(level, player.getX(), player.getY(), player.getZ(), stack);
                }
            }
        }
    }

    /** Shell took damage: the stored real health follows it. */
    public static void onShellHealth(ServerPlayer owner, float health) {
        PossessionState state = state(owner);
        PossessionState.Data data = state.data();
        if (data != null) {
            owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState(new PossessionState.Data(data.real().withHealth(health), data.anchor(), data.body())));
        }
    }

    /** Shell died: the player is pulled back (the echo keeps its items where it stood) and dies at the shell. */
    public static void onShellKilled(ServerPlayer owner, DamageSource source) {
        if (!unpossess(owner, Reason.SHELL_KILLED)) {
            return;
        }
        owner.invulnerableTime = 0;
        owner.hurtServer(owner.level(), source, Float.MAX_VALUE);
        Mnemolith.LOGGER.info("Mnemolith echo shell killed player={} alive={}", owner.getGameProfile().name(), owner.isAlive());
    }

    /** Returns every possessing player on the server. Used when the server stops. */
    public static void releaseAll(Iterable<ServerPlayer> players, Reason reason) {
        for (ServerPlayer player : players) {
            if (isPossessing(player)) {
                unpossess(player, reason);
            }
        }
    }

    private static void settleMenus(ServerPlayer player) {
        player.closeContainer();
        player.inventoryMenu.removed(player);
    }

    private static void drop(ServerLevel level, double x, double y, double z, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(level, x, y + 0.5D, z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private static void setExperience(ServerPlayer player, int level, float progress, int total) {
        player.setExperienceLevels(level + 1);
        player.setExperienceLevels(level);
        player.experienceProgress = progress;
        player.totalExperience = total;
    }

    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        player.teleportTo(level, x, y, z, Set.of(), yRot, xRot, true);
        if (player instanceof FakePlayer) {
            player.snapTo(x, y, z, yRot, xRot);
        }
    }

    private static void sendHeldSlot(ServerPlayer player) {
        if (!(player instanceof FakePlayer)) {
            player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
        }
    }

    public static void sync(ServerPlayer player) {
        if (!(player instanceof FakePlayer) && player.connection != null) {
            PacketDistributor.sendToPlayer(player, new EchoStatePayload(isPossessing(player)));
        }
    }

    public static Level levelOf(ServerPlayer player) {
        return player.level();
    }
}
