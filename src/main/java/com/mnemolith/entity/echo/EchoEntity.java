package com.mnemolith.entity.echo;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.menu.EchoMenu;
import com.mnemolith.echo.EchoAction;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.EchoView;
import com.mnemolith.echo.SlotStack;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.network.EchoGhostPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * An echo: replays its owner's recording, then stays as an idle helper body.
 * It owns a real, player-shaped inventory that starts empty. World edits go through {@link EchoHands}.
 */
public class EchoEntity extends MemoryAvatar {
    private static final EntityDataAccessor<Boolean> DATA_REPLAYING = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.BOOLEAN);
    // Job display state for the owner's screen and the lens label (stage 2).
    private static final EntityDataAccessor<Byte> DATA_JOB_MODE = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Component> DATA_JOB_STATUS = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Integer> DATA_JOB_RADIUS = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_JOB_CHEST = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_JOB_ANCHOR = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Byte> DATA_LESSON = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.BYTE);
    /** Stage 3: memory band of the chunk the echo works in (ordinal of PressureBand). */
    private static final EntityDataAccessor<Byte> DATA_STRAIN = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.BYTE);
    public static final int LESSON_MINING = 1;
    public static final int LESSON_BUILDING = 2;
    /** Stage 3: the echo knows farming; {@link #DATA_FARM} names the crops. */
    public static final int LESSON_FARMING = 4;
    private static final EntityDataAccessor<Component> DATA_FARM = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.COMPONENT);
    private static final int JOB_STOPPED = 0x40;
    /** Set by the physical client so client-side echoes carry a skin. Null on a dedicated server. */
    public static EntityType.@Nullable EntityFactory<EchoEntity> clientFactory;

    private final NonNullList<ItemStack> main = NonNullList.withSize(EchoInventory.MAIN, ItemStack.EMPTY);
    private final EchoInventory inventory = new EchoInventory(this);
    private int selected;
    private @Nullable EchoRecording recording;
    private int replayTick = -1;
    private int nextAction;
    private long generation;
    /** True once the body was handed to its owner, or found to be a stale copy. Suppresses drops and registry removal. */
    private boolean silentRemoval;
    private final EchoJob job = new EchoJob();
    /** Where the job wants the body to walk this tick; null when standing. Server only. */
    private @Nullable Vec3 moveTarget;
    /** Stage 3: extra max health from the owner's sturdy body upgrades (saved). */
    private double bonusHealth;

    protected EchoEntity(EntityType<? extends EchoEntity> type, Level level) {
        super(type, level);
    }

    public static EchoEntity create(EntityType<EchoEntity> type, Level level) {
        if (level.isClientSide() && clientFactory != null) {
            return clientFactory.create(type, level);
        }
        return new EchoEntity(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.1D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_REPLAYING, false);
        entityData.define(DATA_JOB_MODE, (byte) 0);
        entityData.define(DATA_JOB_STATUS, Component.empty());
        entityData.define(DATA_JOB_RADIUS, 16);
        entityData.define(DATA_JOB_CHEST, Optional.empty());
        entityData.define(DATA_JOB_ANCHOR, Optional.empty());
        entityData.define(DATA_LESSON, (byte) 0);
        entityData.define(DATA_STRAIN, (byte) 0);
        entityData.define(DATA_FARM, Component.empty());
    }

    // ---- inventory ----

    public NonNullList<ItemStack> mainItems() {
        return this.main;
    }

    public EchoInventory inventory() {
        return this.inventory;
    }

    public int selectedSlot() {
        return this.selected;
    }

    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < 9) {
            ItemStack before = this.getItemBySlot(EquipmentSlot.MAINHAND);
            this.selected = slot;
            this.onEquipItem(EquipmentSlot.MAINHAND, before, this.getItemBySlot(EquipmentSlot.MAINHAND));
        }
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            return this.main.get(this.selected);
        }
        return super.getItemBySlot(slot);
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack itemStack, boolean insideTransaction) {
        if (slot == EquipmentSlot.MAINHAND) {
            ItemStack old = this.main.set(this.selected, itemStack);
            if (!insideTransaction) {
                this.onEquipItem(slot, old, itemStack);
            }
            return;
        }
        super.setItemSlot(slot, itemStack, insideTransaction);
    }

    // ---- recording and replay ----

    public @Nullable EchoRecording recording() {
        return this.recording;
    }

    public long generation() {
        return this.generation;
    }

    public void setGeneration(long generation) {
        this.generation = generation;
    }

    public boolean isReplaying() {
        return this.level().isClientSide() ? this.entityData.get(DATA_REPLAYING) : this.replayTick >= 0;
    }

    public int replayTick() {
        return this.replayTick;
    }

    /** Stores {@code recording}, snaps to its first frame, and replays it from the start. */
    public void startReplay(EchoRecording recording) {
        this.recording = recording;
        EchoRecording.Frame first = recording.frame(0);
        this.snapTo(first.x(), first.y(), first.z(), first.yRot(), first.xRot());
        this.setYHeadRot(first.headRot());
        this.setYBodyRot(first.yRot());
        this.replayTick = 0;
        this.nextAction = 0;
        this.moveTarget = null;
        this.job.release(this);
        this.job.beginReplay();
        this.beginReplayPhysics();
        this.syncJob();
    }

    /** Keeps {@code recording} as this body's lesson without replaying it (used when a possessed body is released). */
    public void keepRecording(EchoRecording recording) {
        this.recording = recording;
        this.stopReplay();
    }

    public void stopReplayIfRunning() {
        if (this.replayTick >= 0) {
            this.stopReplay();
        }
    }

    public void stopReplay() {
        this.replayTick = -1;
        this.nextAction = 0;
        this.noPhysics = false;
        this.setNoGravity(false);
        this.setShiftKeyDown(false);
        this.setSprinting(false);
        this.setPose(Pose.STANDING);
        this.entityData.set(DATA_REPLAYING, false);
    }

    private void beginReplayPhysics() {
        this.noPhysics = true;
        this.setNoGravity(true);
        this.entityData.set(DATA_REPLAYING, true);
    }

    /** One frame of the replay. Public so the QA command can drive a replay without waiting on ticks. */
    public void stepReplay(ServerLevel level) {
        EchoRecording rec = this.recording;
        if (rec == null || this.replayTick < 0) {
            return;
        }
        if (!level.dimension().equals(rec.dimension()) || this.replayTick >= rec.length()) {
            this.stopReplay();
            return;
        }
        EchoRecording.Frame frame = rec.frame(this.replayTick);
        this.setPos(frame.x(), frame.y(), frame.z());
        this.setYRot(frame.yRot());
        this.setXRot(frame.xRot());
        this.setYHeadRot(frame.headRot());
        boolean sneak = frame.has(EchoRecording.FLAG_SNEAK);
        this.setShiftKeyDown(sneak);
        this.setPose(sneak ? Pose.CROUCHING : Pose.STANDING);
        this.setSprinting(frame.has(EchoRecording.FLAG_SPRINT));
        this.setOnGround(frame.has(EchoRecording.FLAG_GROUND));
        this.setDeltaMovement(Vec3.ZERO);
        this.resetFallDistance();
        if (frame.has(EchoRecording.FLAG_SWING)) {
            this.swing(frame.has(EchoRecording.FLAG_SWING_OFFHAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        }
        List<EchoAction> actions = rec.actions();
        while (this.nextAction < actions.size() && actions.get(this.nextAction).tick() <= this.replayTick) {
            EchoAction action = actions.get(this.nextAction++);
            if (action.tick() == this.replayTick) {
                EchoHands.perform(level, this, action, frame);
            }
        }
        this.replayTick++;
        if (this.replayTick >= rec.length()) {
            this.stopReplay();
            Mnemolith.LOGGER.info("Mnemolith echo replay done owner={} at {}", this.ownerName(), this.blockPosition().toShortString());
        }
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel level) {
            if ((this.tickCount % 40 == 1) && !EchoRegistry.get(level.getServer()).isCurrent(this.ownerId(), this.getUUID(), this.generation)) {
                Mnemolith.LOGGER.warn("Mnemolith echo stale copy removed id={} owner={} generation={}", this.getUUID(), this.ownerName(), this.generation);
                this.discardSilently();
                return;
            }
            this.stepReplay(level);
            if (!this.isReplaying() && this.isAlive()) {
                this.job.tick(level, this);
            }
            if (this.job.consumeDirty()) {
                this.syncJob();
            }
            if (this.tickCount % 100 == 11 && this.ownerId() != null && level.getServer().getPlayerList().getPlayer(this.ownerId()) instanceof ServerPlayer owner) {
                // Keeps the body in step with the owner's sturdy upgrades (also for echoes that were unloaded).
                double bonus = com.mnemolith.echo.EchoProgress.bonusHealth(owner);
                if (bonus != this.bonusHealth) {
                    this.applyBonusHealth(bonus);
                }
            }
            if (this.tickCount % 40 == 7 && !this.attractsMobs() && !this.job.alarmed()) {
                this.releaseHunters(level);
            }
            if (!this.isReplaying() && this.tickCount % 100 == 0 && this.getHealth() < this.getMaxHealth() && this.isAlive()) {
                this.heal(1.0F);
            }
        }
        super.tick();
    }

    // ---- threats (stage 3) ----

    /** Whether hostile mobs may pick this echo as a target: only while it works in the world. */
    public boolean attractsMobs() {
        return this.isAlive() && !this.isReplaying() && this.job.isWorking() && CommonConfig.ECHO_MOB_AGGRO.get();
    }

    /** Mobs that still hunt an echo that no longer works lose interest. */
    private void releaseHunters(ServerLevel level) {
        for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, this.getBoundingBox().inflate(24.0D),
                mob -> mob.getTarget() == this && mob instanceof net.minecraft.world.entity.monster.Enemy)) {
            mob.setTarget(null);
        }
    }

    // ---- jobs (stage 2) ----

    public EchoJob job() {
        return this.job;
    }

    /** The echo learns {@code lesson}; an old blueprint ghost disappears for the owner. */
    public void teachLesson(EchoLesson lesson) {
        this.job.setLesson(lesson);
        this.sendGhostToOwner();
        this.syncJob();
    }

    /** Restores a job saved while the body was possessed. Lesson, chest and blueprint stay; the echo waits idle. */
    public void restoreJobIdle(EchoJob.Saved saved) {
        this.job.load(saved);
        this.job.stop(this);
        this.syncJob();
    }

    public void setMoveTarget(@Nullable Vec3 target) {
        this.moveTarget = target;
    }

    public @Nullable Vec3 moveTarget() {
        return this.moveTarget;
    }

    /** Turns head and body towards {@code point} (used when digging or placing). */
    public void lookAt(Vec3 point) {
        Vec3 eye = this.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
        this.setYRot(yaw);
        this.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    /** The building job finished: the owner's ghost is cleared. */
    public void onBuildFinished() {
        this.sendGhostToOwner();
    }

    /** Sends the owner the current blueprint ghost (or a clear when nothing is being built). */
    public void sendGhostToOwner() {
        if (this.level() instanceof ServerLevel level && this.ownerId() != null
                && level.getServer().getPlayerList().getPlayer(this.ownerId()) instanceof ServerPlayer owner) {
            PacketDistributor.sendToPlayer(owner, this.ghostPayload());
        }
    }

    private EchoGhostPayload ghostPayload() {
        BlockPos anchor = this.job.buildAnchor();
        if (anchor == null || this.job.mode() != EchoJob.Mode.BUILD || this.job.lesson().blueprint().isEmpty()) {
            return EchoGhostPayload.clear(this.getId());
        }
        return new EchoGhostPayload(this.getId(), anchor, this.job.rotation(), this.job.lesson().blueprint());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (this.isOwnedBy(player) && this.job.mode() == EchoJob.Mode.BUILD) {
            PacketDistributor.sendToPlayer(player, this.ghostPayload());
        }
    }

    /** Pushes the job display state to clients now (stage 3 lens orders). */
    public void syncJobNow() {
        this.job.consumeDirty();
        this.syncJob();
    }

    private void syncJob() {
        var shown = this.job.shownStatus();
        this.entityData.set(DATA_JOB_MODE, (byte) (this.job.mode().ordinal() | (shown.kind().isStop() ? JOB_STOPPED : 0)));
        this.entityData.set(DATA_JOB_STATUS, shown.component());
        this.entityData.set(DATA_JOB_RADIUS, this.job.radius());
        this.entityData.set(DATA_JOB_CHEST, Optional.ofNullable(this.job.chest()));
        this.entityData.set(DATA_JOB_ANCHOR, Optional.ofNullable(this.job.buildAnchor()));
        this.entityData.set(DATA_STRAIN, (byte) this.job.strain().ordinal());
        EchoLesson lesson = this.job.lesson();
        boolean farming = this.job.farmLesson().teaches();
        this.entityData.set(DATA_LESSON, (byte) ((lesson.teachesMining() ? LESSON_MINING : 0) | (lesson.teachesBuilding() ? LESSON_BUILDING : 0) | (farming ? LESSON_FARMING : 0)));
        this.entityData.set(DATA_FARM, farming ? this.job.farmLesson().cropNames() : Component.empty());
    }

    /** Synced job mode (client and server). */
    public EchoJob.Mode jobMode() {
        return EchoJob.Mode.byId(this.entityData.get(DATA_JOB_MODE) & 0x0F);
    }

    /** True when the last job ended with a stop reason (shown in a warmer color). */
    public boolean jobStopped() {
        return (this.entityData.get(DATA_JOB_MODE) & JOB_STOPPED) != 0;
    }

    /** Synced, already translated-on-display job status. */
    public Component jobStatus() {
        return this.entityData.get(DATA_JOB_STATUS);
    }

    public int jobRadius() {
        return this.entityData.get(DATA_JOB_RADIUS);
    }

    public Optional<BlockPos> jobChest() {
        return this.entityData.get(DATA_JOB_CHEST);
    }

    public Optional<BlockPos> jobAnchor() {
        return this.entityData.get(DATA_JOB_ANCHOR);
    }

    /** Synced memory band of the chunk the echo works in; CALM when it does not work. */
    public com.mnemolith.pressure.PressureBand strain() {
        return com.mnemolith.pressure.PressureBand.byOrdinal(this.entityData.get(DATA_STRAIN));
    }

    /** Synced crop names of the farming lesson ("Wheat, Carrots"); empty without one. */
    public Component farmCrops() {
        return this.entityData.get(DATA_FARM);
    }

    /** Stage 3: an echo never tramples farmland, also when it jumps onto it. */
    @Override
    public boolean canTrample(ServerLevel level, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, double fallDistance) {
        return false;
    }

    public int lessonFlags() {
        return this.entityData.get(DATA_LESSON);
    }

    /** Walks towards {@link #moveTarget} with normal physics and collisions. Replay keeps its exact frames instead. */
    @Override
    protected void serverAiStep() {
        super.serverAiStep();
        Vec3 target = this.moveTarget;
        if (this.isReplaying() || target == null) {
            this.zza = 0.0F;
            this.xxa = 0.0F;
            this.setJumping(false);
            return;
        }
        double dx = target.x - this.getX();
        double dz = target.z - this.getZ();
        double dy = target.y - this.getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > 0.05D) {
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float turned = Mth.approachDegrees(this.getYRot(), yaw, 40.0F);
            this.setYRot(turned);
            this.setYBodyRot(turned);
            this.setYHeadRot(turned);
            this.setXRot(0.0F);
        }
        this.setSpeed((float) (this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.3D));
        this.xxa = 0.0F;
        this.zza = horizontal > 0.05D ? (float) Math.min(1.0D, horizontal * 3.0D) : 0.0F;
        // Stage 3: climb a ladder or vine and wade out of water by "jumping" (vanilla climbing and swimming).
        boolean climbOrSwim = dy > 0.3D && (this.onClimbable() || this.isInWater());
        this.setJumping(climbOrSwim || dy > 0.5D && this.onGround() && (this.horizontalCollision || horizontal < 1.3D));
    }

    /** Removes this body without drops and without touching the registry. */
    public void discardSilently() {
        this.silentRemoval = true;
        this.discard();
    }

    // ---- interaction ----

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return this.isOwnedBy(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!this.isOwnedBy(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.translatable("mnemolith.echo.not_yours", this.ownerName()), true);
            return InteractionResult.FAIL;
        }
        ItemStack held = serverPlayer.getItemInHand(hand);
        if (!serverPlayer.isShiftKeyDown() && held.is(ModItems.ECHO_RECORDING.get())) {
            return EchoLife.teach(serverPlayer, this, held) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
        }
        if (serverPlayer.isShiftKeyDown()) {
            this.openInventory(serverPlayer);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (this.job.hasWorkMode() || this.job.order() != com.mnemolith.echo.job.EchoJob.Order.NONE) {
            serverPlayer.sendSystemMessage(this.job.shownStatus().component(), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        serverPlayer.sendSystemMessage(Component.translatable(
                this.isReplaying() ? "mnemolith.echo.status_replaying" : "mnemolith.echo.status_idle",
                Math.round(this.getHealth()),
                Math.round(this.getMaxHealth()),
                this.inventory.totalCount()), true);
        return InteractionResult.SUCCESS_SERVER;
    }

    public void openInventory(ServerPlayer player) {
        int id = this.getId();
        player.openMenu(
                new SimpleMenuProvider((containerId, playerInventory, p) -> new EchoMenu(containerId, playerInventory, this.inventory, this), this.getDisplayName()),
                buffer -> {
                    buffer.writeVarInt(id);
                    EchoLesson.STREAM_CODEC.encode(buffer, this.job.lesson());
                });
    }

    // ---- damage and death ----

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypeTags.IS_FALL) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getDirectEntity() instanceof Player player && this.isOwnedBy(player) && !player.isShiftKeyDown()) {
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive() && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                && attacker instanceof net.minecraft.world.entity.monster.Enemy) {
            // Stage 3: a working echo never fights back; it runs and resumes later.
            this.job.onAttacked(level, this, attacker);
            this.syncJob();
        }
        return hurt;
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        if (this.silentRemoval) {
            return;
        }
        for (int i = 0; i < EchoInventory.SIZE; i++) {
            ItemStack stack = this.inventory.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                this.spawnAtLocation(level, stack);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        boolean wasAlive = !this.isRemoved() && !this.dead;
        super.die(source);
        if (wasAlive && this.dead && this.level() instanceof ServerLevel level && !this.silentRemoval) {
            EchoLife.onEchoBodyDied(level, this.ownerId(), this.getUUID(), this.blockPosition(), this.ownerName());
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.DISCARDED && !this.silentRemoval && this.level() instanceof ServerLevel level && !this.dead) {
            // Something outside this mod discarded a live echo. Keep its items and free the owner's slot.
            for (int i = 0; i < EchoInventory.SIZE; i++) {
                ItemStack stack = this.inventory.removeItemNoUpdate(i);
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(level, stack);
                }
            }
            EchoRegistry.get(level.getServer()).remove(this.ownerId(), this.getUUID());
        }
        super.remove(reason);
    }

    // ---- client presentation ----

    @Override
    public boolean isCurrentlyGlowing() {
        if (this.level().isClientSide() && EchoView.thermal()) {
            // Stage 2: every echo gets a filled silhouette; the vanilla edge outline only marks the targeted one.
            return EchoView.isTarget(this.getId());
        }
        return super.isCurrentlyGlowing();
    }

    @Override
    public int getTeamColor() {
        if (this.level().isClientSide() && EchoView.thermal()) {
            return EchoView.outlineColor(this.getId());
        }
        return super.getTeamColor();
    }

    @Override
    public Component getName() {
        if (this.hasCustomName()) {
            return super.getName();
        }
        return Component.translatable("entity.mnemolith.echo.named", this.ownerName());
    }

    // ---- save ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        List<SlotStack> stacks = new java.util.ArrayList<>();
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            if (!this.main.get(i).isEmpty()) {
                stacks.add(new SlotStack(i, this.main.get(i)));
            }
        }
        output.store("echo_main", SlotStack.LIST_CODEC, stacks);
        output.putInt("echo_selected", this.selected);
        output.putLong("echo_generation", this.generation);
        if (this.recording != null) {
            output.store("echo_recording", EchoRecording.CODEC, this.recording);
        }
        output.putInt("echo_replay_tick", this.replayTick);
        output.store("echo_job", EchoJob.Saved.CODEC, this.job.save());
        if (this.bonusHealth > 0.0D) {
            output.putDouble("echo_bonus_health", this.bonusHealth);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            this.main.set(i, ItemStack.EMPTY);
        }
        input.read("echo_main", SlotStack.LIST_CODEC).ifPresent(list -> {
            for (SlotStack stack : list) {
                if (stack.slot() >= 0 && stack.slot() < EchoInventory.MAIN) {
                    this.main.set(stack.slot(), stack.stack());
                }
            }
        });
        this.selected = Math.max(0, Math.min(8, input.getIntOr("echo_selected", 0)));
        this.generation = input.getLongOr("echo_generation", 0L);
        this.recording = input.read("echo_recording", EchoRecording.CODEC).orElse(null);
        this.replayTick = input.getIntOr("echo_replay_tick", -1);
        input.read("echo_job", EchoJob.Saved.CODEC).ifPresent(this.job::load);
        this.bonusHealth = Math.max(0.0D, input.getDoubleOr("echo_bonus_health", 0.0D));
        this.applyConfiguredHealth();
        if (this.recording != null && this.replayTick >= 0 && this.replayTick < this.recording.length()) {
            this.nextAction = 0;
            List<EchoAction> actions = this.recording.actions();
            while (this.nextAction < actions.size() && actions.get(this.nextAction).tick() < this.replayTick) {
                this.nextAction++;
            }
            this.beginReplayPhysics();
            this.job.beginReplay();
        } else {
            this.replayTick = -1;
        }
        this.syncJob();
    }

    public void applyConfiguredHealth() {
        var attribute = this.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(CommonConfig.ECHO_MAX_HEALTH.get() + this.bonusHealth);
        }
    }

    public double bonusHealth() {
        return this.bonusHealth;
    }

    /** Stage 3 sturdy body: sets the extra max health; current health grows by the same amount when it goes up. */
    public void applyBonusHealth(double bonus) {
        double gained = bonus - this.bonusHealth;
        this.bonusHealth = Math.max(0.0D, bonus);
        this.applyConfiguredHealth();
        if (gained > 0.0D) {
            this.heal((float) gained);
        } else if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }
}
