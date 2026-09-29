package com.mnemolith.content.item;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.content.MemoryNavigation;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * Survival compass: residual shard + vanilla compass. While carried it locks onto the nearest
 * residue, fracture chunk, or chronicle observatory via {@link LodestoneTracker} (tracked=false so
 * the needle keeps working without a lodestone POI). No {@code /locate} required.
 */
public class MemoryCompassItem extends Item {
    private static final int REFRESH_TICKS = 40;

    public MemoryCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(DataComponents.LODESTONE_TRACKER) || super.isFoil(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        if ((player.tickCount + player.getId()) % REFRESH_TICKS != 0) {
            return;
        }
        MemoryNavigation.Target target = MemoryNavigation.nearest(level, player.blockPosition());
        if (target == null) {
            if (stack.has(DataComponents.LODESTONE_TRACKER)) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
            return;
        }
        LodestoneTracker next = new LodestoneTracker(
                Optional.of(GlobalPos.of(level.dimension(), target.pos())),
                false);
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        if (current == null || !current.equals(next)) {
            stack.set(DataComponents.LODESTONE_TRACKER, next);
        }
    }
}
