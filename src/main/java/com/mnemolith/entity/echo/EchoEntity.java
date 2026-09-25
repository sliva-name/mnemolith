package com.mnemolith.entity.echo;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.menu.EchoMenu;
import com.mnemolith.echo.EchoAction;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.EchoView;
import com.mnemolith.echo.SlotStack;

import net.minecraft.core.NonNullList;
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

/**
 * An echo: replays its owner's recording, then stays as an idle helper body.
 * It owns a real, player-shaped inventory that starts empty. World edits go through {@link EchoHands}.
 */
public class EchoEntity extends MemoryAvatar {
    private static final EntityDataAccessor<Boolean> DATA_REPLAYING = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.BOOLEAN);
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
        this.beginReplayPhysics();
    }

    /** Keeps {@code recording} as this body's lesson without replaying it (used when a possessed body is released). */
    public void keepRecording(EchoRecording recording) {
        this.recording = recording;
        this.stopReplay();
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
            if (!this.isReplaying() && this.tickCount % 100 == 0 && this.getHealth() < this.getMaxHealth() && this.isAlive()) {
                this.heal(1.0F);
            }
        }
        super.tick();
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
                buffer -> buffer.writeVarInt(id));
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
        return super.hurtServer(level, source, damage);
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
            return true;
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
        if (this.recording != null && this.replayTick >= 0 && this.replayTick < this.recording.length()) {
            this.nextAction = 0;
            List<EchoAction> actions = this.recording.actions();
            while (this.nextAction < actions.size() && actions.get(this.nextAction).tick() < this.replayTick) {
                this.nextAction++;
            }
            this.beginReplayPhysics();
        } else {
            this.replayTick = -1;
        }
    }

    public void applyConfiguredHealth() {
        var attribute = this.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(CommonConfig.ECHO_MAX_HEALTH.get());
        }
    }
}
