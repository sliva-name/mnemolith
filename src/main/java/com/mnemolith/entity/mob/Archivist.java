package com.mnemolith.entity.mob;

import com.mnemolith.entity.ai.StunGoal;
import com.mnemolith.entity.ai.BaitGoal;
import com.mnemolith.entity.ai.FleeGoal;
import com.mnemolith.entity.ai.StalkGoal;
import org.jspecify.annotations.Nullable;
import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.entity.MemoryMob;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobTuning;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.world.LoadedChunkMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/** Stalks an open container or a held slip, takes one, then runs for a louder chunk. */
public class Archivist extends MemoryMob {
    private int stealCooldown;
    private int fleeTicks;
    private int stunTicks;
    private @Nullable ServerPlayer interest;
    private @Nullable ItemEntity dropped;
    private ItemStack carried = ItemStack.EMPTY;
    // Same-tick cache for nearestBait(): BaitGoal can ask twice in one AI step (continue check, then start check).
    private int baitTick = Integer.MIN_VALUE;
    private @Nullable ItemEntity bait;
    /** Stage 3: a working echo it is sneaking up on, and what it took from one (always dropped on death, saved). */
    private com.mnemolith.entity.echo.@Nullable EchoEntity echoTarget;
    private ItemStack echoLoot = ItemStack.EMPTY;

    public Archivist(EntityType<? extends Archivist> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new StunGoal(this));
        this.goalSelector.addGoal(2, new BaitGoal(this));
        this.goalSelector.addGoal(3, new FleeGoal(this));
        this.goalSelector.addGoal(4, new StalkGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        this.applyAttackDamage(MobTuning.archivistDamage());
        return result;
    }

    public boolean isStunned() {
        return this.stunTicks > 0;
    }

    public boolean isFleeing() {
        return this.fleeTicks > 0;
    }

    public boolean stealReady() {
        return this.stealCooldown <= 0;
    }

    public @Nullable ServerPlayer interest() {
        return this.interest;
    }

    public void loseInterest() {
        this.interest = null;
    }

    public com.mnemolith.entity.echo.@Nullable EchoEntity echoTarget() {
        return this.echoTarget;
    }

    public ItemStack echoLoot() {
        return this.echoLoot;
    }

    /** A stack an archivist may take from an echo: never tools, weapons, armor or anything with durability. */
    public static boolean stealableFromEcho(ItemStack stack) {
        return !stack.isEmpty() && !stack.isDamageableItem() && !stack.has(net.minecraft.core.component.DataComponents.TOOL)
                && !stack.has(net.minecraft.core.component.DataComponents.WEAPON) && !stack.has(net.minecraft.core.component.DataComponents.EQUIPPABLE);
    }

    /** The main slot it would steal from: the most valuable slip first, then the largest stack. -1 when nothing fits. */
    public static int echoStealSlot(com.mnemolith.entity.echo.EchoInventory inventory) {
        int best = -1;
        long bestScore = -1;
        for (int slot = 0; slot < com.mnemolith.entity.echo.EchoInventory.MAIN; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stealableFromEcho(stack)) {
                continue;
            }
            long score = (long) ImprintSlips.weight(stack) * 1000L + stack.getCount();
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private boolean canTargetEcho(com.mnemolith.entity.echo.EchoEntity echo) {
        return echo.isAlive() && !echo.isRemoved() && echo.level() == this.level() && echo.job().isWorking() && !echo.isReplaying()
                && this.distanceToSqr(echo) <= MobTuning.INTEREST_RANGE * MobTuning.INTEREST_RANGE * 1.5D && echoStealSlot(echo.inventory()) >= 0;
    }

    /**
     * Takes up to {@code echoArchivistStealMax} items of one stack from a working echo. The loot stays with the
     * archivist (saved) and always drops when it dies, so nothing is lost; players are never robbed this way.
     */
    public boolean stealFromEcho(ServerLevel level, com.mnemolith.entity.echo.EchoEntity echo) {
        this.echoTarget = null;
        if (!this.echoLoot.isEmpty() || this.stunTicks > 0 || !com.mnemolith.config.CommonConfig.ECHO_ARCHIVIST_STEAL.get()) {
            return false;
        }
        int slot = echoStealSlot(echo.inventory());
        if (slot < 0) {
            return false;
        }
        ItemStack stack = echo.inventory().getItem(slot);
        ItemStack stolen = echo.inventory().removeItem(slot, Math.min(stack.getCount(), com.mnemolith.config.CommonConfig.ECHO_ARCHIVIST_STEAL_MAX.get()));
        if (stolen.isEmpty()) {
            return false;
        }
        this.echoLoot = stolen;
        this.setPersistenceRequired();
        this.stealCooldown = MobTuning.stealCooldown();
        this.fleeTicks = 80;
        this.dropped = null;
        this.setAction(MobActions.FLEE);
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        BlockPos at = echo.blockPosition();
        level.playSound(null, at, ModSounds.ARCHIVIST_STEAL.get(), SoundSource.NEUTRAL, 1.0F, 1.1F);
        MemoryFx.mob(level, ModParticles.ARCHIVIST_SNATCH.get(), echo.getX(), echo.getY() + 1.2D, echo.getZ(), 14);
        MemoryFx.mob(level, ModParticles.ARCHIVIST_SNATCH.get(), this.getX(), this.getY() + 1.0D, this.getZ(), 8);
        String detail = com.mnemolith.echo.job.JobStatus.missingDetail(java.util.Map.of(stolen.getItem(), stolen.getCount()));
        echo.job().notice(com.mnemolith.echo.job.JobStatus.of(com.mnemolith.echo.job.JobStatus.Kind.STOLEN, detail), 120);
        if (echo.ownerId() != null && level.getServer().getPlayerList().getPlayer(echo.ownerId()) instanceof ServerPlayer owner
                && owner.distanceToSqr(echo) <= 64.0D * 64.0D) {
            owner.sendOverlayMessage(Component.translatable("mnemolith.message.echo_stolen", stolen.getCount(), stolen.getHoverName()));
        }
        Mnemolith.LOGGER.info("Mnemolith archivist stole from echo owner={} item={} count={} at {}", echo.ownerName(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stolen.getItem()), stolen.getCount(), at.toShortString());
        return true;
    }

    public @Nullable ItemEntity dropped() {
        return this.dropped;
    }

    /** Eating bait: stunned for five seconds and forgets both the player and any dropped slip. */
    public void stunByBait() {
        this.stunTicks = 100;
        this.interest = null;
        this.dropped = null;
        this.setAction(MobActions.IDLE);
    }

    public void noticeMenu(ServerPlayer player, AbstractContainerMenu menu) {
        if (!MobTuning.archivistEnabled() || this.stunTicks > 0) {
            return;
        }
        this.interest = player;
        if (this.distanceToSqr(player) <= MobTuning.SNATCH_RANGE_SQR) {
            this.snatchMenu(player, menu, false);
        }
    }

    public void noticeDrop(ItemEntity entity) {
        if (!MobTuning.archivistEnabled() || this.stunTicks > 0) {
            return;
        }
        ItemStack stack = entity.getItem();
        if (ImprintSlips.isSlip(stack) || stack.getItem() == ModItems.ARCHIVIST_BAIT.get()) {
            this.dropped = entity;
        }
    }

    public boolean snatch(ServerLevel level, Container container, @Nullable ServerPlayer player, boolean ignoreCooldown) {
        if (this.stunTicks > 0) {
            return false;
        }
        if (!ignoreCooldown && (!MobTuning.archivistEnabled() || this.stealCooldown > 0)) {
            return false;
        }
        ItemStack stolen = takeSlip(container);
        if (stolen.isEmpty() && player != null) {
            stolen = takeSlip(player.getInventory());
        }
        if (stolen.isEmpty()) {
            return false;
        }
        this.finishSteal(level, player, stolen);
        return true;
    }

    public boolean snatchMenu(ServerPlayer player, AbstractContainerMenu menu, boolean ignoreCooldown) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (this.stunTicks > 0) {
            return false;
        }
        if (!ignoreCooldown && (!MobTuning.archivistEnabled() || this.stealCooldown > 0)) {
            return false;
        }
        ItemStack stolen = takeSlip(menu);
        if (stolen.isEmpty()) {
            stolen = takeSlip(player.getInventory());
        }
        if (stolen.isEmpty()) {
            return false;
        }
        this.finishSteal(level, player, stolen);
        return true;
    }

    public void finishSteal(ServerLevel level, @Nullable ServerPlayer player, ItemStack stolen) {
        this.carried = stolen;
        this.stealCooldown = MobTuning.stealCooldown();
        this.fleeTicks = 80;
        this.dropped = null;
        this.setAction(MobActions.FLEE);
        ImprintCast cast = stolen.get(ModDataComponents.IMPRINT_CAST.get());
        String tag = cast == null ? "blank" : cast.tag().getSerializedName();
        BlockPos pos = this.blockPosition();
        Mnemolith.LOGGER.info("Mnemolith archivist stole=true tag={} at {},{},{}", tag, pos.getX(), pos.getY(), pos.getZ());
        level.playSound(null, pos, ModSounds.ARCHIVIST_STEAL.get(), SoundSource.NEUTRAL, 1.0F, 1.1F);
        MemoryFx.mob(level, ModParticles.ARCHIVIST_SNATCH.get(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 10);
        if (player != null && cast != null) {
            player.sendOverlayMessage(Component.translatable("mnemolith.message.stolen", Component.translatable(cast.tag().translationKey())));
        }
    }

    private static ItemStack takeSlip(@Nullable Container container) {
        if (container == null) {
            return ItemStack.EMPTY;
        }
        int best = -1;
        int bestWeight = -1;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            int weight = ImprintSlips.weight(stack);
            if (weight > bestWeight) {
                bestWeight = weight;
                best = slot;
            }
        }
        if (best < 0) {
            return ItemStack.EMPTY;
        }
        return container.removeItem(best, 1);
    }

    /** Prefers the open container so both viewers lose the same slip, then the player's own inventory. */
    private static ItemStack takeSlip(AbstractContainerMenu menu) {
        ItemStack shared = takeFromMenu(menu, false);
        if (!shared.isEmpty()) {
            return shared;
        }
        return takeFromMenu(menu, true);
    }

    private static ItemStack takeFromMenu(AbstractContainerMenu menu, boolean playerInventory) {
        Slot best = null;
        int bestWeight = -1;
        for (Slot slot : menu.slots) {
            if ((slot.container instanceof Inventory) != playerInventory) {
                continue;
            }
            int weight = ImprintSlips.weight(slot.getItem());
            if (weight > bestWeight) {
                bestWeight = weight;
                best = slot;
            }
        }
        if (best == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stolen = best.remove(1);
        best.setChanged();
        return stolen;
    }

    public @Nullable ItemEntity nearestBait() {
        if (this.baitTick != this.tickCount) {
            this.baitTick = this.tickCount;
            this.bait = this.findNearestBait();
        }
        return this.bait;
    }

    private @Nullable ItemEntity findNearestBait() {
        AABB box = this.getBoundingBox().inflate(MobTuning.BAIT_RANGE);
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity entity : this.level().getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().getItem() == ModItems.ARCHIVIST_BAIT.get())) {
            double dist = this.distanceToSqr(entity);
            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.stealCooldown > 0) {
            this.stealCooldown--;
        }
        if (this.stunTicks > 0) {
            this.stunTicks--;
            this.getNavigation().stop();
            this.setAction(MobActions.IDLE);
            if (this.stunTicks % 10 == 0) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 1.0D, this.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
            }
            return;
        }
        if (this.fleeTicks > 0) {
            this.fleeTicks--;
        }
        if (MobTuning.sensorDue(this.tickCount, false) && LoadedChunkMemory.resonatorNearby(level, this.blockPosition(), MobTuning.RESONATOR_RANGE)) {
            this.stunTicks = 80;
            this.fleeTicks = 0;
            this.interest = null;
            this.setAction(MobActions.IDLE);
            level.playSound(null, this.blockPosition(), ModSounds.ARCHIVIST_STEAL.get(), SoundSource.NEUTRAL, 0.6F, 0.5F);
            return;
        }
        if (this.tickCount % 20 != 0 || !MobTuning.archivistEnabled()) {
            return;
        }
        Player nearest = level.getNearestPlayer(this, MobTuning.INTEREST_RANGE);
        if (nearest instanceof ServerPlayer player) {
            ItemStack hand = player.getMainHandItem();
            if (ImprintSlips.weight(hand) >= MobTuning.HIGH_VALUE_WEIGHT) {
                this.interest = player;
            }
            if (this.interest == player && this.distanceToSqr(player) <= MobTuning.SNATCH_RANGE_SQR && this.stealCooldown <= 0) {
                if (player.containerMenu != player.inventoryMenu) {
                    this.snatchMenu(player, player.containerMenu, false);
                } else if (ImprintSlips.isSlip(hand) || ImprintSlips.isSlip(player.getOffhandItem())) {
                    this.snatch(level, player.getInventory(), player, false);
                }
            }
        }
        if (this.echoTarget != null && !this.canTargetEcho(this.echoTarget)) {
            this.echoTarget = null;
        }
        if (this.echoTarget == null && this.echoLoot.isEmpty() && this.stealCooldown <= 0 && this.interest == null
                && com.mnemolith.config.CommonConfig.ECHO_ARCHIVIST_STEAL.get()) {
            double bestDistance = Double.MAX_VALUE;
            for (com.mnemolith.entity.echo.EchoEntity echo : level.getEntitiesOfClass(com.mnemolith.entity.echo.EchoEntity.class,
                    this.getBoundingBox().inflate(MobTuning.INTEREST_RANGE), this::canTargetEcho)) {
                double distance = this.distanceToSqr(echo);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    this.echoTarget = echo;
                }
            }
        }
        if (this.dropped == null || !this.dropped.isAlive()) {
            AABB box = this.getBoundingBox().inflate(8.0D);
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box, item -> ImprintSlips.isSlip(item.getItem()))) {
                this.dropped = entity;
                break;
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!this.carried.isEmpty() && this.random.nextFloat() < 0.5F) {
            this.spawnAtLocation(level, this.carried.copy());
        }
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        if (!this.echoLoot.isEmpty()) {
            // What it took from an echo always comes back, whatever the loot rules say.
            this.spawnAtLocation(level, this.echoLoot);
            this.echoLoot = ItemStack.EMPTY;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.DISCARDED && !this.echoLoot.isEmpty() && this.level() instanceof ServerLevel level) {
            this.spawnAtLocation(level, this.echoLoot);
            this.echoLoot = ItemStack.EMPTY;
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (!this.echoLoot.isEmpty()) {
            output.store("echo_loot", ItemStack.CODEC, this.echoLoot);
        }
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        this.echoLoot = input.read("echo_loot", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.ARCHIVIST_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ARCHIVIST_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ARCHIVIST_DEATH.get();
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

}
