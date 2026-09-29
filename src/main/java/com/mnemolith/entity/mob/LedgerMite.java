package com.mnemolith.entity.mob;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * A small archive mite. Paper tames it. While the owner sneaks it walks toward the nearest archival stratum.
 * When the chunk saturates it chirps as a canary; it also gathers fallen slips and recovers what an archivist stole.
 */
public class LedgerMite extends TamableAnimal {
    private BlockPos stratum;
    private int stratumCooldown;
    private int alarmCooldown;
    private int gatherCooldown;

    public LedgerMite(EntityType<? extends LedgerMite> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.4D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.1D, stack -> stack.is(Items.PAPER), false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return ModSounds.MITE_AMBIENT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return ModSounds.MITE_HURT.get();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.PAPER);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.isFood(stack) && !this.isTame()) {
            if (this.level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (this.random.nextInt(3) == 0) {
                this.tame(player);
                this.level().broadcastEntityEvent(this, (byte) 7);
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        LedgerMite child = com.mnemolith.entity.ModEntities.LEDGER_MITE.get().create(level, EntitySpawnReason.BREEDING);
        if (child != null && this.getOwner() != null) {
            child.setOwner(this.getOwner());
        }
        return child;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level) || !this.isTame()) {
            return;
        }
        this.tickAlarm(level);
        this.tickGather(level);
        if (this.stratumCooldown-- > 0) {
            return;
        }
        if (!(this.getOwner() instanceof Player owner) || !owner.isShiftKeyDown() || owner.distanceToSqr(this) > 32.0D * 32.0D) {
            return;
        }
        this.stratumCooldown = 20;
        if (this.stratum == null || this.blockPosition().distSqr(this.stratum) < 4.0D) {
            this.stratum = nearestStratum(level);
        }
        if (this.stratum != null) {
            this.getNavigation().moveTo(this.stratum.getX() + 0.5D, this.stratum.getY(), this.stratum.getZ() + 0.5D, 1.15D);
        }
    }

    private void tickAlarm(ServerLevel level) {
        if (this.alarmCooldown-- > 0) {
            return;
        }
        this.alarmCooldown = 40;
        var memory = LoadedChunkMemory.existing(level.getChunkAt(this.blockPosition()));
        if (memory == null) {
            return;
        }
        PressureBand band = MemoryPressure.band(memory.cachedPressure());
        if (band.ordinal() < PressureBand.SATURATED.ordinal()) {
            return;
        }
        level.playSound(null, this.blockPosition(), ModSounds.PRESSURE_WARN.get(), SoundSource.NEUTRAL, 0.55F, 1.6F);
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.25D, 0.0D));
        this.hurtMarked = true;
        if (this.getOwner() instanceof net.minecraft.server.level.ServerPlayer owner && owner.distanceToSqr(this) < 24.0D * 24.0D) {
            owner.sendSystemMessage(net.minecraft.network.chat.Component.translatable("mnemolith.mite.alarm"), true);
        }
    }

    private void tickGather(ServerLevel level) {
        if (this.gatherCooldown-- > 0 || !(this.getOwner() instanceof Player owner)) {
            return;
        }
        this.gatherCooldown = 15;
        AABB box = this.getBoundingBox().inflate(2.5D);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            ItemStack stack = item.getItem();
            if (!ImprintSlips.isSlip(stack) && stack.getItem() != ModItems.RESIDUAL_SHARD.get()) {
                continue;
            }
            if (owner.getInventory().add(stack)) {
                item.discard();
                level.playSound(null, this.blockPosition(), ModSounds.MITE_AMBIENT.get(), SoundSource.NEUTRAL, 0.4F, 1.8F);
                return;
            }
        }
        for (Archivist archivist : level.getEntitiesOfClass(Archivist.class, box.inflate(1.5D))) {
            ItemStack recovered = archivist.reclaimStolen();
            if (recovered.isEmpty()) {
                continue;
            }
            if (!owner.getInventory().add(recovered) && !recovered.isEmpty()) {
                owner.drop(recovered, false);
            }
            level.playSound(null, this.blockPosition(), ModSounds.EXTRACT.get(), SoundSource.NEUTRAL, 0.5F, 1.5F);
            return;
        }
    }

    private BlockPos nearestStratum(ServerLevel level) {
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestDistance = 48.0D * 48.0D;
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                var memory = LoadedChunkMemory.existing(level.getChunk(cx + dx, cz + dz));
                if (memory == null) {
                    continue;
                }
                for (BlockPos pos : memory.strataCopy()) {
                    double distance = origin.distSqr(pos);
                    if (distance < bestDistance) {
                        best = pos;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
    }
}
