package com.mnemolith.fishing;

import java.util.Optional;

import com.mnemolith.data.ImprintSlips;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Undertow, stage 1: memory angling. A catch reeled in from water may bring up the oldest imprint of the bobber's
 * chunk (one no hush loom keeps; with a tide lure in the off hand, the loudest, more often) as an imprint slip, flung to the player like a second catch. See
 * {@code docs/design/undertow.md}.
 */
public final class Angling {
    public static final float BASE_CHANCE = 0.30F;
    public static final float PER_LUCK = 0.10F;
    public static final float MAX_CHANCE = 0.60F;
    /** Stage 2: a tide lure in the off hand adds this, up to {@link #LURE_MAX_CHANCE}, and pulls the loudest memory. */
    public static final float LURE_BONUS = 0.20F;
    public static final float LURE_MAX_CHANCE = 0.80F;
    public static final int LURE_DURABILITY = 32;

    private Angling() {}

    /** The chance for a catch made with {@code rod}: base plus Luck of the Sea, capped. */
    public static float chance(ServerLevel level, ItemStack rod) {
        int luck = 0;
        if (!rod.isEmpty()) {
            var holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUCK_OF_THE_SEA);
            luck = EnchantmentHelper.getItemEnchantmentLevel(holder, rod);
        }
        return chanceFor(luck);
    }

    /** Base plus 10% per level of Luck of the Sea (at most three levels count), never above 1. */
    public static float chanceFor(int luck) {
        return Math.min(1.0F, baseChance() + PER_LUCK * Math.min(3, Math.max(0, luck)));
    }

    public static float chanceFor(int luck, boolean lure) {
        return lure ? Math.min(1.0F, chanceFor(luck) + lureBonus()) : chanceFor(luck);
    }

    /** The server option {@code anglingChance} (default {@link #BASE_CHANCE}). */
    public static float baseChance() {
        return com.mnemolith.config.ServerConfig.ANGLING_CHANCE.get().floatValue();
    }

    /** The server option {@code tideLureBonus} (default {@link #LURE_BONUS}). */
    public static float lureBonus() {
        return com.mnemolith.config.ServerConfig.TIDE_LURE_BONUS.get().floatValue();
    }

    /** Advancements: Undertow (any memory landed) and Lure of the Deep (one landed with a tide lure). */
    public static final net.minecraft.resources.Identifier ADVANCEMENT = net.minecraft.resources.Identifier.fromNamespaceAndPath(
            com.mnemolith.Mnemolith.MOD_ID, "undertow_landed");
    public static final net.minecraft.resources.Identifier LURE_ADVANCEMENT = net.minecraft.resources.Identifier.fromNamespaceAndPath(
            com.mnemolith.Mnemolith.MOD_ID, "tide_lure_landed");

    /** True when the player holds a tide lure in the off hand. */
    public static boolean lured(Player player) {
        return player.getOffhandItem().is(com.mnemolith.content.ModItems.TIDE_LURE.get());
    }

    /** The chance for this player's catch: the rod's Luck of the Sea and the off-hand lure. */
    public static float chance(ServerLevel level, Player player) {
        float base = chance(level, rod(player));
        return lured(player) ? Math.min(1.0F, base + lureBonus()) : base;
    }

    private static void award(ServerLevel level, net.minecraft.server.level.ServerPlayer player, net.minecraft.resources.Identifier id, String criterion) {
        var advancement = level.getServer().getAdvancements().get(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    /** The rod in the owner's hands (main hand first), or empty. */
    public static ItemStack rod(Player player) {
        if (player.getMainHandItem().is(Items.FISHING_ROD)) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem().is(Items.FISHING_ROD) ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    /** One reeled catch: rolls the chance and angles on a hit. */
    public static Optional<Imprint> onCatch(ServerLevel level, FishingHook hook, Player player) {
        return onCatch(level, hook, player, level.getRandom());
    }

    /** As {@link #onCatch(ServerLevel, FishingHook, Player)} with the roll from {@code random} (QA seeds it). */
    public static Optional<Imprint> onCatch(ServerLevel level, FishingHook hook, Player player, net.minecraft.util.RandomSource random) {
        if (random.nextFloat() >= chance(level, player)) {
            return Optional.empty();
        }
        return angle(level, hook, player);
    }

    /** Takes the oldest unkept imprint of the bobber's chunk and flings its slip to the player; no roll. */
    public static Optional<Imprint> angle(ServerLevel level, FishingHook hook, Player player) {
        BlockPos at = hook.blockPosition();
        if (!level.getFluidState(at).is(FluidTags.WATER)) {
            return Optional.empty();
        }
        LevelChunk chunk = level.getChunkAt(at);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        boolean lure = lured(player);
        Optional<Imprint> taken = memory == null ? Optional.empty() : lure ? memory.removeHighestUnkept() : memory.removeOldestUnkept();
        if (taken.isEmpty()) {
            return taken;
        }
        chunk.markUnsaved();
        if (lure) {
            player.getOffhandItem().hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            award(level, serverPlayer, ADVANCEMENT, "landed");
            if (lure) {
                award(level, serverPlayer, LURE_ADVANCEMENT, "landed");
            }
        }
        MemoryPressure.recompute(chunk, memory);
        ItemEntity slip = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), ImprintSlips.of(taken.get().tag(), at));
        double dx = player.getX() - hook.getX();
        double dy = player.getY() - hook.getY();
        double dz = player.getZ() - hook.getZ();
        slip.setDeltaMovement(dx * 0.1D, dy * 0.1D + Math.sqrt(Mth.length(dx, dy, dz)) * 0.08D, dz * 0.1D);
        level.addFreshEntity(slip);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, hook.getX(), hook.getY(), hook.getZ(), 6, 0.2D, 0.3D, 0.2D, 0.05D);
        com.mnemolith.particle.MemoryFeel.cue(level, com.mnemolith.particle.MemoryFeel.Kind.CAUGHT, hook.getX(), hook.getY(), hook.getZ(), 6);
        return taken;
    }
}
