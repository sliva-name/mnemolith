package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.MobTuning;
import com.mnemolith.entity.ai.ActionMemory;
import com.mnemolith.entity.ai.CopiedActionKind;
import com.mnemolith.entity.ai.PathLedger;
import com.mnemolith.entity.mob.Archivist;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Event interest for the archivist and the replicant's action window. No chest scans. */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class MobEvents {
    private static final Map<UUID, Boolean> WAS_ON_GROUND = new HashMap<>();

    private MobEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // Written only on the server thread; the integrated client used to share this map.
        boolean grounded = player.onGround();
        Boolean was = WAS_ON_GROUND.put(player.getUUID(), grounded);
        PathLedger.note(serverPlayer);
        if (was != null && was && !grounded && player.getDeltaMovement().y > 0.2D) {
            ActionMemory.record(serverPlayer, CopiedActionKind.JUMP, serverPlayer.blockPosition(), ItemStack.EMPTY);
        }
    }

    /** Drops the player's per-player mob state (logout). Server thread only. */
    public static void forget(UUID player) {
        WAS_ON_GROUND.remove(player);
        ActionMemory.forget(player);
        PathLedger.forget(player);
    }

    /** Drops all per-player mob state (server stopped). */
    public static void clearAll() {
        WAS_ON_GROUND.clear();
        ActionMemory.clearAll();
        PathLedger.clearAll();
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            ActionMemory.record(player, CopiedActionKind.MELEE, player.blockPosition(), player.getMainHandItem());
        }
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ActionMemory.record(player, CopiedActionKind.USE, player.blockPosition(), event.getItemStack());
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ActionMemory.record(player, CopiedActionKind.PLACE, event.getPos(), new ItemStack(event.getPlacedBlock().getBlock()));
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Level level = player.level();
        AABB box = player.getBoundingBox().inflate(MobTuning.INTEREST_RANGE);
        for (Archivist archivist : level.getEntitiesOfClass(Archivist.class, box)) {
            archivist.noticeMenu(player, event.getContainer());
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(MobTuning.INTEREST_RANGE);
        for (Archivist archivist : player.level().getEntitiesOfClass(Archivist.class, box)) {
            archivist.noticeDrop(event.getEntity());
        }
    }
}
