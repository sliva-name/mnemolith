package com.mnemolith.client.echo;

import org.jspecify.annotations.Nullable;

import com.mnemolith.entity.echo.PastSelf;

import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;

/** Client-side past self: the avatar state and the owner's skin for {@link EchoRenderer} (translucent, fading in and out). */
public class ClientPastSelf extends PastSelf implements ClientAvatarEntity {
    private final ClientAvatarState avatarState = new ClientAvatarState();
    private final AvatarSkin skin = new AvatarSkin();
    private boolean wasSleeping;

    public ClientPastSelf(EntityType<? extends PastSelf> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        this.avatarState.tick(this.position(), this.getDeltaMovement());
        this.skin.tick();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor.equals(DATA_PROFILE)) {
            this.skin.request(this.getProfile());
        }
        // Lying down: vanilla snaps the body onto the bed when the sleeping position arrives, right after the move
        // packet started interpolating toward the same spot. The interpolation then adds that snap a second time and
        // the body floats a block beside and above the bed until the next forced position sync. Stop it here.
        boolean sleeping = this.isSleeping();
        if (sleeping && !this.wasSleeping) {
            this.getInterpolation().cancel();
            this.setOldPosAndRot();
        }
        this.wasSleeping = sleeping;
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
