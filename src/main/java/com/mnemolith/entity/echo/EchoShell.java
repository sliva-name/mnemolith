package com.mnemolith.entity.echo;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.EchoPossession;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * The body a player leaves behind while possessing an echo. It holds no items: the real inventory lives in
 * the player's {@code echo_possession} attachment. Damage here is damage to the stored real health.
 */
public class EchoShell extends MemoryAvatar {
    public static EntityType.@Nullable EntityFactory<EchoShell> clientFactory;

    protected EchoShell(EntityType<? extends EchoShell> type, Level level) {
        super(type, level);
    }

    public static EchoShell create(EntityType<EchoShell> type, Level level) {
        if (level.isClientSide() && clientFactory != null) {
            return clientFactory.create(type, level);
        }
        return new EchoShell(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    /** Owner online and still possessing with this exact shell. Anything else means the shell is left over. */
    private @Nullable ServerPlayer liveOwner() {
        ServerPlayer owner = this.onlineOwner();
        if (owner == null) {
            return null;
        }
        UUID shell = EchoPossession.shellOf(owner);
        return this.getUUID().equals(shell) ? owner : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.tickCount % 20 != 1) {
            return;
        }
        ServerPlayer owner = this.liveOwner();
        if (owner == null) {
            this.discard();
            return;
        }
        int radius = CommonConfig.ECHO_SHELL_AGGRO_RADIUS.get();
        if (radius > 0) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(radius), mob -> mob instanceof Enemy && mob.getTarget() == null && mob.isAlive())) {
                mob.setTarget(this);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        ServerPlayer owner = this.liveOwner();
        if (owner == null) {
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive()) {
            EchoPossession.onShellHealth(owner, this.getHealth());
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel && !this.isRemoved() && !this.dead) {
            ServerPlayer owner = this.liveOwner();
            this.dead = true;
            if (owner != null) {
                EchoPossession.onShellKilled(owner, source);
            }
            this.discard();
            return;
        }
        super.die(source);
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return false;
    }

    @Override
    public Component getName() {
        if (this.hasCustomName()) {
            return super.getName();
        }
        return Component.translatable("entity.mnemolith.echo_shell.named", this.ownerName());
    }
}
