package com.mnemolith.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/**
 * E3 imprint sources wired to vanilla: lightning, portals, sculk noise, boss victories, villager trade.
 * W5: mute stone (and selective mute) also cancels GameEvents that carry a vibration frequency, so sculk
 * sensors and shriekers stay quiet in muted chunks. Ancient City quiet seeds are W1; Warden/dragon/wither
 * boss tags are written here via {@link #onBossDeath}.
 * Sleep consolidation stays in {@link SleepEvents}.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class ExpansionImprintEvents {
    /** Bolt entity id → game time of the imprint already written for it. */
    private static final Map<Integer, Long> LIGHTNING_WRITTEN = new HashMap<>();

    private ExpansionImprintEvents() {}

    @SubscribeEvent
    public static void onLightning(EntityStruckByLightningEvent event) {
        Entity struck = event.getEntity();
        if (struck instanceof EchoEntity echo && EchoGrafts.lightningProof(echo)) {
            event.setCanceled(true);
            return;
        }
        if (!(struck.level() instanceof ServerLevel level)) {
            return;
        }
        if (!CommonConfig.WRITE_LIGHTNING.get()) {
            return;
        }
        LightningBolt bolt = event.getLightning();
        int id = bolt.getId();
        long now = level.getGameTime();
        Long previous = LIGHTNING_WRITTEN.put(id, now);
        if (previous != null && previous == now) {
            return;
        }
        if (LIGHTNING_WRITTEN.size() > 64) {
            LIGHTNING_WRITTEN.entrySet().removeIf(entry -> now - entry.getValue() > 40L);
        }
        BlockPos pos = bolt.blockPosition();
        List<ImprintTag> tags = new ArrayList<>();
        tags.add(ImprintTag.LIGHTNING);
        UUID playerId = null;
        if (struck instanceof Player player) {
            tags.add(ImprintTag.PLAYER);
            playerId = player.getUUID();
        }
        ImprintWriter.write(level, pos, tags, playerId, false);
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel from)) {
            return;
        }
        // Wandering followers cross before the owner leaves this dimension.
        EchoGrafts.bringWanderingFollowers(player, from, event.getDimension());
        if (!CommonConfig.WRITE_PORTAL.get() || event.getDimension().equals(from.dimension())) {
            return;
        }
        List<ImprintTag> tags = List.of(ImprintTag.PORTAL, ImprintTag.PLAYER);
        ImprintWriter.write(from, player.blockPosition(), tags, player.getUUID(), false);
    }

    /**
     * W5: in a muted chunk, cancel any GameEvent that has a vibration frequency (sculk sensors / shriekers listen
     * to those). Selective mute counts as muted via {@link com.mnemolith.world.LoadedChunkMemory#isMuted}.
     * Runs at HIGH so imprint writes below still see a cancelled event and skip.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onMuteVibrations(VanillaGameEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var holder = event.getVanillaEvent();
        var frequency = holder.getData(net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps.VIBRATION_FREQUENCIES);
        boolean sculkVoice = holder.is(GameEvent.SHRIEK) || holder.is(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING);
        if (frequency == null && !sculkVoice) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getEventPosition());
        if (com.mnemolith.world.LoadedChunkMemory.isMuted(level, pos)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onSculk(VanillaGameEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!CommonConfig.WRITE_SCULK.get()) {
            return;
        }
        var holder = event.getVanillaEvent();
        boolean shriek = holder.is(GameEvent.SHRIEK);
        boolean sensor = holder.is(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING);
        if (!shriek && !sensor) {
            return;
        }
        Vec3 at = event.getEventPosition();
        BlockPos pos = BlockPos.containing(at);
        // Sensors chatter constantly; throttle them like build writes. Shrieks are rare and loud.
        boolean throttled = sensor;
        if (throttled && !ImprintWriter.acceptsThrottled(level, pos)) {
            return;
        }
        List<ImprintTag> tags = new ArrayList<>();
        tags.add(ImprintTag.SCULK);
        UUID playerId = null;
        Entity cause = event.getCause();
        if (cause instanceof Player player) {
            tags.add(ImprintTag.PLAYER);
            playerId = player.getUUID();
        }
        ImprintWriter.write(level, pos, tags, playerId, throttled);
    }

    @SubscribeEvent
    public static void onBossDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!CommonConfig.WRITE_BOSS.get()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (!isBoss(dead)) {
            return;
        }
        List<ImprintTag> tags = new ArrayList<>();
        tags.add(ImprintTag.BOSS);
        // Death is already written by ImprintEvents.onDeath; this adds the triumph tag beside it.
        UUID playerId = null;
        if (event.getSource().getEntity() instanceof Player player) {
            tags.add(ImprintTag.PLAYER);
            playerId = player.getUUID();
        }
        ImprintWriter.write(level, dead.blockPosition(), tags, playerId, false);
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!CommonConfig.WRITE_TRADE.get()) {
            return;
        }
        BlockPos pos = event.getAbstractVillager().blockPosition();
        if (!ImprintWriter.acceptsThrottled(level, pos)) {
            return;
        }
        ImprintWriter.write(level, pos, List.of(ImprintTag.TRADE, ImprintTag.PLAYER), player.getUUID(), true);
    }

    private static boolean isBoss(LivingEntity entity) {
        if (entity instanceof ScarEntity) {
            return true;
        }
        EntityType<?> type = entity.getType();
        return type == EntityTypes.ENDER_DRAGON
                || type == EntityTypes.WITHER
                || type == EntityTypes.WARDEN
                || type == EntityTypes.ELDER_GUARDIAN;
    }
}
