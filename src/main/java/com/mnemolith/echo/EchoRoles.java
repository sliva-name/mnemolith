package com.mnemolith.echo;

import com.mnemolith.armory.ArmoryItems;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Role behavior for an idle echo. A working echo keeps its job; the warden's fracture exception is checked elsewhere. */
public final class EchoRoles {
    private EchoRoles() {}

    public static EchoRole roleFor(ItemStack stack) {
        if (stack.is(ArmoryItems.HUSH_SPEAR.get())) {
            return EchoRole.SCOUT;
        }
        if (stack.is(ArmoryItems.GRAVE_MAUL.get())) {
            return EchoRole.WARDEN;
        }
        if (stack.is(ArmoryItems.RECALL_BLADE.get()) || stack.is(ArmoryItems.SCAR_BRAND.get())) {
            return EchoRole.HUNTER;
        }
        if (stack.is(ArmoryItems.CHORUS_SLING.get())) {
            return EchoRole.GATHERER;
        }
        return null;
    }

    public static void tick(ServerLevel level, EchoEntity echo) {
        if (echo.role() == EchoRole.NONE) {
            return;
        }
        if (!echo.job().canRoleRoam()) {
            return;
        }
        switch (echo.role()) {
            case HUNTER -> hunt(level, echo);
            case WARDEN -> ward(level, echo);
            case GATHERER -> gather(level, echo);
            default -> {
            }
        }
    }

    private static void hunt(ServerLevel level, EchoEntity echo) {
        BlockPos mark = echo.huntMark(level.getGameTime());
        LivingEntity near = nearestMonster(level, echo, 14.0D);
        if (near != null && echo.distanceToSqr(near) < 6.0D) {
            strike(level, echo, near);
            return;
        }
        if (mark != null && echo.blockPosition().distSqr(mark) > 4.0D) {
            echo.job().roleWalk(level, echo, mark);
            return;
        }
        if (near != null) {
            echo.job().roleWalk(level, echo, near.blockPosition());
        }
    }

    private static void ward(ServerLevel level, EchoEntity echo) {
        Player owner = echo.onlineOwner();
        if (owner == null) {
            return;
        }
        LivingEntity threat = null;
        double best = 16.0D * 16.0D;
        for (Monster monster : level.getEntitiesOfClass(Monster.class, echo.getBoundingBox().inflate(16.0D))) {
            if (monster.getTarget() == owner && echo.distanceToSqr(monster) < best) {
                threat = monster;
                best = echo.distanceToSqr(monster);
            }
        }
        if (threat == null) {
            return;
        }
        if (echo.distanceToSqr(threat) < 6.0D) {
            strike(level, echo, threat);
        } else {
            echo.job().roleWalk(level, echo, threat.blockPosition());
        }
    }

    private static void gather(ServerLevel level, EchoEntity echo) {
        ItemEntity drop = null;
        double best = 12.0D * 12.0D;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, echo.getBoundingBox().inflate(12.0D))) {
            if (item.isAlive() && !item.getItem().isEmpty() && echo.distanceToSqr(item) < best) {
                drop = item;
                best = echo.distanceToSqr(item);
            }
        }
        if (drop == null) {
            return;
        }
        if (echo.distanceToSqr(drop) < 4.0D) {
            ItemStack stack = drop.getItem().copy();
            echo.inventory().insert(stack);
            if (stack.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(stack);
            }
            return;
        }
        echo.job().roleWalk(level, echo, drop.blockPosition());
    }

    private static void strike(ServerLevel level, EchoEntity echo, LivingEntity target) {
        if (echo.tickCount % 20 != 0) {
            return;
        }
        float damage = (float) echo.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damage < 1.0F) {
            damage = 1.0F;
        }
        target.hurt(level.damageSources().mobAttack(echo), damage);
        echo.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    private static LivingEntity nearestMonster(ServerLevel level, EchoEntity echo, double range) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (Monster monster : level.getEntitiesOfClass(Monster.class, echo.getBoundingBox().inflate(range))) {
            if (monster.isAlive() && echo.distanceToSqr(monster) < bestDistance) {
                best = monster;
                bestDistance = echo.distanceToSqr(monster);
            }
        }
        return best;
    }

    /** Silence a scout's work imprint. The caller still counts the action. */
    public static boolean swallowWork(EchoEntity echo) {
        return echo.role() == EchoRole.SCOUT;
    }
}
