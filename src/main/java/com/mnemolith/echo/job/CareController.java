package com.mnemolith.echo.job;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.CareLesson;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Shear, milk, and breed nearby animals the recording taught. */
final class CareController {
    private final EchoJob job;
    CareLesson taught = CareLesson.NONE;
    int tended;
    @Nullable LivingEntity target;
    int pollTicks;

    CareController(EchoJob job) {
        this.job = job;
    }

    void resetSearch() {
        this.target = null;
        this.pollTicks = 0;
    }

    private int radius() {
        return Math.max(2, Math.min(this.job.radius(), CommonConfig.ECHO_FARM_MAX_RADIUS.get()));
    }

    void tick(ServerLevel level, EchoEntity echo) {
        BlockPos anchor = this.job.workAnchor;
        if (anchor == null) {
            anchor = echo.blockPosition();
            this.job.workAnchor = anchor;
        }
        if (!level.isLoaded(anchor)) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.UNLOADED));
            return;
        }
        if (this.job.motion.placeCooldown > 0) {
            this.job.motion.placeCooldown--;
            return;
        }
        switch (this.job.motion.phase) {
            case START, SELECT -> this.select(level, echo, anchor);
            case PATH -> this.job.motion.tickPath(level, echo);
            case WALK -> this.job.motion.tickWalk(level, echo);
            case TO_CHEST -> this.job.chests.atChest(level, echo);
            case WAIT -> {
                if (++this.job.motion.waitTicks % CommonConfig.ECHO_FARM_POLL_TICKS.get() == 0) {
                    this.job.motion.phase = JobMotion.Phase.START;
                }
            }
            default -> this.job.motion.phase = JobMotion.Phase.SELECT;
        }
    }

    private void select(ServerLevel level, EchoEntity echo, BlockPos anchor) {
        if (this.job.chests.needsDropOff(echo)) {
            if (this.job.chest == null) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.INVENTORY_FULL));
            } else {
                this.job.chests.goToChest(level, echo);
            }
            return;
        }
        LivingEntity best = this.findTarget(level, echo, anchor);
        if (best == null) {
            this.job.release(echo);
            this.job.setStatus(new JobStatus(JobStatus.Kind.CARE_WAIT, "", this.tended, 0));
            this.job.motion.phase = JobMotion.Phase.WAIT;
            this.job.motion.waitTicks = 0;
            return;
        }
        this.target = best;
        this.job.setStatus(new JobStatus(JobStatus.Kind.CARE, "", this.tended, 0));
        LivingEntity goal = best;
        this.job.motion.startPath(level, echo, new EchoNav.Goal() {
            @Override
            public boolean reached(BlockPos feet) {
                return feet.distSqr(goal.blockPosition()) <= 9.0D;
            }

            @Override
            public double estimate(BlockPos feet) {
                return Math.max(0.0D, Math.sqrt(feet.distSqr(goal.blockPosition())) - 2.0D);
            }
        }, null, 0, false);
    }

    private @Nullable LivingEntity findTarget(ServerLevel level, EchoEntity echo, BlockPos anchor) {
        int r = this.radius();
        AABB box = new AABB(anchor).inflate(r, r, r);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box, entity -> this.canTend(echo, entity));
        if (found.isEmpty()) {
            return null;
        }
        found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(echo)));
        return found.get(0);
    }

    private boolean canTend(EchoEntity echo, LivingEntity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        if (this.taught.shear() && entity instanceof Shearable shearable && shearable.readyForShearing()
                && echo.inventory().find(Items.SHEARS) >= 0) {
            return true;
        }
        if (this.taught.milk() && entity instanceof AbstractCow cow && !cow.isBaby()
                && echo.inventory().find(Items.BUCKET) >= 0) {
            return true;
        }
        if (this.taught.breed() && entity instanceof Animal animal && !animal.isBaby() && animal.canFallInLove()
                && !animal.isInLove()) {
            Item food = this.taught.breedFood().orElse(Items.AIR);
            if (food != Items.AIR && echo.inventory().find(food) >= 0 && animal.isFood(new ItemStack(food))) {
                return true;
            }
            // Any taught food already in inventory that this animal accepts.
            EchoInventory inventory = echo.inventory();
            for (int i = 0; i < EchoInventory.MAIN; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && animal.isFood(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    void act(ServerLevel level, EchoEntity echo) {
        LivingEntity target = this.target;
        this.target = null;
        this.job.motion.phase = JobMotion.Phase.SELECT;
        if (target == null || !target.isAlive() || !this.canTend(echo, target)) {
            return;
        }
        echo.lookAt(target.getEyePosition());
        boolean did = false;
        if (this.taught.shear() && target instanceof Shearable shearable && shearable.readyForShearing()) {
            did = this.shear(level, echo, target, shearable);
        } else if (this.taught.milk() && target instanceof AbstractCow) {
            did = this.interactWith(level, echo, target, Items.BUCKET);
        } else if (this.taught.breed() && target instanceof Animal) {
            Item food = this.pickFood(echo, (Animal) target);
            if (food != Items.AIR) {
                did = this.interactWith(level, echo, target, food);
            }
        }
        if (did) {
            this.tended++;
            this.job.motion.unreachableInRow = 0;
            this.job.strain.onWorkAction(level, echo, target.blockPosition());
            this.job.setStatus(new JobStatus(JobStatus.Kind.CARE, "", this.tended, 0));
            this.job.motion.placeCooldown = JobLimits.PLACE_INTERVAL;
        }
    }

    private Item pickFood(EchoEntity echo, Animal animal) {
        Item preferred = this.taught.breedFood().orElse(Items.AIR);
        if (preferred != Items.AIR && echo.inventory().find(preferred) >= 0 && animal.isFood(new ItemStack(preferred))) {
            return preferred;
        }
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            ItemStack stack = echo.inventory().getItem(i);
            if (!stack.isEmpty() && animal.isFood(stack)) {
                return stack.getItem();
            }
        }
        return Items.AIR;
    }

    private boolean shear(ServerLevel level, EchoEntity echo, LivingEntity target, Shearable shearable) {
        int slot = echo.inventory().find(Items.SHEARS);
        if (slot < 0) {
            return false;
        }
        ItemStack shears = echo.inventory().getItem(slot);
        shearable.shear(level, SoundSource.PLAYERS, shears);
        if (shears.isDamageableItem()) {
            shears.setDamageValue(shears.getDamageValue() + 1);
            if (shears.getDamageValue() >= shears.getMaxDamage()) {
                echo.inventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        echo.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** Lend {@code item} to the owner's fake player and interact with {@code target}. */
    private boolean interactWith(ServerLevel level, EchoEntity echo, LivingEntity target, Item item) {
        UUID owner = echo.ownerId();
        if (owner == null) {
            return false;
        }
        int slot = echo.inventory().find(item);
        if (slot < 0) {
            return false;
        }
        FakePlayer hand = EchoHands.hand(level, owner, echo.ownerName());
        hand.snapTo(echo.getX(), echo.getY(), echo.getZ(), echo.getYRot(), echo.getXRot());
        ItemStack stack = echo.inventory().getItem(slot);
        ItemStack displaced = hand.getInventory().getItem(0);
        hand.getInventory().setSelectedSlot(0);
        hand.getInventory().setItem(0, stack);
        echo.inventory().setItem(slot, ItemStack.EMPTY);
        InteractionResult result;
        try {
            result = hand.interactOn(target, InteractionHand.MAIN_HAND, target.getBoundingBox().getCenter());
        } finally {
            ItemStack after = hand.getInventory().getItem(0);
            hand.getInventory().setItem(0, displaced);
            // Milk swaps bucket → milk bucket; breeding may consume one.
            echo.inventory().setItem(slot, after);
            // Sweep anything else the fake player picked up.
            for (int i = 0; i < hand.getInventory().getContainerSize(); i++) {
                ItemStack leftover = hand.getInventory().removeItemNoUpdate(i);
                if (!leftover.isEmpty()) {
                    echo.inventory().insert(leftover);
                    if (!leftover.isEmpty()) {
                        echo.spawnAtLocation(level, leftover);
                    }
                }
            }
        }
        echo.swing(InteractionHand.MAIN_HAND);
        return result.consumesAction();
    }
}
