package com.mnemolith.entity.mob;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.MemoryMob;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobTuning;
import com.mnemolith.entity.ai.ActionMemory;
import com.mnemolith.entity.ai.CopiedActionKind;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.particle.ModParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Telegraphs, then replays the last whitelisted thing a nearby player did. */
public class MomentReplicant extends MemoryMob {
    private int telegraphTicks;
    private int blindTicks;
    private int executeTicks;
    private ActionMemory.@Nullable CopiedAction pending;
    private @Nullable ServerPlayer focus;

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
        this.telegraphTicks = 0;
        this.executeTicks = 0;
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

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.blindTicks > 0) {
            this.blindTicks--;
            this.setAction(MobActions.FLEE);
            return;
        }
        if (!MobTuning.replicantEnabled() && this.telegraphTicks <= 0 && this.pending == null) {
            return;
        }
        if (this.telegraphTicks > 0) {
            this.telegraphTicks--;
            this.setAction(MobActions.TELEGRAPH);
            if (this.telegraphTicks == 0) {
                this.executeTicks = 30;
                this.replay(level);
            }
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
            return;
        }
        this.focus = chosen;
        Optional<ActionMemory.CopiedAction> recent = ActionMemory.recent(level, chosen);
        if (recent.isEmpty()) {
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

    private void placeCopy(ServerLevel level, @Nullable ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            this.swing(InteractionHand.MAIN_HAND);
            return;
        }
        Block block = blockItem.getBlock();
        if (block == Blocks.TNT || block == Blocks.COMMAND_BLOCK || block == Blocks.REPEATING_COMMAND_BLOCK || block == Blocks.CHAIN_COMMAND_BLOCK || block == Blocks.STRUCTURE_BLOCK) {
            return;
        }
        Direction facing = player == null ? this.getDirection() : player.getDirection();
        BlockPos target = (player == null ? this.blockPosition() : player.blockPosition()).relative(facing);
        BlockState existing = level.getBlockState(target);
        if (existing.isAir() || existing.canBeReplaced()) {
            level.setBlock(target, block.defaultBlockState(), Block.UPDATE_ALL);
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

    private static final class BlindGoal extends Goal {
        private final MomentReplicant replicant;

        private BlindGoal(MomentReplicant replicant) {
            this.replicant = replicant;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.replicant.blinded();
        }

        @Override
        public void tick() {
            Player player = this.replicant.level().getNearestPlayer(this.replicant, 12.0D);
            Vec3 away = player == null ? this.replicant.getLookAngle().scale(-1.0D) : this.replicant.position().subtract(player.position());
            if (away.lengthSqr() < 0.01D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            away = away.normalize();
            if (MobTuning.sensorDue(this.replicant.tickCount, this.replicant.getNavigation().isDone())) {
                this.replicant.getNavigation().moveTo(this.replicant.getX() + away.x * 6.0D, this.replicant.getY(), this.replicant.getZ() + away.z * 6.0D, 1.2D);
            }
            this.replicant.setAction(MobActions.FLEE);
        }
    }

    private static final class ApproachGoal extends Goal {
        private final MomentReplicant replicant;

        private ApproachGoal(MomentReplicant replicant) {
            this.replicant = replicant;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return MobTuning.replicantEnabled() && !this.replicant.blinded() && this.replicant.telegraphTicks <= 0;
        }

        @Override
        public void tick() {
            Player player = this.replicant.focus != null ? this.replicant.focus : this.replicant.level().getNearestPlayer(this.replicant, 16.0D);
            if (player == null) {
                return;
            }
            if (this.replicant.distanceToSqr(player) > 9.0D && MobTuning.sensorDue(this.replicant.tickCount, this.replicant.getNavigation().isDone())) {
                this.replicant.getNavigation().moveTo(player, 0.9D);
            }
        }
    }
}
