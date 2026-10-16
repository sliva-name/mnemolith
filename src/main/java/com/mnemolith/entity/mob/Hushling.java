package com.mnemolith.entity.mob;

import java.util.List;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * The hushling, the singer of the Pale Chorus: a small pale figure of the End that thins memory. Every
 * {@link #THIN_PERIOD} ticks it removes the quietest imprint of its chunk; its hit hushes a player ({@link Hush}) for
 * {@link #HUSH_TICKS}. Sound breaks the hush: a note block played within {@link #NOTE_RANGE} shatters it for
 * {@link #SHATTER_TICKS} (no thinning, no hushing, glowing, double damage). Dying in the End it writes a silence
 * imprint; killed by a player it drops hush filament. See {@code docs/design/pale-chorus.md}.
 */
public class Hushling extends Monster {
    public static final int THIN_PERIOD = 200;
    public static final int HUSH_TICKS = 120;
    public static final int SHATTER_TICKS = 200;
    public static final double NOTE_RANGE = 12.0D;

    private int thinTicks = THIN_PERIOD;
    private long shatteredUntil;

    public Hushling(EntityType<? extends Hushling> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean shattered() {
        return this.level().getGameTime() < this.shatteredUntil;
    }

    /** Sound broke the hush: for {@link #SHATTER_TICKS} it neither thins nor hushes, glows and takes double damage. */
    public void shatter() {
        this.shatteredUntil = this.level().getGameTime() + SHATTER_TICKS;
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, SHATTER_TICKS, 0, false, false));
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 1.4D, this.getZ(), 6, 0.4D, 0.3D, 0.4D, 1.0D);
        }
    }

    /** A note block played at {@code pos}: shatters every hushling within {@link #NOTE_RANGE}. Returns how many. */
    public static int onNote(ServerLevel level, BlockPos pos) {
        int n = 0;
        for (Hushling hushling : level.getEntitiesOfClass(Hushling.class, new AABB(pos).inflate(NOTE_RANGE))) {
            if (hushling.isAlive() && hushling.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= NOTE_RANGE * NOTE_RANGE) {
                hushling.shatter();
                n++;
            }
        }
        return n;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (--this.thinTicks <= 0) {
            this.thinTicks = THIN_PERIOD;
            this.thin(level);
        }
    }

    /** Removes the quietest imprint of its chunk. "thinned", "empty" or "shattered"; the QA reads it. */
    public String thin(ServerLevel level) {
        if (this.shattered()) {
            return "shattered";
        }
        LevelChunk chunk = level.getChunkAt(this.blockPosition());
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || memory.removeLowest().isEmpty()) {
            return "empty";
        }
        MemoryPressure.recompute(chunk, memory);
        level.sendParticles(ParticleTypes.WHITE_ASH, this.getX(), this.getY() + 1.0D, this.getZ(), 10, 0.5D, 0.5D, 0.5D, 0.01D);
        return "thinned";
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return super.hurtServer(level, source, this.shattered() ? amount * 2.0F : amount);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof ServerPlayer player) {
            hushPlayer(level, player);
        }
        return hit;
    }

    /** The silence-flavoured hit. False when shattered, or for creative and spectator players. */
    public boolean hushPlayer(ServerLevel level, ServerPlayer player) {
        if (this.shattered() || player.isCreative() || player.isSpectator()) {
            return false;
        }
        Hush.hush(level, player.getUUID(), HUSH_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HUSH_TICKS, 0));
        player.sendOverlayMessage(Component.translatable("mnemolith.hushling.hushed"));
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 0.6F, 0.5F);
        return true;
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = this.dead || this.isRemoved();
        super.die(source);
        if (!wasDead && this.level() instanceof ServerLevel level && level.dimension() == Level.END) {
            ImprintWriter.write(level, this.blockPosition(), List.of(ImprintTag.SILENCE), null, false);
        }
    }

    @Override
    public float getVoicePitch() {
        return 1.6F + (this.random.nextFloat() - 0.5F) * 0.15F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.FADED_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }
}
