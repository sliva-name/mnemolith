package com.mnemolith.echo.graft;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.entity.ModEffects;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * Memory grafts: an imprint slip grafted into an echo becomes its temper. Every rule of the system lives here; the
 * echo, job, possession and mob code only ask this class. Server side only (the client reads the synced temper from
 * {@link EchoEntity#graftTemper()}).
 * <p>
 * One rule holds everywhere: a graft is never silently deleted while it is worth something. Unpicked, replaced,
 * rejected by a fracture, or left behind by a dying body, a graft with at least half of one slip's charges left goes
 * back to the owner (needle) or to the chunk as an imprint (or drops as a slip where the chunk is muted). A graft worn
 * below half is spent and gone. Charges only ever come from slips.
 */
public final class EchoGrafts {
    /** How far a plunging echo may drop in one path step, and how deep a floor it may dig out under itself. */
    public static final int PLUNGE_DROP = 12;
    public static final int NORMAL_DROP = 3;
    public static final double HUSHED_SLOWDOWN = 1.35D;
    public static final double VOLATILE_SPEEDUP = 0.55D;
    public static final float VOLATILE_BLAST = 2.5F;
    /** Ticks between two charges a possessed body spends on its temper's effect. */
    public static final int POSSESSED_SPEND_TICKS = 200;
    private static final int EFFECT_TICKS = 60;

    private EchoGrafts() {}

    public static boolean enabled() {
        return CommonConfig.ECHO_GRAFTS_ENABLED.get();
    }

    /** Charges one slip of {@code temper} gives. */
    public static int slipCharge(Temper temper) {
        return Math.max(1, (int) Math.round(temper.baseCharge() * CommonConfig.ECHO_GRAFT_CHARGE_SCALE.get()));
    }

    /** Most charges a graft can hold: two slips' worth. */
    public static int capacity(Temper temper) {
        return capacity(temper, false);
    }

    /** Two slips' worth, or three on a scar-set echo. */
    public static int capacity(Temper temper, boolean scarred) {
        return slipCharge(temper) * (scarred ? 3 : 2);
    }

    public static int capacity(EchoEntity echo, Temper temper) {
        return capacity(temper, echo.scarred());
    }

    /**
     * Right-click with a scar fragment on your own echo: it becomes scar-set for good (three slips' worth of graft,
     * no graft rejection and no work stop in a fracture). Consumes the fragment; one per echo.
     */
    public static boolean scarSet(ServerPlayer player, EchoEntity echo, ItemStack fragment) {
        if (echo.scarred()) {
            player.sendSystemMessage(Component.translatable("mnemolith.graft.already_scarred"), true);
            return false;
        }
        echo.setScarred(true);
        fragment.consume(1, player);
        ServerLevel level = (ServerLevel) echo.level();
        level.playSound(null, echo.blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, echo.getX(), echo.getY() + 1.0D, echo.getZ(), 30, 0.4D, 0.8D, 0.4D, 0.05D);
        player.sendSystemMessage(Component.translatable("mnemolith.graft.scar_set"), true);
        Mnemolith.LOGGER.info("Mnemolith echo scar-set owner={}", echo.ownerName());
        return true;
    }

    /** Whether the graft still counts as a whole slip when it leaves the echo. */
    public static boolean worthReturning(EchoGraft graft) {
        return graft.charge() * 2 >= slipCharge(graft.temper());
    }

    public static @Nullable Temper active(EchoEntity echo) {
        return enabled() ? echo.graftTemper() : null;
    }

    public static boolean is(EchoEntity echo, Temper temper) {
        return active(echo) == temper;
    }

    // ---- grafting and unpicking ----

    /** Right-click with an imprint slip on your own echo. Consumes one slip on success. */
    public static boolean graft(ServerPlayer player, EchoEntity echo, ItemStack slip) {
        ServerLevel level = (ServerLevel) echo.level();
        ImprintCast cast = slip.get(ModDataComponents.IMPRINT_CAST.get());
        if (cast == null) {
            return false;
        }
        if (!enabled()) {
            player.sendSystemMessage(Component.translatable("mnemolith.graft.disabled"), true);
            return false;
        }
        Temper temper = Temper.of(cast.tag());
        if (temper == null) {
            player.sendSystemMessage(Component.translatable("mnemolith.graft.faint", Component.translatable(cast.tag().translationKey())), true);
            return false;
        }
        EchoGraft current = echo.graft();
        int charge = slipCharge(temper);
        if (current != null && current.temper() == temper) {
            int cap = capacity(echo, temper);
            if (current.charge() >= cap - charge / 2) {
                player.sendSystemMessage(Component.translatable("mnemolith.graft.full", Component.translatable(temper.key())), true);
                return false;
            }
            int next = Math.min(cap, current.charge() + charge);
            echo.setGraft(current.withCharge(next));
            player.sendSystemMessage(Component.translatable("mnemolith.graft.fed", Component.translatable(temper.key()), next, cap), true);
        } else {
            if (current != null) {
                release(level, echo, echo.blockPosition(), "replaced");
            }
            echo.setGraft(new EchoGraft(cast, charge));
            player.sendSystemMessage(Component.translatable("mnemolith.graft.taken", Component.translatable(temper.key()), charge), true);
        }
        slip.consume(1, player);
        level.playSound(null, echo.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
        burst(level, echo, temper, 18);
        Mnemolith.LOGGER.info("Mnemolith graft owner={} temper={} charge={} at {}", echo.ownerName(), temper, echo.graft() == null ? 0 : echo.graft().charge(),
                echo.blockPosition().toShortString());
        return true;
    }

    /**
     * Extraction needle on your own echo: the graft comes back out as its slip if at least half a slip's charges are
     * left, otherwise it crumbles. Costs the same durability and rest as an extraction.
     */
    public static boolean unpick(ServerPlayer player, EchoEntity echo, ItemStack needle) {
        ServerLevel level = (ServerLevel) echo.level();
        EchoGraft graft = echo.graft();
        if (graft == null) {
            player.sendSystemMessage(Component.translatable("mnemolith.graft.none"), true);
            return false;
        }
        int cooldown = CommonConfig.EXTRACTION_COOLDOWN_TICKS.get();
        if (cooldown > 0 && player.getCooldowns().isOnCooldown(needle)) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.extract_cooldown"), true);
            return false;
        }
        echo.setGraft(null);
        Temper temper = graft.temper();
        if (worthReturning(graft)) {
            ItemStack slip = ImprintSlips.of(graft.cast().toImprint());
            if (!player.getInventory().add(slip)) {
                player.drop(slip, false);
            }
            player.sendSystemMessage(Component.translatable("mnemolith.graft.unpicked", Component.translatable(temper.key())), true);
        } else {
            player.sendSystemMessage(Component.translatable("mnemolith.graft.crumbled", Component.translatable(temper.key())), true);
        }
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(needle, cooldown);
        }
        int cost = CommonConfig.EXTRACTION_DURABILITY_COST.get();
        if (cost > 0) {
            needle.hurtAndBreak(cost, level, player, item -> {});
        }
        level.playSound(null, echo.blockPosition(), com.mnemolith.audio.ModSounds.EXTRACT.get(), SoundSource.PLAYERS, 0.8F, 0.8F);
        burst(level, echo, temper, 10);
        Mnemolith.LOGGER.info("Mnemolith graft unpicked owner={} temper={} charge={} returned={}", echo.ownerName(), temper, graft.charge(), worthReturning(graft));
        return true;
    }

    /**
     * The graft leaves the echo into the world at {@code pos}: written back as its imprint when it is still worth a
     * slip (a muted chunk or disabled writes drop the slip instead), or lost when worn down. Returns what happened:
     * "chunk", "dropped" or "spent".
     */
    public static String release(ServerLevel level, EchoEntity echo, BlockPos pos, String why) {
        EchoGraft graft = echo.graft();
        echo.setGraft(null);
        return graft == null ? "spent" : releaseGraft(level, graft, echo.ownerId(), pos, why);
    }

    /** Same as {@link #release} for a graft that is not on a live echo (a possessed body that died). */
    public static String releaseGraft(ServerLevel level, EchoGraft graft, java.util.@Nullable UUID owner, BlockPos pos, String why) {
        String result;
        if (!worthReturning(graft)) {
            result = "spent";
        } else if (com.mnemolith.echo.residue.Residues.condenseGraft(level, graft, pos)) {
            // An overloaded chunk has no room to take it quietly: it condenses into a residual echo.
            result = "residue";
        } else if (ImprintWriter.write(level, pos, List.of(graft.cast().tag()), owner, false)) {
            result = "chunk";
        } else {
            ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, ImprintSlips.of(graft.cast().toImprint()));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            result = "dropped";
        }
        Mnemolith.LOGGER.info("Mnemolith graft released temper={} why={} charge={} -> {} at {}", graft.temper(), why, graft.charge(), result, pos.toShortString());
        return result;
    }

    // ---- residual echoes ----

    /**
     * Right-click with a residual shard on your own echo: the shard's memory takes as a graft worth up to two slips
     * (full from strength 4). Same temper tops the graft up; another temper replaces it (the old graft is released).
     */
    public static boolean graftShard(ServerPlayer player, EchoEntity echo, ItemStack shard) {
        ServerLevel level = (ServerLevel) echo.level();
        ImprintCast cast = shard.get(ModDataComponents.IMPRINT_CAST.get());
        if (cast == null) {
            return false;
        }
        if (!enabled()) {
            player.sendSystemMessage(Component.translatable("mnemolith.graft.disabled"), true);
            return false;
        }
        Temper temper = Temper.of(cast.tag());
        if (temper == null) {
            player.sendSystemMessage(Component.translatable("mnemolith.graft.faint", Component.translatable(cast.tag().translationKey())), true);
            return false;
        }
        int cap = capacity(echo, temper);
        int charge = com.mnemolith.echo.residue.Residues.shardCharge(temper, cast.intensity());
        EchoGraft current = echo.graft();
        if (current != null && current.temper() == temper) {
            if (current.charge() >= cap) {
                player.sendSystemMessage(Component.translatable("mnemolith.graft.full", Component.translatable(temper.key())), true);
                return false;
            }
            echo.setGraft(current.withCharge(Math.min(cap, current.charge() + charge)));
        } else {
            if (current != null) {
                release(level, echo, echo.blockPosition(), "replaced");
            }
            echo.setGraft(new EchoGraft(cast, charge));
        }
        int now = echo.graft() == null ? 0 : echo.graft().charge();
        player.sendSystemMessage(Component.translatable("mnemolith.graft.shard", Component.translatable(temper.key()), now, cap), true);
        shard.consume(1, player);
        level.playSound(null, echo.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
        burst(level, echo, temper, 28);
        Mnemolith.LOGGER.info("Mnemolith graft from shard owner={} temper={} charge={}", echo.ownerName(), temper, now);
        return true;
    }

    /** A residue feeds {@code amount} charges into an echo that already carries its temper. False when it is full. */
    public static boolean topUp(EchoEntity echo, int amount) {
        EchoGraft graft = echo.graft();
        if (graft == null || amount <= 0) {
            return false;
        }
        int cap = capacity(echo, graft.temper());
        if (graft.charge() >= cap) {
            return false;
        }
        echo.setGraft(graft.withCharge(Math.min(cap, graft.charge() + amount)));
        return true;
    }

    /**
     * A possessed body absorbs a residue: {@code cast} becomes the body's graft with {@code charge} charges (capped).
     * A graft the body had before is released at the player's feet.
     */
    public static boolean absorbIntoPossessed(ServerPlayer player, ImprintCast cast, int charge) {
        Temper temper = Temper.of(cast.tag());
        if (!enabled() || temper == null || !EchoPossession.isPossessing(player)) {
            return false;
        }
        EchoGraft before = possessedGraft(player);
        PossessionState.Data body = EchoPossession.state(player).data();
        int cap = capacity(temper, body != null && body.body().scarred());
        if (before != null && before.temper() == temper) {
            setPossessedGraft(player, before.withCharge(Math.min(cap, before.charge() + charge)));
            return true;
        }
        if (before != null && player.level() instanceof ServerLevel level) {
            releaseGraft(level, before, player.getUUID(), player.blockPosition(), "absorbed");
        }
        setPossessedGraft(player, new EchoGraft(cast, Math.min(cap, charge)));
        return true;
    }

    /** Spends {@code amount} charges of {@code echo}'s graft; an empty graft is gone and the owner is told. */
    public static void spend(EchoEntity echo, int amount) {
        EchoGraft graft = echo.graft();
        if (graft == null || amount <= 0) {
            return;
        }
        int left = graft.charge() - amount;
        if (left > 0) {
            echo.setGraft(graft.withCharge(left));
            return;
        }
        echo.setGraft(null);
        if (echo.level() instanceof ServerLevel level) {
            level.playSound(null, echo.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.NEUTRAL, 0.8F, 0.6F);
            burst(level, echo, graft.temper(), 6);
            tellOwner(level, echo, Component.translatable("mnemolith.graft.spent", Component.translatable(graft.temper().key())));
        }
        Mnemolith.LOGGER.info("Mnemolith graft spent owner={} temper={}", echo.ownerName(), graft.temper());
    }

    // ---- work hooks ----

    /** The echo whose hush covers {@code echo}'s work: itself, or the owner's nearest hushed echo in the aura radius. */
    public static @Nullable EchoEntity hushSource(ServerLevel level, EchoEntity echo) {
        return source(level, echo, Temper.HUSHED);
    }

    /** The echo whose fire smelts {@code echo}'s ore: itself, or the owner's nearest kindled echo in the aura radius. */
    public static @Nullable EchoEntity kindleSource(ServerLevel level, EchoEntity echo) {
        return source(level, echo, Temper.KINDLED);
    }

    private static @Nullable EchoEntity source(ServerLevel level, EchoEntity echo, Temper temper) {
        if (!enabled()) {
            return null;
        }
        if (echo.graftTemper() == temper) {
            return echo;
        }
        int radius = CommonConfig.ECHO_GRAFT_AURA_RADIUS.get();
        if (radius <= 0 || echo.ownerId() == null) {
            return null;
        }
        EchoEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EchoEntity other : level.getEntitiesOfClass(EchoEntity.class, echo.getBoundingBox().inflate(radius),
                other -> other != echo && other.isAlive() && other.graftTemper() == temper && echo.ownerId().equals(other.ownerId()))) {
            double distance = other.distanceToSqr(echo);
            if (distance <= (double) radius * radius && distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    /**
     * A hushed echo (anyone's) within the aura radius of {@code pos} that swallows a residue's act-out: it pays one
     * charge. Null when there is none.
     */
    public static @Nullable EchoEntity hushNear(ServerLevel level, BlockPos pos) {
        int radius = CommonConfig.ECHO_GRAFT_AURA_RADIUS.get();
        if (!enabled() || radius <= 0) {
            return null;
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos).inflate(radius);
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, box, e -> e.isAlive() && e.graftTemper() == Temper.HUSHED)) {
            if (echo.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= (double) radius * radius) {
                spend(echo, 1);
                return echo;
            }
        }
        return null;
    }

    /**
     * A work imprint is about to be written by {@code echo}. Returns the tag it should carry, or null when a hush
     * swallows it (the hushed echo pays one charge).
     */
    public static @Nullable ImprintTag workImprint(ServerLevel level, EchoEntity echo, BlockPos pos) {
        EchoEntity hush = hushSource(level, echo);
        if (hush != null) {
            spend(hush, 1);
            Mnemolith.LOGGER.info("Mnemolith graft hush swallowed work imprint owner={} at {} by={}", echo.ownerName(), pos.toShortString(),
                    hush == echo ? "self" : "neighbour");
            return null;
        }
        Temper temper = active(echo);
        ImprintTag residue = temper == null ? null : temper.residue();
        return residue == null ? ImprintTag.BUILD : residue;
    }

    /** Dig time multiplier: hushed digs slower, volatile faster. */
    public static double digFactor(EchoEntity echo) {
        Temper temper = active(echo);
        return temper == Temper.HUSHED ? HUSHED_SLOWDOWN : temper == Temper.VOLATILE ? VOLATILE_SPEEDUP : 1.0D;
    }

    /** Ticks between two placed blocks: a hushed echo places slower. */
    public static int placeInterval(EchoEntity echo, int base) {
        return is(echo, Temper.HUSHED) ? (int) Math.ceil(base * HUSHED_SLOWDOWN) : base;
    }

    /** A job dig finished: a volatile echo pays one charge per block. */
    public static void afterDig(EchoEntity echo) {
        if (is(echo, Temper.VOLATILE)) {
            spend(echo, 1);
        }
    }

    /** Longest drop in one path step. */
    public static int maxDrop(EchoEntity echo) {
        return is(echo, Temper.PLUNGING) ? PLUNGE_DROP : NORMAL_DROP;
    }

    /** A kindled echo shrugs off lava next to what it digs (never water, never a fluid block itself). */
    public static boolean lavaProof(EchoEntity echo) {
        return is(echo, Temper.KINDLED);
    }

    /** A plunging echo dug out its own floor over a drop longer than the normal limit: one charge. */
    public static void onPlungeDig(EchoEntity echo) {
        if (is(echo, Temper.PLUNGING)) {
            spend(echo, 1);
        }
    }

    /** A plunging echo takes no fall damage; a landing after a drop longer than the normal limit costs one charge. */
    public static void onEchoFall(EchoEntity echo, net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (!is(echo, Temper.PLUNGING)) {
            return;
        }
        event.setDamageMultiplier(0.0F);
        if (event.getDistance() > NORMAL_DROP + 0.5D) {
            spend(echo, 1);
        }
    }

    /**
     * Drops of a block {@code echo} broke. When the block is an ore and a kindled echo covers it, every drop with a
     * smelting recipe comes out smelted and the kindled echo pays one charge for the block.
     */
    public static void smeltDrops(ServerLevel level, EchoEntity echo, BlockState broken, List<ItemStack> drops) {
        if (drops.isEmpty() || !broken.is(Tags.Blocks.ORES)) {
            return;
        }
        EchoEntity fire = kindleSource(level, echo);
        if (fire == null) {
            return;
        }
        boolean smelted = false;
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            ItemStack result = smelt(level, drop);
            if (!result.isEmpty()) {
                drops.set(i, result);
                smelted = true;
            }
        }
        if (smelted) {
            spend(fire, 1);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, echo.getX(), echo.getY() + 1.0D, echo.getZ(), 4, 0.25D, 0.25D, 0.25D, 0.01D);
        }
    }

    private static ItemStack smelt(ServerLevel level, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input.copyWithCount(1));
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, recipeInput, level)
                .map(holder -> {
                    ItemStack out = holder.value().assemble(recipeInput);
                    return out.isEmpty() ? ItemStack.EMPTY : out.copyWithCount(Math.min(out.getMaxStackSize(), out.getCount() * input.getCount()));
                })
                .orElse(ItemStack.EMPTY);
    }

    // ---- threats ----

    /** A grave echo is a decoy: hostile mobs may pick it even when it is not working. */
    public static boolean decoy(EchoEntity echo) {
        return is(echo, Temper.GRAVE);
    }

    /** Hushed echoes are not noticed by hostile mobs, archivists, striders or replicants. */
    public static boolean unnoticed(EchoEntity echo) {
        return is(echo, Temper.HUSHED);
    }

    /** A grave echo took a hit from a hostile mob: it stands its ground and pays one charge. */
    public static void onDecoyHit(EchoEntity echo) {
        spend(echo, 1);
    }

    /** Fireproof while kindled. */
    public static boolean fireproof(EchoEntity echo) {
        return is(echo, Temper.KINDLED);
    }

    // ---- the chunk ----

    /** Every 40 ticks: a fracture under a grafted echo rejects the graft into that chunk. */
    public static void checkFracture(ServerLevel level, EchoEntity echo) {
        EchoGraft graft = echo.graft();
        if (graft == null || !enabled() || echo.scarred()) {
            return;
        }
        BlockPos pos = echo.blockPosition();
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        if (memory == null || MemoryPressure.band(memory.cachedPressure()) != PressureBand.FRACTURE) {
            return;
        }
        String result = release(level, echo, pos, "fracture");
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.NEUTRAL, 1.0F, 0.5F);
        burst(level, echo, graft.temper(), 20);
        tellOwner(level, echo, Component.translatable("mnemolith.graft.rejected." + result, Component.translatable(graft.temper().key())));
    }

    /**
     * The echo's body died (not possessed). A volatile graft bursts: a blast that breaks no blocks but hurts what
     * stands near, echoes and players included. Any other graft is released into the chunk.
     */
    public static void onBodyDied(ServerLevel level, EchoEntity echo) {
        EchoGraft graft = echo.graft();
        if (graft == null) {
            return;
        }
        if (enabled() && graft.temper() == Temper.VOLATILE) {
            echo.setGraft(null);
            Mnemolith.LOGGER.info("Mnemolith graft volatile burst owner={} at {}", echo.ownerName(), echo.blockPosition().toShortString());
            level.explode(echo, echo.getX(), echo.getY() + 0.5D, echo.getZ(), VOLATILE_BLAST, Level.ExplosionInteraction.NONE);
            return;
        }
        release(level, echo, echo.blockPosition(), "death");
    }

    /**
     * A possessed body died. The player is at 1 health and about to be returned, so a volatile graft vents without a
     * blast (it still writes its explosion into the chunk); any other graft is released as usual.
     */
    public static void onPossessedBodyDied(ServerLevel level, java.util.@Nullable UUID owner, @Nullable EchoGraft graft, BlockPos pos) {
        if (graft == null) {
            return;
        }
        if (enabled() && graft.temper() == Temper.VOLATILE) {
            ImprintWriter.write(level, pos, List.of(ImprintTag.EXPLOSION), owner, false);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            Mnemolith.LOGGER.info("Mnemolith graft volatile vent (possessed) at {}", pos.toShortString());
            return;
        }
        releaseGraft(level, graft, owner, pos, "possessed_death");
    }

    // ---- possession ----

    /** The graft of the body {@code player} is possessing, or null. */
    public static @Nullable EchoGraft possessedGraft(ServerPlayer player) {
        PossessionState.Data data = EchoPossession.state(player).data();
        return data == null ? null : data.body().graft().orElse(null);
    }

    private static void setPossessedGraft(ServerPlayer player, @Nullable EchoGraft graft) {
        PossessionState.Data data = EchoPossession.state(player).data();
        if (data == null) {
            return;
        }
        PossessionState.Body body = data.body().withGraft(Optional.ofNullable(graft));
        player.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState(new PossessionState.Data(data.real(), data.anchor(), body)));
    }

    /**
     * Player tick while possessing: the body's temper becomes an effect on the player (hushed: unrecorded, grave:
     * resistance, kindled: fire resistance, volatile: haste II) and costs one charge every 10 seconds (kindled only
     * while burning or in lava). Plunging works through {@link #possessedFall}.
     */
    public static void possessedTick(ServerPlayer player) {
        if (!enabled() || player.tickCount % 20 != 0) {
            return;
        }
        EchoGraft graft = possessedGraft(player);
        if (graft == null) {
            return;
        }
        Temper temper = graft.temper();
        Holder<MobEffect> effect = switch (temper) {
            case HUSHED -> ModEffects.UNRECORDED;
            case GRAVE -> MobEffects.RESISTANCE;
            case KINDLED -> MobEffects.FIRE_RESISTANCE;
            case VOLATILE -> MobEffects.HASTE;
            case PLUNGING -> null;
        };
        if (effect == null) {
            return;
        }
        player.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, temper == Temper.VOLATILE ? 1 : 0, true, false, true));
        boolean paying = temper != Temper.KINDLED || player.isOnFire() || player.isInLava();
        if (paying && player.tickCount % POSSESSED_SPEND_TICKS == 0) {
            spendPossessed(player, graft, 1);
        }
    }

    /** A possessed plunging body takes no fall damage; a drop that would have hurt costs one charge. */
    public static void possessedFall(ServerPlayer player, net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (!enabled() || event.getDistance() <= NORMAL_DROP + 0.5D) {
            return;
        }
        EchoGraft graft = possessedGraft(player);
        if (graft == null || graft.temper() != Temper.PLUNGING) {
            return;
        }
        event.setDamageMultiplier(0.0F);
        spendPossessed(player, graft, 1);
    }

    private static void spendPossessed(ServerPlayer player, EchoGraft graft, int amount) {
        int left = graft.charge() - amount;
        if (left > 0) {
            setPossessedGraft(player, graft.withCharge(left));
            return;
        }
        setPossessedGraft(player, null);
        player.removeEffect(effectOf(graft.temper()));
        player.sendOverlayMessage(Component.translatable("mnemolith.graft.spent", Component.translatable(graft.temper().key())));
        Mnemolith.LOGGER.info("Mnemolith graft spent in possessed body player={} temper={}", player.getGameProfile().name(), graft.temper());
    }

    private static Holder<MobEffect> effectOf(Temper temper) {
        return switch (temper) {
            case HUSHED -> ModEffects.UNRECORDED;
            case GRAVE -> MobEffects.RESISTANCE;
            case KINDLED -> MobEffects.FIRE_RESISTANCE;
            case VOLATILE -> MobEffects.HASTE;
            case PLUNGING -> MobEffects.SLOW_FALLING;
        };
    }

    // ---- presentation ----

    public static SimpleParticleType particle(Temper temper) {
        return switch (temper) {
            case HUSHED -> ModParticles.GRAFT_HUSHED.get();
            case GRAVE -> ModParticles.GRAFT_GRAVE.get();
            case KINDLED -> ModParticles.GRAFT_KINDLED.get();
            case PLUNGING -> ModParticles.GRAFT_PLUNGING.get();
            case VOLATILE -> ModParticles.GRAFT_VOLATILE.get();
        };
    }

    private static void burst(ServerLevel level, EchoEntity echo, Temper temper, int count) {
        level.sendParticles(particle(temper), echo.getX(), echo.getY() + 1.0D, echo.getZ(), count, 0.3D, 0.5D, 0.3D, 0.02D);
    }

    private static void tellOwner(ServerLevel level, EchoEntity echo, Component message) {
        if (echo.ownerId() != null && level.getServer().getPlayerList().getPlayer(echo.ownerId()) instanceof ServerPlayer owner
                && owner.level() == level && owner.distanceToSqr(echo) <= 96.0D * 96.0D) {
            owner.sendOverlayMessage(message);
        }
    }

    /** Status line for the owner's right-click and the screen: "Graft: Kindled · 20/64", or empty without a graft. */
    public static Component describe(@Nullable Temper temper, int charge, int capacity) {
        if (temper == null) {
            return Component.empty();
        }
        return Component.translatable("mnemolith.graft.status", Component.translatable(temper.key()), charge, capacity);
    }
}
