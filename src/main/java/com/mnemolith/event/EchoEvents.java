package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoPossession;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof FakePlayer fake) {
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
