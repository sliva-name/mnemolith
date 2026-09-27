package com.mnemolith.entity;

import com.mnemolith.audio.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A turquoise plastic chair. It does not walk. It bobs and leans toward whoever is close, and the meme
 * voice plays once when someone comes near or sits, then waits out the clip.
 */
public class PleadingChair extends Entity {
    /** Hips land on the baked seat (about 0.57). The sitting pose keeps the legs forward of the feet. */
    public static final float SEAT = -0.18F;
    public static final double APPROACH_RANGE = 5.0D;
    /** Long enough that the ~19s clip is not restarted over itself. */
    public static final int VOICE_COOLDOWN = 500;

    private int voiceCooldown;
    private boolean playerNear;

    public PleadingChair(EntityType<? extends PleadingChair> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public static PleadingChair summon(ServerLevel level, BlockPos pos) {
        PleadingChair chair = ModEntities.PLEADING_CHAIR.get().create(level, EntitySpawnReason.COMMAND);
        if (chair == null) {
            return null;
        }
        chair.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        chair.yRotO = 0.0F;
        level.addFreshEntity(chair);
        return chair;
    }

    /** True when a player has just stepped into range and the clip is not already playing. */
    public static boolean heard(boolean wasNear, boolean near, int cooldown) {
        return near && !wasNear && cooldown <= 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        if (this.voiceCooldown > 0) {
            this.voiceCooldown--;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        boolean near = false;
        double range = APPROACH_RANGE * APPROACH_RANGE;
        for (Player player : level.players()) {
            if (player.distanceToSqr(this) <= range) {
                near = true;
                break;
            }
        }
        if (heard(this.playerNear, near, this.voiceCooldown)) {
            this.playVoice(level);
        }
        this.playerNear = near;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!this.isVehicle()) {
            player.startRiding(this);
        }
        if (this.level() instanceof ServerLevel level) {
            this.tryVoice(level);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Plays the clip if the cooldown has elapsed. Returns whether it started. */
    public boolean tryVoice(ServerLevel level) {
        if (this.voiceCooldown > 0) {
            return false;
        }
        this.playVoice(level);
        return true;
    }

    private void playVoice(ServerLevel level) {
        this.voiceCooldown = VOICE_COOLDOWN;
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CHAIR_VOICE.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float scale) {
        return new Vec3(0.0D, SEAT, 0.0D);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(this.getX() + 0.9D, this.getY(), this.getZ());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!(source.getEntity() instanceof Player)) {
            return false;
        }
        this.discard();
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("VoiceCooldown", this.voiceCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.voiceCooldown = Mth.clamp(input.getIntOr("VoiceCooldown", 0), 0, VOICE_COOLDOWN);
    }
}
