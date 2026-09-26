package com.mnemolith.entity.echo;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A residual echo: a memory that condensed out of an overloaded chunk and drifts inside it. It cannot be hurt (only
 * /kill and the void remove it); it is dealt with by reading, capturing, feeding or starving it. Rules live in
 * {@link Residues}; this class keeps its state, timers and drift.
 * <p>
 * Synced to clients: temper id, strength, pinned and reading progress (the renderer and the lens label use them).
 * Saved: tag, strength, home origin, fester timer, old flag. A residue never despawns.
 */
public final class ResidueEntity extends Mob {
    private static final EntityDataAccessor<Integer> DATA_TEMPER = SynchedEntityData.defineId(ResidueEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STRENGTH = SynchedEntityData.defineId(ResidueEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PINNED = SynchedEntityData.defineId(ResidueEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_READ = SynchedEntityData.defineId(ResidueEntity.class, EntityDataSerializers.INT);
    private static final double DRIFT_SPEED = 0.035D;

    private ImprintTag tag = ImprintTag.FIRE;
    /** Null until set up (a residue from /summon takes the spot it first ticks at as its home). */
    private @Nullable BlockPos origin;
    private boolean old;
    private int festerTicks;
    private int pinTicks;
    private int lashCooldown;
    private @Nullable Vec3 driftTarget;

    public ResidueEntity(EntityType<? extends ResidueEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.MOVEMENT_SPEED, 0.1D).add(Attributes.FOLLOW_RANGE, 8.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_TEMPER, Temper.KINDLED.id());
        entityData.define(DATA_STRENGTH, 3);
        entityData.define(DATA_PINNED, false);
        entityData.define(DATA_READ, 0);
    }

    /** Called once by {@link Residues#spawn} before the entity is added. */
    public void setup(ImprintTag tag, int strength, BlockPos origin, boolean old) {
        this.tag = Residues.graftable(tag) ? tag : ImprintTag.FIRE;
        this.origin = origin.immutable();
        this.old = old;
        this.entityData.set(DATA_TEMPER, this.temper0().id());
        this.setStrength(strength);
        // The first fester comes a full period after it condensed.
        this.festerTicks = 0;
    }

    // ---- state ----

    public ImprintTag tag() {
        return this.tag;
    }

    /** Synced: correct on both sides. */
    public Temper temper() {
        Temper temper = Temper.byId(this.entityData.get(DATA_TEMPER));
        return temper == null ? Temper.KINDLED : temper;
    }

    public int strength() {
        return this.entityData.get(DATA_STRENGTH);
    }

    public void setStrength(int strength) {
        this.entityData.set(DATA_STRENGTH, Math.max(Residues.MIN_STRENGTH, Math.min(Residues.MAX_STRENGTH, strength)));
    }

    public BlockPos origin() {
        if (this.origin == null) {
            this.origin = this.blockPosition().immutable();
        }
        return this.origin;
    }

    public boolean isOld() {
        return this.old;
    }

    public boolean isPinned() {
        return this.entityData.get(DATA_PINNED);
    }

    public void pin(int ticks) {
        this.pinTicks = ticks;
        this.entityData.set(DATA_PINNED, true);
        this.entityData.set(DATA_READ, 0);
    }

    /** Reading progress in ticks, 0..{@link Residues#READ_TICKS}. Synced for the lens label. */
    public int readProgress() {
        return this.entityData.get(DATA_READ);
    }

    public void lashed() {
        this.lashCooldown = Residues.LASH_COOLDOWN_TICKS;
    }

    public int festerTicks() {
        return this.festerTicks;
    }

    /** QA: move the fester timer so the next server tick festers. */
    public void festerNow() {
        this.festerTicks = Residues.festerTicks();
    }

    // ---- ticking ----

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.setNoGravity(true);
        if (this.level().isClientSide()) {
            if (this.random.nextInt(this.isPinned() ? 2 : 4) == 0) {
                this.level().addParticle(EchoGrafts.particle(this.temper()), this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 0.0D, 0.02D, 0.0D);
            }
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (!Residues.enabled()) {
            return;
        }
        if (this.pinTicks > 0 && --this.pinTicks == 0) {
            this.entityData.set(DATA_PINNED, false);
        }
        if (this.lashCooldown > 0) {
            this.lashCooldown--;
        }
        this.sense(level, level.players());
        if (++this.festerTicks >= Residues.festerTicks()) {
            this.festerTicks = 0;
            Residues.fester(level, this);
        }
    }

    /**
     * The lens reading (every 5 ticks) and the lash (every 10 ticks) against {@code players}. The tick passes the
     * level's players; the QA passes its fake player, which is never in that list.
     */
    public void sense(ServerLevel level, Iterable<? extends ServerPlayer> players) {
        if (this.tickCount % 5 == 0) {
            this.checkReading(level, players);
        }
        if (this.tickCount % 10 == 0 && !this.isPinned() && this.lashCooldown <= 0) {
            this.checkLash(level, players);
        }
    }

    private void checkReading(ServerLevel level, Iterable<? extends ServerPlayer> players) {
        if (this.isPinned()) {
            return;
        }
        ServerPlayer reader = null;
        for (ServerPlayer player : players) {
            if (!player.isSpectator() && Residues.reading(player, this)) {
                reader = player;
                break;
            }
        }
        int progress = this.readProgress();
        if (reader == null) {
            if (progress > 0) {
                this.entityData.set(DATA_READ, Math.max(0, progress - 5));
            }
            return;
        }
        progress += 5;
        if (progress >= Residues.READ_TICKS) {
            Residues.pinned(level, this, reader);
        } else {
            this.entityData.set(DATA_READ, progress);
        }
    }

    private void checkLash(ServerLevel level, Iterable<? extends ServerPlayer> players) {
        double radius = com.mnemolith.config.CommonConfig.RESIDUE_LASH_RADIUS.get();
        if (radius <= 0.0D) {
            return;
        }
        for (ServerPlayer player : players) {
            if (player.level() != level || !Residues.affects(player)) {
                continue;
            }
            double reach = player.isShiftKeyDown() ? radius * 0.5D : radius;
            if (player.distanceToSqr(this.getX(), this.getY() + 0.8D, this.getZ()) <= reach * reach) {
                Residues.lash(level, this, player);
                this.lashed();
                return;
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        double speed = this.isPinned() ? 0.0D : DRIFT_SPEED;
        if (this.driftTarget == null || this.position().distanceToSqr(this.driftTarget) < 0.25D || this.random.nextInt(120) == 0) {
            this.driftTarget = this.pickDriftTarget();
        }
        Vec3 to = this.driftTarget.subtract(this.position());
        double length = to.length();
        Vec3 motion = length < 1.0E-3D ? Vec3.ZERO : to.scale(speed / length);
        this.setDeltaMovement(motion);
        if (motion.horizontalDistanceSqr() > 1.0E-6D) {
            float yaw = (float) (Math.atan2(motion.z, motion.x) * (180.0D / Math.PI)) - 90.0F;
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
        }
    }

    /** A random point in its home chunk, from one below to three above where it condensed. */
    private Vec3 pickDriftTarget() {
        BlockPos home = this.origin();
        int minX = (home.getX() >> 4) << 4;
        int minZ = (home.getZ() >> 4) << 4;
        double x = minX + 1.0D + this.random.nextDouble() * 14.0D;
        double z = minZ + 1.0D + this.random.nextDouble() * 14.0D;
        double y = home.getY() - 1.0D + this.random.nextDouble() * 4.0D;
        return new Vec3(x, y, z);
    }

    @Override
    public void travel(Vec3 input) {
        // Drift is set directly; no friction, no gravity, through blocks.
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
    }

    // ---- interaction ----

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean needle = held.is(ModItems.EXTRACTION_NEEDLE.get());
        if (this.level().isClientSide()) {
            return needle || held.isEmpty() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !Residues.enabled()) {
            return InteractionResult.PASS;
        }
        if (needle) {
            // Either way the attempt happened (a capture, or a slip and a lash), so the click is consumed.
            Residues.capture(serverPlayer, this, held);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (EchoPossession.isPossessing(serverPlayer)) {
                return Residues.absorb(serverPlayer, this) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
            }
            serverPlayer.sendSystemMessage(Component.translatable(this.isPinned() ? "mnemolith.residue.hint_pinned" : "mnemolith.residue.hint",
                    Component.translatable(this.tag.translationKey()), this.strength()), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    // ---- invulnerable, not pushed, never despawns ----

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return this.isPinned() || super.isCurrentlyGlowing();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // ---- save ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("residue_tag", ImprintTag.CODEC, this.tag);
        output.putInt("residue_strength", this.strength());
        output.store("residue_origin", BlockPos.CODEC, this.origin());
        output.putBoolean("residue_old", this.old);
        output.putInt("residue_fester", this.festerTicks);
        output.putInt("residue_pin", this.pinTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.tag = input.read("residue_tag", ImprintTag.CODEC).filter(Residues::graftable).orElse(ImprintTag.FIRE);
        this.entityData.set(DATA_TEMPER, this.temper0().id());
        this.setStrength(input.getIntOr("residue_strength", 3));
        this.origin = input.read("residue_origin", BlockPos.CODEC).orElse(null);
        this.old = input.getBooleanOr("residue_old", false);
        this.festerTicks = Math.max(0, input.getIntOr("residue_fester", 0));
        this.pinTicks = Math.max(0, input.getIntOr("residue_pin", 0));
        this.entityData.set(DATA_PINNED, this.pinTicks > 0);
        Mnemolith.LOGGER.debug("Mnemolith residue loaded tag={} strength={}", this.tag.getSerializedName(), this.strength());
    }

    private Temper temper0() {
        Temper temper = Temper.of(this.tag);
        return temper == null ? Temper.KINDLED : temper;
    }
}
