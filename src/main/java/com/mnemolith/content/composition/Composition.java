package com.mnemolith.content.composition;

import com.mnemolith.event.ComposeFinishedEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.Mnemolith;
import com.mnemolith.imprint.DiscoveryNotes;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.InventorySpace;
import com.mnemolith.content.ModItems;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.particle.MemoryFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-side composition. The menu button and the smoke command both call {@link #compose}. */
public final class Composition {
    private Composition() {}

    public static Optional<CompositionRecipe> match(List<ImprintTag> tags) {
        return CompositionRecipes.match(tags);
    }

    public static ComposeResult compose(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, Container container) {
        if (!CommonConfig.COMPOSITION_ENABLED.get()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("mnemolith.message.compose_disabled"));
            }
            return finish(level, pos, player, ComposeResult.DISABLED, -1);
        }
        List<Integer> slots = new ArrayList<>();
        List<ImprintTag> tags = new ArrayList<>();
        for (int slot = 0; slot < ImprintConstants.COMPOSITION_SLOTS && slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
            if (stack.getItem() != ModItems.IMPRINT_SLIP.get() || cast == null) {
                return fail(level, pos, player, container, tags);
            }
            slots.add(slot);
            tags.add(cast.tag());
        }
        if (slots.isEmpty()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("mnemolith.message.compose_empty"));
            }
            return finish(level, pos, player, ComposeResult.EMPTY, -1);
        }
        if (player != null) {
            for (ImprintTag tag : tags) {
                DiscoveryNotes.noteTag(player, tag);
            }
        }
        Optional<CompositionRecipe> formula = match(tags);
        if (formula.isEmpty()) {
            return fail(level, pos, player, container, tags);
        }
        ItemStack reward = player == null ? ItemStack.EMPTY : rewardOf(formula.get(), pos, level.getGameTime());
        if (player != null && !InventorySpace.fits(player.getInventory(), reward)) {
            InventorySpace.refuse(player);
            return finish(level, pos, player, ComposeResult.FULL, -1);
        }
        for (int slot : slots) {
            container.removeItem(slot, 1);
        }
        give(player, reward);
        level.playSound(null, pos, ModSounds.COMPOSE_SUCCESS.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        MemoryFx.composeSuccess(level, pos);
        if (player != null) {
            DiscoveryNotes.noteFormula(player, CompositionRecipes.indexOf(formula.get()));
            player.sendSystemMessage(Component.translatable("mnemolith.message.composed", Component.translatable(formula.get().translationKey())));
        }
        return finish(level, pos, player, ComposeResult.SUCCESS, CompositionRecipes.indexOf(formula.get()));
    }

    /** Shard of {@code product}, or the optional result item (with filter bound from product when relevant). */
    public static ItemStack rewardOf(CompositionRecipe recipe, BlockPos pos, long gameTime) {
        if (recipe.resultItem().isPresent()) {
            Identifier id = recipe.resultItem().get();
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            if (item == Items.AIR) {
                Mnemolith.LOGGER.warn("Mnemolith composition result {} is missing; falling back to shard", id);
                return Residues.shard(recipe.product(), CompositionFormula.SHARD_STRENGTH, pos, gameTime);
            }
            ItemStack stack = new ItemStack(item);
            if (item == ModItems.CHRONICLE_LENS.get() || item == ModItems.SELECTIVE_MUTE_STONE.get()) {
                stack.set(ModDataComponents.FILTER_TAG.get(), recipe.product());
            }
            return stack;
        }
        return Residues.shard(recipe.product(), CompositionFormula.SHARD_STRENGTH, pos, gameTime);
    }

    private static ComposeResult fail(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, Container container, List<ImprintTag> tags) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                container.removeItem(slot, 1);
                break;
            }
        }
        int pressure = ImprintWriter.spike(level, pos, CommonConfig.FAILURE_PRESSURE_SPIKE.get());
        PressureBand band = MemoryPressure.band(pressure);
        boolean replicant = band == PressureBand.OVERLOADED || band == PressureBand.FRACTURE;
        if (replicant) {
            MobSpawns.trySpawnReplicant(level, pos.above());
        }
        Mnemolith.LOGGER.info("Mnemolith compose fail pressure={} band={} replicantAsked={}", pressure, band, replicant);
        level.playSound(null, pos, ModSounds.COMPOSE_FAIL.get(), SoundSource.BLOCKS, 0.7F, 0.8F);
        MemoryFx.composeFail(level, pos);
        if (player != null) {
            // Needed for the invalid-item path, which reaches fail() before compose() notes the tags.
            // On a formula miss the tags are already noted; noteTag is idempotent (no second sync or message).
            for (ImprintTag tag : tags) {
                DiscoveryNotes.noteTag(player, tag);
            }
            player.sendSystemMessage(Component.translatable("mnemolith.message.compose_fail"));
        }
        return finish(level, pos, player, ComposeResult.FAIL, -1);
    }

    private static ComposeResult finish(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, int status, int formulaOrdinal) {
        Mnemolith.LOGGER.info("Mnemolith compose status={} formula={}", status, formulaOrdinal);
        NeoForge.EVENT_BUS.post(new ComposeFinishedEvent(level, pos, player, status, formulaOrdinal));
        return new ComposeResult(status, formulaOrdinal);
    }

    private static void give(@Nullable ServerPlayer player, ItemStack reward) {
        if (player == null || reward.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(reward) && !reward.isEmpty()) {
            player.drop(reward, false);
        }
    }
}
