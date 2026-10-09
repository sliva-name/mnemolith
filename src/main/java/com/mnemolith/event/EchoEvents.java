package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.FakePlace;
import com.mnemolith.echo.EchoRecorder;
import com.mnemolith.entity.echo.MemoryAvatar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Echo hooks: self-recording, drops of echo breaks, and every exit path out of a possession.
 * Recording listens at the lowest priority and ignores cancelled events, so it only keeps what really happened.
 * Fake players are ignored by the recorder: the echo's own hands share the owner's UUID.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class EchoEvents {
    private EchoEvents() {}

    private static boolean realPlayer(Object entity) {
        return entity instanceof ServerPlayer && !(entity instanceof FakePlayer);
    }

    // ---- recording ----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (realPlayer(event.getEntity())) {
            EchoRecorder.tick((ServerPlayer) event.getEntity());
            if (EchoPossession.isPossessing((ServerPlayer) event.getEntity())) {
                com.mnemolith.echo.graft.EchoGrafts.possessedTick((ServerPlayer) event.getEntity());
            }
        }
    }

    /** Memory grafts: a possessed plunging body takes no fall damage; a plunging echo pays for long drops. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && EchoPossession.isPossessing(player)) {
            com.mnemolith.echo.graft.EchoGrafts.possessedFall(player, event);
        } else if (event.getEntity() instanceof com.mnemolith.entity.echo.EchoEntity echo && !echo.level().isClientSide()) {
            com.mnemolith.echo.graft.EchoGrafts.onEchoFall(echo, event);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof FakePlayer fake) {
            EchoHands.lastEventActor = fake.getUUID();
        }
        if (!event.isCanceled() && realPlayer(event.getPlayer())) {
            EchoRecorder.onBreak((ServerPlayer) event.getPlayer(), event.getPos(), event.getState());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() && realPlayer(event.getEntity())) {
            EchoRecorder.onRightClickBlock((ServerPlayer) event.getEntity(), event.getHand(), event.getHitVec());
        }
    }

    /** O1: teach animal care from shear / milk / breed interactions during a recording. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !realPlayer(event.getEntity())) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (!EchoRecorder.isRecording(player)) {
            return;
        }
        var target = event.getTarget();
        var stack = player.getItemInHand(event.getHand());
        boolean shear = stack.is(net.minecraft.world.item.Items.SHEARS) && target instanceof net.minecraft.world.entity.Shearable;
        boolean milk = stack.is(net.minecraft.world.item.Items.BUCKET) && target instanceof net.minecraft.world.entity.animal.cow.AbstractCow;
        boolean breed = target instanceof net.minecraft.world.entity.animal.Animal animal && animal.isFood(stack);
        if (shear || milk || breed) {
            EchoRecorder.onCare(player, shear, milk, breed, breed ? stack.getItem() : null);
        }
    }

    /** Guard: teach from melee hits on hostile mobs during a recording. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuardHit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (event.getInflictedDamage() <= 0.0F || !(guardHitter(event.getSource()) instanceof ServerPlayer player)
                || !realPlayer(player) || !EchoRecorder.isRecording(player)) {
            return;
        }
        EchoRecorder.onGuardHit(player, event.getEntity(), false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuardKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.isCanceled() || !(guardHitter(event.getSource()) instanceof ServerPlayer player)
                || !realPlayer(player) || !EchoRecorder.isRecording(player)) {
            return;
        }
        EchoRecorder.onGuardHit(player, event.getEntity(), true);
    }

    /** The player behind a melee hit or one of their arrows, for the guard lesson; null for anything else. */
    private static net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity guardHitter(net.minecraft.world.damagesource.DamageSource source) {
        net.minecraft.world.entity.Entity direct = source.getDirectEntity();
        net.minecraft.world.entity.Entity cause = source.getEntity();
        if (!(cause instanceof ServerPlayer)) {
            return null;
        }
        return direct == cause || direct instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow ? cause : null;
    }

    /** A guard's arrow killed a hostile mob: it counts for the guard's kills and the owner's advancement. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEchoArrowKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!event.isCanceled() && event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow
                && event.getSource().getEntity() instanceof com.mnemolith.entity.echo.EchoEntity echo
                && echo.level() instanceof net.minecraft.server.level.ServerLevel level) {
            echo.job().onGuardArrowKill(level, echo, event.getEntity());
        }
    }

    /** A guard's raised shield stopped a hit: count it and wear the shield. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEchoShieldBlock(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent event) {
        if (event.getBlocked() && event.getBlockedDamage() > 0.0F && event.getEntity() instanceof com.mnemolith.entity.echo.EchoEntity echo
                && echo.level() instanceof net.minecraft.server.level.ServerLevel level) {
            echo.job().onGuardBlocked();
            // Vanilla only wears a player's shield; an echo's shield wears the same way.
            net.minecraft.world.item.ItemStack shield = echo.getUseItem();
            var blocks = shield.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
            if (blocks != null) {
                int wear = event.shieldDamage() >= 0 ? event.shieldDamage() : blocks.itemDamage().apply(event.getBlockedDamage());
                if (wear > 0) {
                    net.minecraft.world.entity.EquipmentSlot slot = echo.getUsedItemHand().asEquipmentSlot();
                    shield.hurtAndBreak(wear, level, echo, item -> echo.onEquippedItemBroken(item, slot));
                }
            }
        }
    }

    /** Arrows an echo guard fires never hit the owner, players, villagers, pets or passive mobs: they fly through. */
    @SubscribeEvent
    public static void onEchoArrowImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && event.getProjectile().getOwner() instanceof com.mnemolith.entity.echo.EchoEntity echo
                && !com.mnemolith.echo.job.GuardController.canFight(echo, hit.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        // A replicant ghost also uses a fake player. It is not an echo hand, so it does not become the QA actor.
        if (!event.isCanceled() && event.getEntity() instanceof FakePlayer fake && !FakePlace.skippingOwnerImprint()) {
            EchoHands.lastEventActor = fake.getUUID();
        }
        if (!event.isCanceled() && realPlayer(event.getEntity())) {
            EchoRecorder.onPlace((ServerPlayer) event.getEntity(), event.getPos(), event.getPlacedBlock());
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        EchoHands.onBlockDrops(event);
    }

    // ---- death ----

    /** Totems run before this. A possessed body that still dies hands the player back to the shell instead. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeathFirst(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EchoPossession.isPossessing(player)) {
            event.setCanceled(true);
            player.setHealth(1.0F);
            EchoPossession.unpossess(player, EchoPossession.Reason.BODY_DIED);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeathLast(LivingDeathEvent event) {
        if (!event.isCanceled() && realPlayer(event.getEntity())) {
            EchoRecorder.finish((ServerPlayer) event.getEntity(), "death");
        }
    }

    // ---- possession exits ----

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EchoPossession.isPossessing(player)
                && !event.getDimension().equals(player.level().dimension())) {
            event.setCanceled(true);
            EchoPossession.unpossess(player, EchoPossession.Reason.DIMENSION);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (EchoPossession.isPossessing(player)) {
                EchoPossession.unpossess(player, EchoPossession.Reason.DIMENSION);
            }
            if (realPlayer(player)) {
                EchoRecorder.finish(player, "dimension");
            }
        }
    }

    /** Fires before the player file is written, so the swapped-back state is what gets saved. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (realPlayer(player)) {
                EchoRecorder.finish(player, "logout");
            }
            EchoPossession.unpossess(player, EchoPossession.Reason.LOGOUT);
        }
    }

    /** A crash can leave a saved possession behind. Swap back on join; the stale echo copy retires itself. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (EchoPossession.isPossessing(player)) {
                EchoPossession.unpossess(player, EchoPossession.Reason.RECOVER);
            } else {
                EchoPossession.sync(player);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EchoPossession.isPossessing(player)) {
            EchoPossession.unpossess(player, EchoPossession.Reason.RECOVER);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            EchoRecorder.finish(player, "server_stop");
            EchoPossession.unpossess(player, EchoPossession.Reason.SERVER_STOP);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EchoRecorder.clearAll();
        MemoryAvatar.STAND_INS.clear();
    }
}
