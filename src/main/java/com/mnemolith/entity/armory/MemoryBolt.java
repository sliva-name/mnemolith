package com.mnemolith.entity.armory;

import com.mnemolith.armory.ArmoryItems;
import com.mnemolith.armory.ChorusSlingItem;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A thrown memory bolt. On impact it glows the target and tells the shooter's nearest echo where to walk. */
public class MemoryBolt extends ThrowableItemProjectile {
    public MemoryBolt(EntityType<? extends MemoryBolt> type, Level level) {
        super(type, level);
    }

    public MemoryBolt(ServerLevel level, LivingEntity shooter, ItemStack stack) {
        super(ModEntities.MEMORY_BOLT.get(), shooter, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ArmoryItems.MEMORY_BOLT.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (hit.getEntity() instanceof LivingEntity living && this.getOwner() instanceof LivingEntity owner) {
            living.hurt(this.damageSources().thrown(this, owner), 4.0F);
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, true));
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof ServerPlayer player) {
            EchoEntity echo = ChorusSlingItem.nearestEcho(level, player);
            if (echo != null) {
                echo.markHunt(this.blockPosition(), level.getGameTime() + 200L);
            }
        }
        if (!this.level().isClientSide()) {
            this.discard();
        }
    }
}
