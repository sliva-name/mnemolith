package com.mnemolith.entity.mob;

import com.mnemolith.armory.Armory;
import com.mnemolith.armory.ArmoryItems;
import com.mnemolith.armory.ArmorySet;
import com.mnemolith.content.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Stands its ground until struck, or until it trusts the player. Hush fiber builds trust.
 * At three fibers, paper is answered with one archival tablet a day.
 */
public class KinWitness extends PathfinderMob {
    private int trust;
    private long nextGift;
    private int angryTicks;

    public KinWitness(EntityType<? extends KinWitness> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1D, false));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public int trust() {
        return this.trust;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.mnemolith.audio.ModSounds.WITNESS_AMBIENT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return com.mnemolith.audio.ModSounds.WITNESS_HURT.get();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.angryTicks > 0) {
            this.angryTicks--;
        } else if (this.getTarget() != null && this.tickCount % 20 == 0) {
            this.setTarget(null);
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.getTarget() == null) {
            FractureStalker hunter = level.getEntitiesOfClass(FractureStalker.class, this.getBoundingBox().inflate(8.0D))
                    .stream().findFirst().orElse(null);
            if (hunter != null) {
                this.getNavigation().moveTo(this.getX() + (this.getX() - hunter.getX()), this.getY(), this.getZ() + (this.getZ() - hunter.getZ()), 1.2D);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof Player player) {
            this.angryTicks = Armory.full(player, ArmorySet.HUSH) ? 160 : 400;
            this.setTarget(player);
            this.trust = Math.max(0, this.trust - 1);
        }
        return hurt;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.angryTicks > 0) {
            return InteractionResult.FAIL;
        }
        if (stack.is(ArmoryItems.HUSH_FIBER.get()) && this.trust < 4) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            this.trust++;
            if (player instanceof net.minecraft.server.level.ServerPlayer server) {
                server.sendSystemMessage(Component.translatable("mnemolith.armory.witness_trust", this.trust), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.PAPER) && this.trust >= 3 && this.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            if (now < this.nextGift) {
                if (player instanceof net.minecraft.server.level.ServerPlayer server) {
                    server.sendSystemMessage(Component.translatable("mnemolith.armory.witness_wait"), true);
                }
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            this.nextGift = now + 24000L;
            ItemStack tablet = new ItemStack(ModItems.ARCHIVAL_TABLET.get());
            if (!player.getInventory().add(tablet)) {
                this.spawnAtLocation(level, tablet);
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer server) {
                server.sendSystemMessage(Component.translatable("mnemolith.armory.witness_gift"), true);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("witness_trust", this.trust);
        output.putLong("witness_gift", this.nextGift);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.trust = input.getIntOr("witness_trust", 0);
        this.nextGift = input.getLongOr("witness_gift", 0L);
    }
}
