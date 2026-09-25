package com.mnemolith.entity.echo;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A humanoid that wears its owner's skin. The profile is synced so the client can resolve the skin;
 * the owner id is also kept server-side so ownership never depends on a profile lookup.
 */
public abstract class MemoryAvatar extends Avatar {
    /** QA only: fake players that count as "online owners" while {@code /mnemolith echoqa} runs. Always empty otherwise. */
    public static final java.util.Map<java.util.UUID, ServerPlayer> STAND_INS = new java.util.HashMap<>();
    protected static final EntityDataAccessor<ResolvableProfile> DATA_PROFILE = SynchedEntityData.defineId(MemoryAvatar.class, EntityDataSerializers.RESOLVABLE_PROFILE);
    private static final byte ALL_LAYERS = 0x7F;

    private @Nullable UUID ownerId;
    private String ownerName = "";

    protected MemoryAvatar(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, ALL_LAYERS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PROFILE, ResolvableProfile.Static.EMPTY);
    }

    @Override
    public ResolvableProfile getProfile() {
        return this.entityData.get(DATA_PROFILE);
    }

    public void setOwner(ServerPlayer player) {
        this.setOwner(player.getUUID(), player.getGameProfile().name(), ResolvableProfile.createResolved(player.getGameProfile()));
    }

    public void setOwner(UUID id, String name, ResolvableProfile profile) {
        this.ownerId = id;
        this.ownerName = name == null ? "" : name;
        this.entityData.set(DATA_PROFILE, profile);
    }

    /** Server: the stored owner. Client: the id inside the synced profile. */
    public @Nullable UUID ownerId() {
        if (this.ownerId != null) {
            return this.ownerId;
        }
        GameProfile profile = this.getProfile().partialProfile();
        return profile == null ? null : profile.id();
    }

    public String ownerName() {
        if (!this.ownerName.isEmpty()) {
            return this.ownerName;
        }
        return this.getProfile().name().orElse("?");
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        UUID id = this.ownerId();
        return player != null && player.equals(id);
    }

    public boolean isOwnedBy(@Nullable Player player) {
        UUID id = this.ownerId();
        return player != null && id != null && id.equals(player.getUUID());
    }

    public @Nullable ServerPlayer onlineOwner() {
        if (!(this.level() instanceof ServerLevel level) || this.ownerId == null) {
            return null;
        }
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(this.ownerId);
        return online != null ? online : STAND_INS.get(this.ownerId);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.ownerId != null) {
            output.store("owner", UUIDUtil.CODEC, this.ownerId);
        }
        output.putString("owner_name", this.ownerName);
        output.store("profile", ResolvableProfile.CODEC, this.getProfile());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        Optional<UUID> owner = input.read("owner", UUIDUtil.CODEC);
        this.ownerId = owner.orElse(null);
        this.ownerName = input.getStringOr("owner_name", "");
        input.read("profile", ResolvableProfile.CODEC).ifPresent(profile -> this.entityData.set(DATA_PROFILE, profile));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return true;
    }
}
