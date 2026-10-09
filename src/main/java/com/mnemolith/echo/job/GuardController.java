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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Guard job: hold the post (the work anchor) and fight hostile mobs near it with the best melee weapon in the
 * inventory. Never targets players, villagers, animals, tamed or owned mobs, creepers, bosses or neutral mobs that are
 * not angry at the owner or the echo. Each landed hit costs the weapon one durability point.
 *
 * <p>Stage 2 ({@code docs/design/echo-guard-2.md}): a bow or crossbow with arrows from the inventory shoots foes out of
 * reach after a line-of-fire check, a shield in the off hand is raised while there is a foe, and in escort mode
 * ({@link EchoJob.Mode#ESCORT}) the post follows the owner.
 */
public final class GuardController {
    /** How far past the post radius a guard follows a foe before it turns back. */
    static final int LEASH_EXTRA = 3;
    /** Reach of a guard's hit, squared, measured between the two bodies' centres (a player reaches about 3). */
    static final float GRAFT_BONUS = 1.5F;
    static final Identifier ADVANCEMENT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "echo_guard");
    /** A guard with a melee weapon shoots only foes farther than this (squared: 3.5 blocks). */
    static final double MIN_SHOT_SQR = 3.5D * 3.5D;
    static final double MAX_SHOT = 16.0D;
    static final float ARROW_VELOCITY = 1.6F;
    static final float ARROW_SPREAD = 2.0F;
    /** Ticks the bow is drawn (and the shield down) before a shot. */
    static final int DRAW_TICKS = 10;
    /** A pure archer drops a foe after this many ticks without a clear line of fire. */
    static final int BLOCKED_GIVE_UP = 40;
    /** An escort follows its owner only within this distance; farther, it waits at the last spot. */
    static final double ESCORT_RANGE = 24.0D;
    /** An escort stays within this distance of its owner (squared: 3 blocks). */
    static final double ESCORT_NEAR_SQR = 9.0D;
    /** How long (ticks) and how far a shooter out of the leash keeps the guard's shield turned to it. */
    static final int THREAT_TICKS = 60;
    static final double THREAT_RANGE = 24.0D;

    private final EchoJob job;
    GuardLesson taught = GuardLesson.NONE;
    int defeated;
    @Nullable LivingEntity target;
    int cooldown;
    int scanTicks;
    int repathTicks;
    /** Hits that landed since the job started (QA). */
    int landed;
    /** Arrows shot and attacks the shield blocked since the job started (QA). */
    int shots;
    int blocked;
    int lineBlockedTicks;
    /** Ticks the shield stays down after an axe disabled it. */
    int shieldDownTicks;
    boolean raised;
    boolean drawing;
    /** Escort: false while the owner is away (the post stays where they were last seen). */
    boolean following = true;
    /**
     * Stage 3: a mob that hurt the guard (or hit its shield) from outside the leash, such as a skeleton shooting from
     * afar. The guard stays on post but faces it with its shield up, and shoots back when it can.
     */
    @Nullable LivingEntity threat;
    int threatTicks;
    /** Where the walk back to the post is heading (an escort re-paths when its owner moves). */
    @Nullable BlockPos postGoal;

    GuardController(EchoJob job) {
        this.job = job;
    }

    void resetSearch() {
        this.target = null;
        this.threat = null;
        this.threatTicks = 0;
        this.scanTicks = 0;
        this.repathTicks = 0;
        this.lineBlockedTicks = 0;
        this.postGoal = null;
        this.following = true;
    }

    public int radius() {
        if (this.job.escorting()) {
            return CommonConfig.ECHO_GUARD_ESCORT_RADIUS.get();
        }
        return Math.max(2, Math.min(this.job.radius(), CommonConfig.ECHO_GUARD_RADIUS.get()));
    }

    // ---- tick ----

    void tick(ServerLevel level, EchoEntity echo) {
        if (this.job.escorting()) {
            this.following = this.followOwner(level, echo);
        }
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
        if (this.shieldDownTicks > 0) {
            this.shieldDownTicks--;
        }
        int weapon = bestWeapon(echo);
        boolean bow = canShoot(echo);
        if (weapon < 0 && !bow) {
            boolean used = this.landed > 0 || this.shots > 0;
            // A bow with no arrows left reads «нет стрел»; nothing at all reads «нет оружия» or «инструмент сломался».
            String what = bestBow(echo) >= 0 ? "arrows" : "weapon";
            this.job.halt(echo, JobStatus.of(used && what.equals("weapon") ? JobStatus.Kind.TOOL_BROKE : JobStatus.Kind.NO_TOOL, what));
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
            this.lineBlockedTicks = 0;
            LivingEntity threat = this.currentThreat(echo);
            this.holdPost(level, echo, anchor);
            if (threat == null) {
                this.lower(echo);
                return;
            }
            // Under fire from beyond the leash: stay on post, face the shooter, shoot back or shield up.
            echo.lookAt(threat.getEyePosition());
            if (bow && echo.distanceToSqr(threat) <= MAX_SHOT * MAX_SHOT && clearShot(level, echo, threat)) {
                this.ranged(level, echo, threat);
                return;
            }
            this.cancelDraw(echo);
            this.raise(echo);
            return;
        }
        this.job.setStatus(new JobStatus(JobStatus.Kind.GUARD_FIGHT, BuiltInRegistries.ENTITY_TYPE.getKey(foe.getType()).toString(), this.defeated, 0));
        this.fight(level, echo, foe, weapon, bow);
    }

    /** Escort: the post is the owner's spot while they are near. Returns false when the owner is away. */
    private boolean followOwner(ServerLevel level, EchoEntity echo) {
        ServerPlayer owner = echo.onlineOwner();
        if (owner == null || owner.level() != level || owner.isSpectator() || !owner.isAlive()
                || owner.distanceToSqr(echo) > ESCORT_RANGE * ESCORT_RANGE) {
            return false;
        }
        this.job.workAnchor = owner.blockPosition();
        return true;
    }

    private void holdPost(ServerLevel level, EchoEntity echo, BlockPos anchor) {
        boolean escort = this.job.escorting();
        JobStatus.Kind kind = !escort ? JobStatus.Kind.GUARD_POST : this.following ? JobStatus.Kind.GUARD_ESCORT : JobStatus.Kind.GUARD_WAITING;
        this.job.setStatus(JobStatus.of(kind, this.defeated, 0));
        double near = escort ? ESCORT_NEAR_SQR : 2.0D;
        double arrive = escort ? 4.0D : 1.0D;
        if (echo.blockPosition().distSqr(anchor) <= near) {
            if (this.job.mover.active()) {
                this.job.mover.stop(level, echo);
            }
            echo.setMoveTarget(null);
            this.postGoal = null;
            return;
        }
        if (this.job.mover.active() && this.postGoal != null && this.postGoal.distSqr(anchor) > 4.0D) {
            // The owner moved on: head for where they are now.
            this.job.mover.stop(level, echo);
        }
        if (!this.job.mover.active()) {
            this.postGoal = anchor;
            this.job.mover.start(level, echo, new EchoNav.Goal() {
                @Override
                public boolean reached(BlockPos feet) {
                    return feet.distSqr(anchor) <= arrive;
                }

                @Override
                public double estimate(BlockPos feet) {
                    return Math.sqrt(feet.distSqr(anchor));
                }
            });
        }
        if (this.job.mover.tick(level, echo) == EchoMover.Result.FAILED) {
            this.postGoal = null;
            if (!escort) {
                // The post is out of reach (blocked in): stand where it is and keep watching.
                this.job.workAnchor = echo.blockPosition();
            }
        }
    }

    private void fight(ServerLevel level, EchoEntity echo, LivingEntity foe, int weapon, boolean bow) {
        echo.lookAt(foe.getEyePosition());
        double distance = echo.distanceToSqr(foe);
        double reach = CommonConfig.ECHO_GUARD_REACH.get();
        boolean inReach = distance <= reach * reach && Math.abs(foe.getY() - echo.getY()) < 2.0D;
        if (weapon >= 0 && inReach) {
            this.cancelDraw(echo);
            if (this.job.mover.active()) {
                this.job.mover.stop(level, echo);
            }
            echo.setMoveTarget(null);
            // The shield comes down just before the swing and goes back up after it.
            if (this.cooldown <= 3) {
                this.lowerShield(echo);
            } else {
                this.raise(echo);
            }
            if (this.cooldown <= 0) {
                this.strike(level, echo, foe, weapon);
            }
            return;
        }
        boolean wantsShot = bow && distance <= MAX_SHOT * MAX_SHOT && (weapon < 0 || distance > MIN_SHOT_SQR);
        if (wantsShot) {
            if (clearShot(level, echo, foe)) {
                this.lineBlockedTicks = 0;
                if (this.job.mover.active()) {
                    this.job.mover.stop(level, echo);
                }
                echo.setMoveTarget(null);
                this.ranged(level, echo, foe);
                return;
            }
            this.cancelDraw(echo);
            if (weapon < 0 && echo.hasLineOfSight(foe)) {
                // A pure archer with someone in the way: hold fire and wait, then let the foe go.
                this.raise(echo);
                if (this.job.mover.active()) {
                    this.job.mover.stop(level, echo);
                }
                echo.setMoveTarget(null);
                if (++this.lineBlockedTicks >= BLOCKED_GIVE_UP) {
                    this.target = null;
                    this.lineBlockedTicks = 0;
                }
                return;
            }
        } else {
            this.cancelDraw(echo);
        }
        this.raise(echo);
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

    // ---- shooting ----

    /** Draws for {@link #DRAW_TICKS}, then shoots one arrow from the inventory. */
    private void ranged(ServerLevel level, EchoEntity echo, LivingEntity foe) {
        int bowSlot = bestBow(echo);
        if (bowSlot < 0 || arrowSlot(echo) < 0) {
            this.cancelDraw(echo);
            return;
        }
        if (!this.drawing) {
            if (this.cooldown > DRAW_TICKS) {
                this.raise(echo);
                return;
            }
            this.lowerShield(echo);
            this.wield(echo, bowSlot);
            echo.startUsingItem(InteractionHand.MAIN_HAND);
            this.drawing = true;
        }
        if (this.cooldown > 0) {
            return;
        }
        this.shoot(level, echo, foe);
        this.cancelDraw(echo);
    }

    private void shoot(ServerLevel level, EchoEntity echo, LivingEntity foe) {
        ItemStack bow = echo.getItemBySlot(EquipmentSlot.MAINHAND);
        int ammoSlot = arrowSlot(echo);
        if (!isBow(bow) || ammoSlot < 0) {
            return;
        }
        EchoInventory inventory = echo.inventory();
        ItemStack ammo = inventory.getItem(ammoSlot);
        ItemStack one = ammo.copyWithCount(1);
        boolean unaware = !(foe instanceof Mob mob) || mob.getTarget() != echo;
        Temper temper = EchoGrafts.active(echo);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(echo, one, 1.0F, bow);
        if (bow.getItem() instanceof ProjectileWeaponItem weaponItem) {
            arrow = weaponItem.customArrow(arrow, one, bow);
        }
        arrow.setBaseDamage(arrowDamage(echo, unaware));
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        double xd = foe.getX() - echo.getX();
        double yd = foe.getY(1.0D / 3.0D) - arrow.getY();
        double zd = foe.getZ() - echo.getZ();
        double flat = Math.sqrt(xd * xd + zd * zd);
        Projectile.spawnProjectileUsingShoot(arrow, level, one, xd, yd + flat * 0.2D, zd, ARROW_VELOCITY, ARROW_SPREAD);
        // No burning arrows from echo work (Flame would light TNT or a mob near the post).
        arrow.clearFire();
        ammo.shrink(1);
        inventory.setItem(ammoSlot, ammo.isEmpty() ? ItemStack.EMPTY : ammo);
        this.cooldown = shotTicks(bow);
        this.shots++;
        level.playSound(null, echo.blockPosition(), bow.getItem() instanceof CrossbowItem ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT,
                SoundSource.NEUTRAL, 0.8F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (temper == Temper.VOLATILE || (temper == Temper.HUSHED && unaware)) {
            EchoGrafts.spend(echo, 1);
        }
        if (bow.isDamageableItem()) {
            bow.hurtAndBreak(1, level, echo, item -> level.playSound(null, echo.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 1.0F));
        }
    }

    /** Base damage of a guard's arrow: 2 × scale, ×1.5 when volatile or a hushed shot at an unaware mob. */
    public static double arrowDamage(EchoEntity echo, boolean unaware) {
        double damage = 2.0D * CommonConfig.ECHO_GUARD_ARROW_DAMAGE_SCALE.get();
        Temper temper = EchoGrafts.active(echo);
        if (temper == Temper.VOLATILE || (temper == Temper.HUSHED && unaware)) {
            damage *= GRAFT_BONUS;
        }
        return damage;
    }

    /** Ticks between shots with {@code bow}: a crossbow takes 1.4 times as long. */
    public static int shotTicks(ItemStack bow) {
        int ticks = CommonConfig.ECHO_GUARD_SHOT_TICKS.get();
        return bow.getItem() instanceof CrossbowItem ? Math.round(ticks * 1.4F) : ticks;
    }

    /**
     * The friendly-fire rule: blocks must not stand between the guard's eyes and the foe, and no protected living
     * entity (anything {@link #canFight} refuses: the owner, players, villagers, animals, pets, creepers, armor stands,
     * other echoes) may touch the line of fire, with 0.4 blocks of margin.
     */
    public static boolean clearShot(ServerLevel level, EchoEntity echo, LivingEntity foe) {
        if (!echo.hasLineOfSight(foe)) {
            return false;
        }
        Vec3 from = echo.getEyePosition();
        Vec3 to = foe.position().add(0.0D, foe.getBbHeight() * 0.5D, 0.0D);
        AABB sweep = new AABB(from, to).inflate(1.0D);
        for (Entity entity : level.getEntities(echo, sweep, entity -> entity != foe && entity instanceof LivingEntity && !canFight(echo, entity))) {
            AABB box = entity.getBoundingBox().inflate(0.4D);
            if (box.contains(from) || box.clip(from, to).isPresent()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBow(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    /** The main inventory slot of a bow or crossbow (a bow first), or -1. */
    public static int bestBow(EchoEntity echo) {
        EchoInventory inventory = echo.inventory();
        int crossbow = -1;
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof BowItem) {
                return i;
            }
            if (crossbow < 0 && stack.getItem() instanceof CrossbowItem) {
                crossbow = i;
            }
        }
        return crossbow;
    }

    /** The main inventory slot of the first arrows (plain, tipped or spectral), or -1. */
    public static int arrowSlot(EchoEntity echo) {
        EchoInventory inventory = echo.inventory();
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            if (inventory.getItem(i).getItem() instanceof ArrowItem) {
                return i;
            }
        }
        return -1;
    }

    /** It has a bow or crossbow and at least one arrow. */
    public static boolean canShoot(EchoEntity echo) {
        return bestBow(echo) >= 0 && arrowSlot(echo) >= 0;
    }

    /** One of this guard's arrows killed a mob: it counts like a melee kill. */
    void onArrowKill(ServerLevel level, EchoEntity echo, LivingEntity victim) {
        this.defeated++;
        if (this.target == victim) {
            this.target = null;
        }
        awardOwner(level, echo);
        Mnemolith.LOGGER.debug("Mnemolith echo guard shot down owner={} foe={} total={}", echo.ownerName(),
                BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()), this.defeated);
    }

    // ---- shield ----

    private static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.BLOCKS_ATTACKS);
    }

    /** Raises the shield (vanilla blocking: the echo uses it from the off hand). Moves one there first if needed. */
    private void raise(EchoEntity echo) {
        if (!CommonConfig.ECHO_GUARD_SHIELDS.get() || this.shieldDownTicks > 0 || this.drawing) {
            return;
        }
        if (this.raised && !echo.isUsingItem()) {
            // Someone else lowered it: an axe disabled it, or it broke.
            this.raised = false;
            this.shieldDownTicks = CommonConfig.ECHO_GUARD_SHIELD_COOLDOWN.get();
            return;
        }
        if (this.raised) {
            return;
        }
        EchoInventory inventory = echo.inventory();
        if (!isShield(inventory.getItem(EchoInventory.OFFHAND))) {
            int slot = -1;
            for (int i = 0; i < EchoInventory.MAIN; i++) {
                if (isShield(inventory.getItem(i))) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0 || slot == echo.selectedSlot()) {
                return;
            }
            ItemStack off = inventory.getItem(EchoInventory.OFFHAND);
            inventory.setItem(EchoInventory.OFFHAND, inventory.getItem(slot));
            inventory.setItem(slot, off);
        }
        echo.startUsingItem(InteractionHand.OFF_HAND);
        this.raised = echo.isUsingItem();
    }

    private void lowerShield(EchoEntity echo) {
        if (this.raised) {
            if (echo.isUsingItem() && echo.getUsedItemHand() == InteractionHand.OFF_HAND) {
                echo.stopUsingItem();
            }
            this.raised = false;
        }
    }

    private void cancelDraw(EchoEntity echo) {
        if (this.drawing) {
            if (echo.isUsingItem() && echo.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                echo.stopUsingItem();
            }
            this.drawing = false;
        }
    }

    /** Shield down and bow undrawn (the job stopped, an order took over, or nothing to fight). */
    void lower(EchoEntity echo) {
        this.lowerShield(echo);
        this.cancelDraw(echo);
    }

    /** The shield the guard holds up, or null. */
    @Nullable ItemStack raisedShield(EchoEntity echo) {
        ItemStack off = echo.getItemBySlot(EquipmentSlot.OFFHAND);
        return this.raised && echo.isUsingItem() && isShield(off) ? off : null;
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
        // No extra shove: the hit's own knockback (0.4, like a player's) is enough, and a bigger one kept zombies from
        // ever reaching the guard.
        this.landed++;
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
        if (!canFight(echo, attacker)) {
            return false;
        }
        if (!this.inLeash(anchor, attacker.position())) {
            if (attacker.distanceToSqr(echo) <= THREAT_RANGE * THREAT_RANGE) {
                this.threat = attacker;
                this.threatTicks = THREAT_TICKS;
            }
            return false;
        }
        this.target = attacker;
        this.repathTicks = 10;
        return true;
    }

    /**
     * A guard hit or shot {@code mob}: it turns on the guard ({@code echoGuardProvokes}). Vanilla's hurt-by goal does
     * the same for most mobs; this also covers mobs without one. Runs on the hit, never as a scan.
     */
    public static void provoke(EchoEntity echo, LivingEntity victim) {
        if (!CommonConfig.ECHO_GUARD_PROVOKES.get() || !(victim instanceof Mob mob) || !mob.isAlive() || mob.getTarget() == echo
                || !echo.attractsMobs() || !canFight(echo, mob)) {
            return;
        }
        mob.setTarget(echo);
        Mnemolith.LOGGER.debug("Mnemolith echo guard provoked owner={} mob={} at {}", echo.ownerName(),
                BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), mob.blockPosition().toShortString());
    }

    /** An axe (or a warden) knocked the shield out of the guard's hands for {@code ticks}. */
    void onShieldDisabled(int ticks) {
        this.raised = false;
        this.shieldDownTicks = Math.max(this.shieldDownTicks, ticks);
    }

    /** The shooter out of the leash the guard still turns its shield to, or null once it is gone, quiet or far. */
    private @Nullable LivingEntity currentThreat(EchoEntity echo) {
        LivingEntity threat = this.threat;
        if (threat == null) {
            return null;
        }
        if (--this.threatTicks <= 0 || !canFight(echo, threat) || threat.distanceToSqr(echo) > THREAT_RANGE * THREAT_RANGE) {
            this.threat = null;
            return null;
        }
        return threat;
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
