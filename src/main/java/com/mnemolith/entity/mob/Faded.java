package com.mnemolith.entity.mob;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.item.ChronicleLensItem;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.worldgen.hollows.HollowFlickers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A pale figure of Memory Hollows that copies the flickers around it. Neutral: it walks to a flicker shown within
 * {@link #MIMIC_RANGE} blocks and replays its scene in place; it turns on whoever hits it or catches a flicker near it.
 * Unseen it shrugs off half of a player's damage; a raised lens looking at it reveals it (glowing) and then it takes
 * half again. Drops recollite now and then, and sometimes a slip of the memory it last copied.
 */
public class Faded extends Monster {
    public static final double MIMIC_RANGE = 16.0D;
    public static final double REVEAL_RANGE = 12.0D;
    public static final int REVEAL_TICKS = 100;
    public static final int REPLAY_TICKS = 60;
    public static final int ANGER_TICKS = 300;
    public static final float UNSEEN_DAMAGE = 0.5F;
    public static final float REVEALED_DAMAGE = 1.5F;
    public static final float SLIP_DROP_CHANCE = 0.5F;
    public static final Identifier REVEALED_ADVANCEMENT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "faded_revealed");

    /** Scene being replayed ({@link HollowFlickers#WALK} etc.), -1 when idle. */
    private static final EntityDataAccessor<Integer> DATA_SCENE = SynchedEntityData.defineId(Faded.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_REVEALED = SynchedEntityData.defineId(Faded.class, EntityDataSerializers.BOOLEAN);

    private @Nullable ImprintTag copied;
    private @Nullable BlockPos mimicAt;
    private int replayTicks;
    private long revealedUntil;
    private int angerTicks;

    public Faded(EntityType<? extends Faded> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SCENE, -1);
        builder.define(DATA_REVEALED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15D, false));
        this.goalSelector.addGoal(2, new MimicGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public int scene() {
        return this.entityData.get(DATA_SCENE);
    }

    public boolean revealed() {
        return this.entityData.get(DATA_REVEALED);
    }

    public @Nullable ImprintTag copied() {
        return this.copied;
    }

    public @Nullable BlockPos mimicAt() {
        return this.mimicAt;
    }

    // ---------------------------------------------------------------- hooks from HollowFlickers

    /** A flicker was shown at {@code pos}: idle faded in range walk over to copy it. Server side. */
    public static void onFlicker(ServerLevel level, BlockPos pos, @Nullable ImprintTag tag) {
        for (Faded faded : level.getEntitiesOfClass(Faded.class, new AABB(pos).inflate(MIMIC_RANGE))) {
            if (faded.getTarget() == null) {
                faded.copy(pos, tag);
            }
        }
    }

    /** A player caught a flicker at {@code pos}: faded in range take it as theft. */
    public static void onCatch(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        for (Faded faded : level.getEntitiesOfClass(Faded.class, new AABB(pos).inflate(MIMIC_RANGE))) {
            faded.provoke(player);
        }
    }

    public void copy(BlockPos pos, @Nullable ImprintTag tag) {
        if (tag != null) {
            this.copied = tag;
        }
        this.mimicAt = pos.immutable();
        this.replayTicks = 0;
    }

    public void provoke(LivingEntity target) {
        this.setTarget(target);
        this.angerTicks = ANGER_TICKS;
        this.mimicAt = null;
        this.setScene(-1);
    }

    private void setScene(int scene) {
        if (this.scene() != scene) {
            this.entityData.set(DATA_SCENE, scene);
        }
    }

    // ---------------------------------------------------------------- lens

    /** Reveals the faded for {@link #REVEAL_TICKS}: glowing, and player damage is raised instead of halved. */
    public void reveal(@Nullable ServerPlayer by) {
        long now = this.level().getGameTime();
        boolean fresh = now >= this.revealedUntil;
        this.revealedUntil = now + REVEAL_TICKS;
        this.entityData.set(DATA_REVEALED, true);
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, REVEAL_TICKS, 0, false, false));
        if (by != null && fresh && this.level() instanceof ServerLevel level) {
            var advancement = level.getServer().getAdvancements().get(REVEALED_ADVANCEMENT);
            if (advancement != null) {
                by.getAdvancements().award(advancement, "revealed");
            }
        }
    }

    /** Whether {@code player} holds a raised lens and looks at this faded within reach and sight. */
    public boolean seenThroughLens(Player player) {
        if (!ChronicleLensItem.isFocusing(player) || player.distanceToSqr(this) > REVEAL_RANGE * REVEAL_RANGE) {
            return false;
        }
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 to = this.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        return look.dot(to) > 0.95D && player.hasLineOfSight(this);
    }

    /** Damage multiplier for a hit from a player. */
    public float playerDamageScale() {
        return this.revealed() ? REVEALED_DAMAGE : UNSEEN_DAMAGE;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player) {
            amount *= this.playerDamageScale();
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            this.provoke(attacker);
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            // memory drag: the hit slows you for a moment
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), this);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (this.revealed() && now >= this.revealedUntil) {
            this.entityData.set(DATA_REVEALED, false);
        }
        if (this.tickCount % 5 == 0) {
            for (Player player : level.players()) {
                if (player instanceof ServerPlayer server && !player.isSpectator() && this.seenThroughLens(player)) {
                    this.reveal(server);
                    break;
                }
            }
        }
        if (this.angerTicks > 0) {
            this.angerTicks--;
        } else if (this.getTarget() != null && this.tickCount % 20 == 0) {
            this.setTarget(null);
        }
        if (this.replayTicks > 0 && --this.replayTicks == 0) {
            this.setScene(-1);
            this.mimicAt = null;
        }
    }

    /** Called by the mimic goal when the faded stands at the flicker's spot. */
    void startReplay() {
        this.replayTicks = REPLAY_TICKS;
        this.setScene(HollowFlickers.scene(this.copied));
        this.getNavigation().stop();
    }

    boolean replaying() {
        return this.replayTicks > 0;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (killedByPlayer && this.copied != null && this.random.nextFloat() < SLIP_DROP_CHANCE) {
            this.spawnAtLocation(level, ImprintSlips.of(this.copied, this.blockPosition()));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.FADED_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.FADED_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FADED_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("faded_copied", this.copied == null ? -1 : this.copied.ordinal());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int copied = input.getIntOr("faded_copied", -1);
        this.copied = copied >= 0 && copied < ImprintTag.values().length ? ImprintTag.values()[copied] : null;
    }

    /** Walk to the last flicker's spot, then stand and replay its scene. */
    static final class MimicGoal extends Goal {
        private final Faded faded;
        private int repath;

        MimicGoal(Faded faded) {
            this.faded = faded;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.faded.mimicAt != null && this.faded.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.repath = 0;
        }

        @Override
        public void stop() {
            this.faded.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos at = this.faded.mimicAt;
            if (at == null || this.faded.replaying()) {
                return;
            }
            double d = this.faded.distanceToSqr(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
            if (d < 2.5D) {
                this.faded.startReplay();
                return;
            }
            if (--this.repath <= 0) {
                this.repath = 20;
                if (!this.faded.getNavigation().moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 1.0D)) {
                    this.faded.mimicAt = null;
                }
            }
        }
    }
}
