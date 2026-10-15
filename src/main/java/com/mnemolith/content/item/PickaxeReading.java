package com.mnemolith.content.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.Hollows;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * The recollite pickaxe reads the chunk while it mines. Every block it breaks is weighed against the chunk's loudest
 * imprint (none under a mute stone):
 * <ul>
 * <li>every block: fire smelts the drops, portal pulls them to the miner's feet, death adds experience to blocks that
 * already drop some, silence and forgotten leave the break unwritten (no build imprint);</li>
 * <li>at most every {@link #READ_COOLDOWN} ticks: a line names what the stone remembers, a short effect follows the tag,
 * and in Memory Hollows the memory surfaces as a catchable flicker beside the block.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class PickaxeReading {
    public static final int READ_COOLDOWN = 60;
    public static final int EFFECT_TICKS = 100;
    public static final int DEATH_XP = 3;
    public static final double REVEAL_RADIUS = 16.0D;

    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static long reads;

    private PickaxeReading() {}

    public static boolean holds(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && living.getMainHandItem().is(ModItems.RECOLLITE_PICKAXE.get());
    }

    /** The loudest imprint of the chunk at {@code pos}; null when the chunk holds none or a mute stone hushes it. */
    public static @Nullable ImprintTag read(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        if (memory == null || memory.imprintCount() == 0 || memory.hasAnyMute()) {
            return null;
        }
        return memory.highest().map(Imprint::tag).orElse(null);
    }

    /** Whether a break by {@code player} at {@code pos} writes no build imprint (pickaxe in a silent or forgotten chunk). */
    public static boolean quiet(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (!holds(player)) {
            return false;
        }
        ImprintTag tag = read(level, pos);
        return tag == ImprintTag.SILENCE || tag == ImprintTag.FORGOTTEN;
    }

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (event.isCanceled() || !(event.getBreaker() instanceof ServerPlayer player) || !event.getTool().is(ModItems.RECOLLITE_PICKAXE.get())) {
            return;
        }
        int xp = surface(event.getLevel(), player, event.getPos(), event.getDrops(), event.getDroppedExperience(), event.getLevel().getGameTime());
        event.setDroppedExperience(xp);
    }

    /**
     * Applies one reading for a block {@code player} broke at {@code pos}: changes {@code drops} in place and returns the
     * experience the block drops. Public for QA.
     */
    public static int surface(ServerLevel level, ServerPlayer player, BlockPos pos, List<ItemEntity> drops, int xp, long now) {
        ImprintTag tag = read(level, pos);
        if (tag == ImprintTag.FIRE) {
            for (ItemEntity drop : drops) {
                ItemStack smelted = smelt(level, drop.getItem());
                if (!smelted.isEmpty()) {
                    drop.setItem(smelted);
                }
            }
        } else if (tag == ImprintTag.PORTAL) {
            for (ItemEntity drop : drops) {
                drop.setPos(player.getX(), player.getY() + 0.2D, player.getZ());
                drop.setDeltaMovement(0.0D, 0.0D, 0.0D);
                drop.setNoPickUpDelay();
            }
        } else if (tag == ImprintTag.DEATH && xp > 0) {
            xp += DEATH_XP;
        }
        Long next = NEXT.get(player.getUUID());
        if (next != null && now < next) {
            return xp;
        }
        NEXT.put(player.getUUID(), now + READ_COOLDOWN);
        reads++;
        player.sendOverlayMessage(line(tag));
        effect(level, player, tag);
        if (tag != null && Hollows.is(level.getBiome(pos))) {
            HollowFlickers.send(level, new HollowFlickerPayload(pos.immutable(), player.getYRot() + 180.0F, HollowFlickers.scene(tag), tag.ordinal()));
        }
        return xp;
    }

    /** The line shown over the hotbar for a reading. */
    public static Component line(@Nullable ImprintTag tag) {
        if (tag == null) {
            return Component.translatable("item.mnemolith.recollite_pickaxe.reads_none").withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("item.mnemolith.recollite_pickaxe.reads", Component.translatable(tag.translationKey()))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /** The short effect a reading gives the miner. */
    public static void effect(ServerLevel level, ServerPlayer player, @Nullable ImprintTag tag) {
        if (tag == null) {
            return;
        }
        switch (tag) {
            case BOSS, EXPLOSION -> player.addEffect(new MobEffectInstance(MobEffects.HASTE, EFFECT_TICKS, 1, false, false, true));
            case BUILD, PATH, PLAYER, TRADE, REDSTONE -> player.addEffect(new MobEffectInstance(MobEffects.HASTE, EFFECT_TICKS, 0, false, false, true));
            case FALL -> player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, EFFECT_TICKS, 0, false, false, true));
            case SCULK -> player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_TICKS * 2, 0, false, false, true));
            case LIGHTNING -> {
                for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(REVEAL_RADIUS),
                        e -> e instanceof Enemy && e.isAlive())) {
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, EFFECT_TICKS, 0, false, false));
                }
            }
            default -> {
                // fire, portal, death and silence act on the block itself
            }
        }
    }

    public static void resetCooldown(UUID player) {
        NEXT.remove(player);
    }

    /** Readings shown since the server started (QA). */
    public static long reads() {
        return reads;
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
}
