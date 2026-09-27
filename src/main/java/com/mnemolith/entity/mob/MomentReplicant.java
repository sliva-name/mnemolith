package com.mnemolith.entity.mob;

import com.mnemolith.entity.ai.BlindGoal;
import com.mnemolith.entity.ai.ApproachGoal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.MemoryMob;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobTuning;
import com.mnemolith.entity.ai.ActionMemory;
import com.mnemolith.entity.ai.CopiedActionKind;
import com.mnemolith.recall.Gesture;
import com.mnemolith.recall.GestureKind;
import com.mnemolith.recall.LivingMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.EventHooks;

/** Telegraphs, then replays the last whitelisted thing a nearby player did. */
public class MomentReplicant extends MemoryMob {
    private int telegraphTicks;
    private int blindTicks;
    private int executeTicks;
    private ActionMemory.@Nullable CopiedAction pending;
    private @Nullable ServerPlayer focus;
    // Stage 3: copying a working echo's job for a while (see EchoJob#beginMimic).
    private com.mnemolith.entity.echo.@Nullable EchoEntity pendingEcho;
    private com.mnemolith.entity.echo.@Nullable EchoEntity mimicEcho;
    private int mimicTicks;
    private int mimicCooldown;
    /** Player this replicant already rolled recall for. A failed roll does not try again. */
    private @Nullable UUID recallDeclined;
    private @Nullable Gesture recallGesture;
    private int recallPhase;
    private int recallTicks;
    private boolean recallPlayed;

    public MomentReplicant(EntityType<? extends MomentReplicant> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BlindGoal(this));
        this.goalSelector.addGoal(2, new ApproachGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        this.applyAttackDamage(MobTuning.replicantDamage());
        return result;
    }

    public static void blindNearby(ServerLevel level, Player source) {
        var box = source.getBoundingBox().inflate(12.0D);
        for (MomentReplicant replicant : level.getEntitiesOfClass(MomentReplicant.class, box)) {
            replicant.blind();
        }
        level.playSound(null, source.blockPosition(), ModSounds.REPLICANT_BLIND.get(), SoundSource.PLAYERS, 0.8F, 1.4F);
        MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), source.getX(), source.getEyeY(), source.getZ(), 12);
    }

    public void blind() {
        this.pending = null;
        this.pendingEcho = null;
        this.mimicEcho = null;
        this.mimicTicks = 0;
        this.telegraphTicks = 0;
        this.executeTicks = 0;
        this.clearRecall();
        this.blindTicks = MobTuning.BLIND_TICKS;
        this.setTarget(null);
        this.setAction(MobActions.FLEE);
    }

    public void beginTelegraph(CopiedActionKind kind, @Nullable ServerPlayer player) {
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem().copy();
        BlockPos pos = player == null ? this.blockPosition() : player.blockPosition();
        this.pending = new ActionMemory.CopiedAction(kind, pos, stack, this.level().getGameTime(), player == null ? this.getUUID() : player.getUUID());
        this.focus = player;
        this.telegraphTicks = MobTuning.REPLICANT_TELEGRAPH_TICKS;
        this.executeTicks = 0;
        this.setAction(MobActions.TELEGRAPH);
        BlockPos at = this.blockPosition();
        Mnemolith.LOGGER.info("Mnemolith replicant telegraph={} at {},{},{}", kind.serialized(), at.getX(), at.getY(), at.getZ());
        this.playSound(ModSounds.REPLICANT_TELEGRAPH.get(), 1.0F, 1.0F);
        if (this.level() instanceof ServerLevel server) {
            MemoryFx.mob(server, ModParticles.REPLICANT_TELEGRAPH.get(), this.getX(), this.getY() + 1.2D, this.getZ(), 10);
        }
    }

    public boolean blinded() {
        return this.blindTicks > 0;
    }

    public boolean telegraphing() {
        return this.telegraphTicks > 0;
    }

    public boolean recalling() {
        return this.recallPhase > 0 && this.recallGesture != null;
    }

    public int recallPhase() {
        return this.recallPhase;
    }

    public boolean recallPlayed() {
        return this.recallPlayed;
    }

    /**
     * Idle, turn to the old yaw, play that gesture once, then discard. No chat.
     * Does not hurt the player. A block gesture places a replicated moment through {@code FakePlace}.
     */
    public void beginRecall(ServerPlayer player, Gesture gesture) {
        this.pending = null;
        this.pendingEcho = null;
        this.mimicEcho = null;
        this.mimicTicks = 0;
        this.telegraphTicks = 0;
        this.executeTicks = 0;
        this.focus = player;
        this.recallDeclined = player.getUUID();
        this.recallGesture = gesture;
        this.recallPhase = 1;
        this.recallTicks = 0;
        this.recallPlayed = false;
        this.setTarget(null);
        this.getNavigation().stop();
        this.setAction(MobActions.IDLE);
        BlockPos at = this.blockPosition();
        Mnemolith.LOGGER.info("Mnemolith replicant recall kind={} at {},{},{}", gesture.kind().getSerializedName(), at.getX(), at.getY(), at.getZ());
    }

    public void tickRecall(ServerLevel level) {
        Gesture gesture = this.recallGesture;
        if (gesture == null || this.recallPhase <= 0) {
            this.clearRecall();
            return;
        }
        this.getNavigation().stop();
        this.setTarget(null);
        ServerPlayer player = this.focus != null && this.focus.isAlive() ? this.focus : null;
        int idle = com.mnemolith.config.CommonConfig.RECALL_IDLE_TICKS.get();
        int match = com.mnemolith.config.CommonConfig.RECALL_MATCH_TICKS.get();
        int vanish = com.mnemolith.config.CommonConfig.RECALL_VANISH_TICKS.get();
        switch (this.recallPhase) {
            case 1 -> {
                this.setAction(MobActions.IDLE);
                if (player != null) {
                    this.getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ(), 30.0F, 30.0F);
                }
                if (++this.recallTicks >= idle) {
                    this.recallPhase = 2;
                    this.recallTicks = 0;
                    this.equipRecall(gesture);
                }
            }
            case 2 -> {
                this.setAction(MobActions.IDLE);
                this.lookAlong(gesture);
                if (++this.recallTicks >= match) {
                    this.recallPhase = 3;
                    this.recallTicks = 0;
                }
            }
            case 3 -> {
                this.setAction(MobActions.ATTACK);
                this.lookAlong(gesture);
                if (!this.recallPlayed) {
                    this.recallPlayed = true;
                    this.playRecall(level, gesture);
                }
                if (++this.recallTicks >= 8) {
                    this.recallPhase = 4;
                    this.recallTicks = 0;
                    this.setAction(MobActions.IDLE);
                }
            }
            case 4 -> {
                this.setAction(MobActions.IDLE);
                if (this.recallTicks == 0) {
                    MemoryFx.mob(level, ModParticles.STRIDER_TRAIL.get(), this.getX(), this.getY() + 0.2D, this.getZ(), 8);
                    MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), this.getX(), this.getY() + 1.0D, this.getZ(), 10);
                }
                if (++this.recallTicks >= vanish) {
                    this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    this.discard();
                }
            }
            default -> this.clearRecall();
        }
    }

    private void clearRecall() {
        this.recallGesture = null;
        this.recallPhase = 0;
        this.recallTicks = 0;
        this.recallPlayed = false;
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void lookAlong(Gesture gesture) {
        float yaw = gesture.yaw() * ((float) Math.PI / 180.0F);
        float pitch = gesture.pitch() * ((float) Math.PI / 180.0F);
        float cosPitch = Mth.cos(pitch);
        Vec3 dir = new Vec3(-Mth.sin(yaw) * cosPitch, -Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
        Vec3 at = this.getEyePosition().add(dir.scale(4.0D));
        this.getLookControl().setLookAt(at.x, at.y, at.z, 40.0F, 40.0F);
    }

    private void equipRecall(Gesture gesture) {
        ItemStack stack = recallHand(gesture);
        this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        if (!stack.isEmpty()) {
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    /**
     * What the recall holds. The stored item wins, then the stored block as an item.
     * Attack, place, and use still get a plain stand-in so the hand is not blank.
     * A fall stays empty. The stack does not drop.
     */
    public static ItemStack recallHand(Gesture gesture) {
        Item fromItem = itemOrAir(gesture.itemId());
        if (fromItem != Items.AIR) {
            return new ItemStack(fromItem);
        }
        Item fromBlock = blockItem(gesture.blockId());
        if (fromBlock != Items.AIR) {
            return new ItemStack(fromBlock);
        }
        return switch (gesture.kind()) {
            case ATTACK -> new ItemStack(Items.WOODEN_SWORD);
            case PLACE -> new ItemStack(Items.OAK_PLANKS);
            case USE -> new ItemStack(Items.STICK);
            case FALL -> ItemStack.EMPTY;
        };
    }

    private static Item itemOrAir(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return Items.AIR;
        }
        return BuiltInRegistries.ITEM.getOptional(parsed).orElse(Items.AIR);
    }

    private static Item blockItem(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return Items.AIR;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(parsed).orElse(Blocks.AIR);
        if (block == Blocks.AIR) {
            return Items.AIR;
        }
        Item item = block.asItem();
        return item == null ? Items.AIR : item;
    }

    private void playRecall(ServerLevel level, Gesture gesture) {
        switch (gesture.kind()) {
            case ATTACK -> {
                this.swing(InteractionHand.MAIN_HAND);
                this.hurtRemembered(level, gesture);
            }
            case PLACE -> this.placeRecall(level, gesture);
            case USE -> {
                this.swing(InteractionHand.MAIN_HAND);
                MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), this.getX(), this.getY() + 1.0D, this.getZ(), 6);
            }
            case FALL -> {
                this.setDeltaMovement(this.getDeltaMovement().x, 0.42D, this.getDeltaMovement().z);
                MemoryFx.mob(level, ModParticles.STRIDER_TRAIL.get(), this.getX(), this.getY() + 0.1D, this.getZ(), 6);
            }
        }
    }

    /** One swing at a nearby mob of the remembered type. Never the player. */
    private void hurtRemembered(ServerLevel level, Gesture gesture) {
        Identifier id = Identifier.tryParse(gesture.targetId());
        if (id == null) {
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            return;
        }
        AABB box = this.getBoundingBox().inflate(3.0D);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != this && entity.isAlive() && !(entity instanceof Player) && entity.getType() == type)) {
            this.doHurtTarget(level, nearby);
            return;
        }
    }

    /** Ghost block in front of this body, facing the stored yaw. No owner imprint. */
    private void placeRecall(ServerLevel level, Gesture gesture) {
        this.swing(InteractionHand.MAIN_HAND);
        if (!EventHooks.canEntityGrief(level, this)) {
            return;
        }
        Direction facing = Direction.fromYRot(gesture.yaw());
        BlockPos target = this.blockPosition().relative(facing);
        BlockState copy = ModBlocks.REPLICATED_MOMENT.get().defaultBlockState();
        if (!level.getBlockState(target).isAir() || !level.isUnobstructed(copy, target, CollisionContext.empty())) {
            return;
        }
        if (com.mnemolith.echo.FakePlace.placeGhost(level, this.focus, this, target, copy)) {
            MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 6);
        }
    }

    public @Nullable ServerPlayer focus() {
        return this.focus;
    }

    public com.mnemolith.entity.echo.@Nullable EchoEntity mimicEcho() {
        return this.mimicEcho;
    }

    public boolean isMimicking(com.mnemolith.entity.echo.EchoEntity echo) {
        return this.mimicEcho == echo && this.mimicTicks > 0;
    }

    private boolean canMimic(com.mnemolith.entity.echo.EchoEntity echo) {
        return echo.isAlive() && !echo.isReplaying() && echo.job().isWorking() && !echo.job().alarmed() && !echo.job().mimicked()
                && !com.mnemolith.echo.graft.EchoGrafts.unnoticed(echo);
    }

    /** Looks for a working echo within 12 blocks and starts the telegraph before copying its job. */
    private void tryMimicEcho(ServerLevel level) {
        if (this.mimicCooldown > 0 || com.mnemolith.config.CommonConfig.ECHO_REPLICANT_MIMIC_TICKS.get() <= 0) {
            return;
        }
        com.mnemolith.entity.echo.EchoEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (com.mnemolith.entity.echo.EchoEntity echo : level.getEntitiesOfClass(com.mnemolith.entity.echo.EchoEntity.class, this.getBoundingBox().inflate(12.0D), this::canMimic)) {
            double distance = this.distanceToSqr(echo);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = echo;
            }
        }
        if (best == null) {
            return;
        }
        this.pendingEcho = best;
        this.telegraphTicks = MobTuning.REPLICANT_TELEGRAPH_TICKS;
        this.setAction(MobActions.TELEGRAPH);
        BlockPos at = this.blockPosition();
        Mnemolith.LOGGER.info("Mnemolith replicant telegraph=echo_job owner={} at {},{},{}", best.ownerName(), at.getX(), at.getY(), at.getZ());
        this.playSound(ModSounds.REPLICANT_TELEGRAPH.get(), 1.0F, 0.9F);
        MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), this.getX(), this.getY() + 1.2D, this.getZ(), 10);
    }

    /** Starts copying {@code echo}'s job right away (after the telegraph; QA calls it directly). */
    public boolean startMimic(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo) {
        this.pendingEcho = null;
        if (!this.canMimic(echo) || this.distanceToSqr(echo) > 16.0D * 16.0D) {
            this.setAction(MobActions.IDLE);
            this.mimicCooldown = 200;
            return false;
        }
        int ticks = com.mnemolith.config.CommonConfig.ECHO_REPLICANT_MIMIC_TICKS.get();
        this.mimicEcho = echo;
        this.mimicTicks = ticks;
        echo.job().beginMimic(this.getUUID(), ticks, com.mnemolith.config.CommonConfig.ECHO_REPLICANT_UNDO_MAX.get());
        this.setAction(MobActions.IDLE);
        MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), echo.getX(), echo.getY() + 1.2D, echo.getZ(), 12);
        Mnemolith.LOGGER.info("Mnemolith replicant mimics echo owner={} mode={} ticks={}", echo.ownerName(), echo.job().mode().getSerializedName(), ticks);
        return true;
    }

    private void tickMimic(ServerLevel level) {
        com.mnemolith.entity.echo.EchoEntity echo = this.mimicEcho;
        if (echo == null || !echo.isAlive() || !echo.job().mimicked() || --this.mimicTicks <= 0) {
            this.mimicEcho = null;
            this.mimicTicks = 0;
            this.mimicCooldown = 600;
            this.setAction(MobActions.IDLE);
            return;
        }
        // Mirrors the echo: looks where it looks and swings when it swings.
        Vec3 look = echo.getEyePosition().add(echo.getLookAngle().scale(3.0D));
        this.getLookControl().setLookAt(look.x, look.y, look.z, 30.0F, 30.0F);
        if (echo.swinging && !this.swinging) {
            this.swing(InteractionHand.MAIN_HAND);
        }
        if (this.tickCount % 10 == 0) {
            MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), this.getX(), this.getY() + 1.4D, this.getZ(), 2);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.blindTicks > 0) {
            this.blindTicks--;
            this.setAction(MobActions.FLEE);
            return;
        }
        if (this.recalling()) {
            this.tickRecall(level);
            return;
        }
        if (this.mimicCooldown > 0) {
            this.mimicCooldown--;
        }
        if (!MobTuning.replicantEnabled() && this.telegraphTicks <= 0 && this.pending == null && this.mimicTicks <= 0 && !this.recalling()) {
            return;
        }
        if (this.telegraphTicks > 0) {
            this.telegraphTicks--;
            this.setAction(MobActions.TELEGRAPH);
            if (this.telegraphTicks == 0) {
                com.mnemolith.entity.echo.EchoEntity echo = this.pendingEcho;
                if (echo != null) {
                    this.startMimic(level, echo);
                } else {
                    this.executeTicks = 30;
                    this.replay(level);
                }
            }
            return;
        }
        if (this.mimicTicks > 0) {
            this.tickMimic(level);
            return;
        }
        if (this.executeTicks > 0) {
            this.executeTicks--;
            this.continueReplay(level);
            return;
        }
        if (this.tickCount % 10 != 0) {
            return;
        }
        ServerPlayer chosen = this.choosePlayer(level);
        if (chosen == null) {
            this.tryMimicEcho(level);
            return;
        }
        this.focus = chosen;
        if (this.recallDeclined == null || !this.recallDeclined.equals(chosen.getUUID())) {
            if (LivingMemory.canRollRecall(chosen)) {
                this.recallDeclined = chosen.getUUID();
                if (LivingMemory.rollRecall(this, chosen)) {
                    return;
                }
            }
        }
        Optional<ActionMemory.CopiedAction> recent = ActionMemory.recent(level, chosen);
        if (recent.isEmpty()) {
            this.tryMimicEcho(level);
            return;
        }
        ActionMemory.CopiedAction action = recent.get();
        this.pending = action;
        this.telegraphTicks = MobTuning.REPLICANT_TELEGRAPH_TICKS;
        this.setAction(MobActions.TELEGRAPH);
        BlockPos at = this.blockPosition();
        Mnemolith.LOGGER.info("Mnemolith replicant telegraph={} at {},{},{}", action.kind().serialized(), at.getX(), at.getY(), at.getZ());
        this.playSound(ModSounds.REPLICANT_TELEGRAPH.get(), 1.0F, 1.2F);
    }

    private @Nullable ServerPlayer choosePlayer(ServerLevel level) {
        if (!this.twin()) {
            Player nearest = level.getNearestPlayer(this, 16.0D);
            return nearest instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        }
        List<ServerPlayer> nearby = level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(16.0D));
        if (nearby.isEmpty()) {
            return null;
        }
        nearby.sort(Comparator.comparingDouble(this::distanceToSqr));
        if (nearby.size() >= 2) {
            return nearby.get((this.tickCount / 20) % 2);
        }
        return nearby.getFirst();
    }

    private void replay(ServerLevel level) {
        ActionMemory.CopiedAction action = this.pending;
        if (action == null) {
            return;
        }
        this.setAction(MobActions.ATTACK);
        ServerPlayer player = this.focus != null && this.focus.isAlive() ? this.focus : this.choosePlayer(level);
        switch (action.kind()) {
            case MELEE -> {
                if (player != null && this.distanceToSqr(player) < 6.0D) {
                    this.doHurtTarget(level, player);
                }
            }
            case JUMP -> {
                Vec3 toward = player == null ? this.getLookAngle() : player.position().subtract(this.position()).normalize();
                this.jumpFromGround();
                this.setDeltaMovement(toward.x * 0.35D, Math.max(0.42D, this.getDeltaMovement().y), toward.z * 0.35D);
            }
            case PLACE -> this.placeCopy(level, player, action.stack());
            case USE -> {
                this.swing(InteractionHand.MAIN_HAND);
                MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), this.getX(), this.getY() + 1.0D, this.getZ(), 8);
            }
        }
    }

    private void continueReplay(ServerLevel level) {
        ActionMemory.CopiedAction action = this.pending;
        ServerPlayer player = this.focus;
        if (action == null || action.kind() != CopiedActionKind.MELEE || player == null || !player.isAlive()) {
            return;
        }
        if (MobTuning.sensorDue(this.tickCount, this.getNavigation().isDone())) {
            this.getNavigation().moveTo(player, 1.2D);
        }
        if (this.distanceToSqr(player) < 4.0D) {
            this.doHurtTarget(level, player);
            this.executeTicks = 0;
            this.pending = null;
            this.setAction(MobActions.IDLE);
        }
    }

    /**
     * Copies a block place as a fading {@link ModBlocks#REPLICATED_MOMENT}, never the block itself, so the copy cannot
     * be mined for a free block. Only air is filled, never fluids or plants, only when mob griefing allows, and never
     * inside an entity. The ghost is then placed by a fake player (the linked player, or a stable replicant profile)
     * so claim mods see the right-click and the place. That place does not write an owner imprint.
     */
    private void placeCopy(ServerLevel level, @Nullable ServerPlayer player, ItemStack stack) {
        this.swing(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof BlockItem) || !EventHooks.canEntityGrief(level, this)) {
            return;
        }
        Direction facing = player == null ? this.getDirection() : player.getDirection();
        BlockPos target = (player == null ? this.blockPosition() : player.blockPosition()).relative(facing);
        BlockState copy = ModBlocks.REPLICATED_MOMENT.get().defaultBlockState();
        if (!level.getBlockState(target).isAir() || !level.isUnobstructed(copy, target, CollisionContext.empty())) {
            return;
        }
        if (com.mnemolith.echo.FakePlace.placeGhost(level, player, this, target, copy)) {
            MemoryFx.mob(level, ModParticles.REPLICANT_TELEGRAPH.get(), target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 6);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(level, new ItemStack(ModItems.UNSTABLE_SLIP.get()));
        if (this.random.nextFloat() < 0.25F) {
            this.spawnAtLocation(level, ImprintSlips.of(ImprintTag.EXPLOSION, this.blockPosition()));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.REPLICANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.REPLICANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.REPLICANT_DEATH.get();
    }

    public static List<MomentReplicant> inColumn(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = ChunkPos.containing(pos);
        AABB column = new AABB(
                chunk.getMinBlockX(),
                level.getMinY(),
                chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1.0D,
                level.getMaxY(),
                chunk.getMaxBlockZ() + 1.0D);
        return level.getEntitiesOfClass(MomentReplicant.class, column);
    }

}
