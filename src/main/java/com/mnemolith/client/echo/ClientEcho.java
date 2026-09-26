package com.mnemolith.client.echo;

import org.jspecify.annotations.Nullable;

import com.mnemolith.entity.echo.EchoEntity;

import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;

/** Client-side echo: carries the avatar state and the owner's skin for {@link EchoRenderer}. */
public class ClientEcho extends EchoEntity implements ClientAvatarEntity {
    private final ClientAvatarState avatarState = new ClientAvatarState();
    private final AvatarSkin skin = new AvatarSkin();

    public ClientEcho(EntityType<? extends EchoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        this.avatarState.tick(this.position(), this.getDeltaMovement());
        this.skin.tick();
        this.graftMotes();
    }

    /** A grafted echo sheds a mote of its temper's color now and then (client only, follows particle density). */
    private void graftMotes() {
        com.mnemolith.echo.graft.Temper temper = this.graftTemper();
        if (temper == null || this.isInvisible()) {
            return;
        }
        double density = com.mnemolith.client.config.ClientConfig.PARTICLE_DENSITY.get();
        if (!com.mnemolith.client.config.ClientConfig.IMPRINT_PARTICLES.get() || density <= 0.0D || this.random.nextDouble() > 0.12D * density) {
            return;
        }
        double x = this.getX() + (this.random.nextDouble() - 0.5D) * 0.7D;
        double y = this.getY() + 0.3D + this.random.nextDouble() * 1.5D;
        double z = this.getZ() + (this.random.nextDouble() - 0.5D) * 0.7D;
        double rise = temper == com.mnemolith.echo.graft.Temper.PLUNGING ? -0.03D : 0.025D;
        this.level().addParticle(com.mnemolith.echo.graft.EchoGrafts.particle(temper), x, y, z, 0.0D, rise, 0.0D);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor.equals(DATA_PROFILE)) {
            this.skin.request(this.getProfile());
        }
    }

    @Override
    public ClientAvatarState avatarState() {
        return this.avatarState;
    }

    @Override
    public PlayerSkin getSkin() {
        return this.skin.skin();
    }

    @Override
    public Parrot.@Nullable Variant getParrotVariantOnShoulder(boolean left) {
        return null;
    }

    @Override
    public boolean showExtraEars() {
        return false;
    }
}
