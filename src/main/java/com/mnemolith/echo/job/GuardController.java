package com.mnemolith.echo.job;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.GuardLesson;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;
import com.mnemolith.entity.echo.ScarEntity;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Guard job: hold the post (the work anchor) and fight hostile mobs near it with the best melee weapon in the
 * inventory. Never targets players, villagers, animals, tamed or owned mobs, creepers, bosses or neutral mobs that are
 * not angry at the owner or the echo. Each landed hit costs the weapon one durability point.
 */
public final class GuardController {
    /** How far past the post radius a guard follows a foe before it turns back. */
    static final int LEASH_EXTRA = 3;
    /** Reach of a guard's hit, squared, measured between the two bodies' centres (a player reaches about 3). */
    static final double REACH_SQR = 2.6D * 2.6D;
    static final float GRAFT_BONUS = 1.5F;
    static final Identifier ADVANCEMENT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "echo_guard");

    private final EchoJob job;
    GuardLesson taught = GuardLesson.NONE;
    int defeated;
    @Nullable LivingEntity target;
    int cooldown;
    int scanTicks;
    int repathTicks;
    /** Hits that landed since the job started (QA). */
    int landed;

    GuardController(EchoJob job) {
        this.job = job;
    }

    void resetSearch() {
        this.target = null;
        this.scanTicks = 0;
        this.repathTicks = 0;
    }

    public int radius() {
        return Math.max(2, Math.min(this.job.radius(), CommonConfig.ECHO_GUARD_RADIUS.get()));
    }

    // ---- tick ----

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
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        int weapon = bestWeapon(echo);
        if (weapon < 0) {
            this.job.halt(echo, JobStatus.of(this.landed > 0 ? JobStatus.Kind.TOOL_BROKE : JobStatus.Kind.NO_TOOL, "weapon"));
            return;
        }
        LivingEntity foe = this.target;
        if (foe != null && (!canFight(echo, foe) || !this.inLeash(anchor, foe.position()))) {
            foe = null;
            this.target = null;
        }
        if (!this.inLeash(anchor, echo.position())) {
            // Chased too far: give up and go back to the post.
            foe = null;
            this.target = null;
        }
        if (foe == null && this.scanTicks++ % 10 == 0) {
            foe = this.findFoe(level, echo, anchor);
            this.target = foe;
            if (foe != null) {
                this.repathTicks = 0;
            }
        }
        if (foe == null) {
            this.holdPost(level, echo, anchor);
            return;
        }
        this.job.setStatus(new JobStatus(JobStatus.Kind.GUARD_FIGHT, BuiltInRegistries.ENTITY_TYPE.getKey(foe.getType()).toString(), this.defeated, 0));
        this.fight(level, echo, foe, weapon);
    }

    private void holdPost(ServerLevel level, EchoEntity echo, BlockPos anchor) {
        this.job.setStatus(JobStatus.of(JobStatus.Kind.GUARD_POST, this.defeated, 0));
        if (echo.blockPosition().distSqr(anchor) <= 2.0D) {
            if (this.job.mover.active()) {
                this.job.mover.stop(level, echo);
            }
            echo.setMoveTarget(null);
            return;
        }
        if (!this.job.mover.active()) {
            this.job.mover.start(level, echo, new EchoNav.Goal() {
                @Override
                public boolean reached(BlockPos feet) {
                    return feet.distSqr(anchor) <= 1.0D;
                }

                @Override
                public double estimate(BlockPos feet) {
                    return Math.sqrt(feet.distSqr(anchor));
                }
            });
        }
        if (this.job.mover.tick(level, echo) == EchoMover.Result.FAILED) {
            // The post is out of reach (blocked in): stand where it is and keep watching.
            this.job.workAnchor = echo.blockPosition();
        }
    }

    private void fight(ServerLevel level, EchoEntity echo, LivingEntity foe, int weapon) {
        echo.lookAt(foe.getEyePosition());
        if (echo.distanceToSqr(foe) <= REACH_SQR && Math.abs(foe.getY() - echo.getY()) < 2.0D) {
            if (this.job.mover.active()) {
                this.job.mover.stop(level, echo);
            }
            echo.setMoveTarget(null);
            if (this.cooldown <= 0) {
                this.strike(level, echo, foe, weapon);
            }
            return;
        }
        if (!this.job.mover.active() || ++this.repathTicks >= 10) {
            this.repathTicks = 0;
            BlockPos at = foe.blockPosition();
            this.job.mover.start(level, echo, new EchoNav.Goal() {
                @Override
                public boolean reached(BlockPos feet) {
                    return feet.distSqr(at) <= 2.0D;
                }

                @Override
                public double estimate(BlockPos feet) {
                    return Math.max(0.0D, Math.sqrt(feet.distSqr(at)) - 1.0D);
                }
            });
        }
        if (this.job.mover.tick(level, echo) == EchoMover.Result.FAILED) {
            // Out of reach (across water, up a wall): leave it and look for another.
            this.target = null;
        }
    }

    // ---- hitting ----

    private void strike(ServerLevel level, EchoEntity echo, LivingEntity foe, int weapon) {
        this.wield(echo, weapon);
        ItemStack stack = echo.getItemBySlot(EquipmentSlot.MAINHAND);
        boolean unaware = !(foe instanceof Mob mob) || mob.getTarget() != echo;
        float damage = damageFor(echo, stack, unaware);
        Temper temper = EchoGrafts.active(echo);
        if (temper == Temper.GRAVE && foe instanceof Mob mob && mob.getTarget() != echo) {
            // A grave guard pulls the mob's anger off whoever it was after (the owner, a villager) onto itself.
            mob.setTarget(echo);
        }
        this.cooldown = CommonConfig.ECHO_GUARD_ATTACK_TICKS.get();
        echo.swing(InteractionHand.MAIN_HAND);
        boolean hurt = foe.hurtServer(level, echo.damageSources().mobAttack(echo), damage);
        if (!hurt) {
            return;
        }
        this.landed++;
        // A light shove away from the echo (a player's unsprinted hit is 0.4).
        foe.push(Vec3.directionFromRotation(0.0F, echo.getYRot()).scale(0.3D).add(0.0D, 0.1D, 0.0D));
        level.playSound(null, foe.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.7F, 1.1F);
        if (temper == Temper.VOLATILE || (temper == Temper.HUSHED && unaware)) {
            EchoGrafts.spend(echo, 1);
        }
        if (!stack.isEmpty() && stack.isDamageableItem()) {
            stack.hurtAndBreak(1, level, echo, item -> level.playSound(null, echo.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 1.0F));
        }
        if (!foe.isAlive()) {
            this.defeated++;
            this.target = null;
            this.job.setStatus(JobStatus.of(JobStatus.Kind.GUARD_POST, this.defeated, 0));
            awardOwner(level, echo);
            Mnemolith.LOGGER.debug("Mnemolith echo guard defeated owner={} foe={} total={} at {}", echo.ownerName(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(foe.getType()), this.defeated, echo.blockPosition().toShortString());
        }
    }

    /** Damage of one hit with {@code stack}: weapon damage × scale, ×1.5 when volatile, ×1.5 for a hushed strike. */
    public static float damageFor(EchoEntity echo, ItemStack stack, boolean unaware) {
        float damage = (float) (weaponDamage(stack) * CommonConfig.ECHO_GUARD_DAMAGE_SCALE.get());
        Temper temper = EchoGrafts.active(echo);
        if (temper == Temper.VOLATILE || (temper == Temper.HUSHED && unaware)) {
            damage *= GRAFT_BONUS;
        }
        return damage;
    }

    /** Attack damage of {@code stack} in a player's main hand (base 1 included), or 0 for no melee weapon. */
    public static double weaponDamage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double total = modifiers.compute(Attributes.ATTACK_DAMAGE, 1.0D, EquipmentSlot.MAINHAND);
        return total > 1.0D ? total : 0.0D;
    }

    /** The main inventory slot of the hardest-hitting melee weapon, or -1. */
    public static int bestWeapon(EchoEntity echo) {
        EchoInventory inventory = echo.inventory();
        int best = -1;
        double bestDamage = 0.0D;
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            double damage = weaponDamage(inventory.getItem(i));
            if (damage > bestDamage) {
                bestDamage = damage;
                best = i;
            }
        }
        return best;
    }

    /** Puts the weapon in the selected hotbar slot so it shows in the echo's hand. */
    private void wield(EchoEntity echo, int slot) {
        int selected = echo.selectedSlot();
        if (slot == selected) {
            return;
        }
        EchoInventory inventory = echo.inventory();
        ItemStack held = inventory.getItem(selected);
        inventory.setItem(selected, inventory.getItem(slot));
        inventory.setItem(slot, held);
    }

    private static void awardOwner(ServerLevel level, EchoEntity echo) {
        if (echo.ownerId() == null || !(level.getServer().getPlayerList().getPlayer(echo.ownerId()) instanceof ServerPlayer owner)) {
            return;
        }
        AdvancementHolder advancement = level.getServer().getAdvancements().get(ADVANCEMENT);
        if (advancement != null) {
            owner.getAdvancements().award(advancement, "guarded");
        }
    }

    // ---- targets ----

    /** A mob hurt the guard: it turns on it, when it is a valid foe near the post. Returns true when it did. */
    boolean onAttacked(ServerLevel level, EchoEntity echo, LivingEntity attacker) {
        BlockPos anchor = this.job.workAnchor == null ? echo.blockPosition() : this.job.workAnchor;
        if (!canFight(echo, attacker) || !this.inLeash(anchor, attacker.position())) {
            return false;
        }
        this.target = attacker;
        this.repathTicks = 10;
        return true;
    }

    private boolean inLeash(BlockPos anchor, Vec3 pos) {
        double leash = this.radius() + LEASH_EXTRA;
        return Vec3.atBottomCenterOf(anchor).distanceToSqr(pos) <= leash * leash;
    }

    private @Nullable LivingEntity findFoe(ServerLevel level, EchoEntity echo, BlockPos anchor) {
        int r = this.radius();
        AABB box = new AABB(anchor).inflate(r, 4.0D, r);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> canFight(echo, entity) && echo.hasLineOfSight(entity));
        if (found.isEmpty()) {
            return null;
        }
        // Mobs that hunt the owner or the echo first, then the nearest.
        found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(echo) - (threatens(echo, entity) ? 1000.0D : 0.0D)));
        return found.get(0);
    }

    private static boolean threatens(EchoEntity echo, LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        return target == echo || (target instanceof Player player && echo.isOwnedBy(player.getUUID()));
    }

    /**
     * The safety rule: only living hostile mobs. Never players, villagers, animals, other echoes, anything tamed or
     * owned, creepers (no terrain damage from echo work), bosses, or neutral mobs that are not angry at the owner or
     * the echo.
     */
    public static boolean canFight(EchoEntity echo, Entity entity) {
        if (!(entity instanceof Mob mob) || !(entity instanceof Enemy) || !mob.isAlive() || mob.isRemoved() || mob.isInvulnerable()) {
            return false;
        }
        if (mob.level() != echo.level() || mob instanceof Creeper || mob instanceof ScarEntity || mob.typeHolder().is(Tags.EntityTypes.BOSSES)) {
            return false;
        }
        if (mob instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
            return false;
        }
        if (mob instanceof NeutralMob) {
            return threatens(echo, mob);
        }
        return true;
    }
}
