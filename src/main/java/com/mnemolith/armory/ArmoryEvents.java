package com.mnemolith.armory;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.MemoryMob;
import com.mnemolith.entity.mob.FractureStalker;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Set bonuses that are not attribute lines on a single piece. */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class ArmoryEvents {
    private static final Identifier GRAVE_WEIGHT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "grave_weight");
    private static final Identifier HUSH_STEP = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "hush_step");
    private static final Identifier SCAR_EDGE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "scar_edge");

    private ArmoryEvents() {}

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player)) {
            return;
        }
        LivingEntity hunter = event.getEntity();
        boolean memory = hunter instanceof MemoryMob || hunter instanceof FractureStalker;
        if (!memory || hunter.getLastHurtByMob() == player) {
            return;
        }
        if (Armory.full(player, ArmorySet.HUSH) && player.isShiftKeyDown()) {
            event.setNewAboutToBeSetTarget(null);
            return;
        }
        if (hunter instanceof FractureStalker && Armory.full(player, ArmorySet.SCAR)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !Armory.full(player, ArmorySet.GRAVE)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Monster) {
            event.setAmount(event.getAmount() * CommonConfig.GRAVE_DAMAGE_MULT.get().floatValue());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean grave = Armory.full(player, ArmorySet.GRAVE);
        boolean hush = Armory.full(player, ArmorySet.HUSH) && player.isShiftKeyDown();
        boolean scar = Armory.full(player, ArmorySet.SCAR) && Armory.inFracture(player);
        toggle(player.getAttribute(Attributes.MOVEMENT_SPEED), GRAVE_WEIGHT, grave, CommonConfig.GRAVE_SPEED_PENALTY.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        toggle(player.getAttribute(Attributes.MOVEMENT_SPEED), HUSH_STEP, hush, CommonConfig.HUSH_STEP_BONUS.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        toggle(player.getAttribute(Attributes.ATTACK_DAMAGE), SCAR_EDGE, scar, 2.0D, AttributeModifier.Operation.ADD_VALUE);
        if (scar && player.tickCount % 200 == 0 && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            LevelChunk chunk = level.getChunkAt(player.blockPosition());
            ChunkMemory memory = LoadedChunkMemory.existing(chunk);
            if (memory != null) {
                com.mnemolith.imprint.ImprintWriter.spike(level, player.blockPosition(), 1);
            }
        }
    }

    private static void toggle(AttributeInstance attribute, Identifier id, boolean on, double amount, AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        if (on) {
            if (!attribute.hasModifier(id)) {
                attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
            }
        } else if (attribute.hasModifier(id)) {
            attribute.removeModifier(id);
        }
    }
}
