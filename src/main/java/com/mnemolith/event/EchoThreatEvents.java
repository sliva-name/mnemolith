package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.MemoryMob;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Stage 3: ordinary hostile mobs may hunt a working echo. The goal sits below their player target (priority 3), only
 * matches an echo while it works ({@link EchoEntity#attractsMobs()}), and skips creepers (no terrain damage from echo
 * work), neutral mobs and this mod's own memory mobs, which have their own echo behaviour.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class EchoThreatEvents {
    private EchoThreatEvents() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob mob) || !(mob instanceof Enemy)) {
            return;
        }
        if (mob instanceof Creeper || mob instanceof NeutralMob || mob instanceof MemoryMob) {
            return;
        }
        mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, EchoEntity.class, 10, true, false,
                (target, level) -> target instanceof EchoEntity echo && echo.attractsMobs()));
    }
}
