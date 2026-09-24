package com.mnemolith.content.composition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.entity.ModEffects;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.content.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/** Server-side composition. The menu button and the smoke command both call {@link #compose}. */
public final class Composition {
    private Composition() {}

    public static Optional<CompositionFormula> match(List<ImprintTag> tags) {
        List<ImprintTag> sorted = tags.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        for (CompositionFormula formula : CompositionFormula.values()) {
            if (formula.tags().equals(sorted)) {
                return Optional.of(formula);
            }
        }
        return Optional.empty();
    }

    public static boolean compose(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, Container container) {
        if (!CommonConfig.COMPOSITION_ENABLED.get()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("mnemolith.message.compose_disabled"));
            }
            return false;
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
                return fail(level, pos, player, container);
            }
            slots.add(slot);
            tags.add(cast.tag());
        }
        if (slots.isEmpty()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("mnemolith.message.compose_empty"));
            }
            return false;
        }
        Optional<CompositionFormula> formula = match(tags);
        if (formula.isEmpty()) {
            return fail(level, pos, player, container);
        }
        for (int slot : slots) {
            container.removeItem(slot, 1);
        }
        apply(player, formula.get());
        level.playSound(null, pos, ModSounds.COMPOSE_SUCCESS.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, ImprintConstants.SERVER_PARTICLE_COUNT, 0.4D, 0.3D, 0.4D, 0.0D);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.composed", Component.translatable(formula.get().translationKey())));
        }
        return true;
    }

    private static boolean fail(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                container.removeItem(slot, 1);
                break;
            }
        }
        ImprintWriter.spike(level, pos, CommonConfig.FAILURE_PRESSURE_SPIKE.get());
        level.playSound(null, pos, ModSounds.COMPOSE_FAIL.get(), SoundSource.BLOCKS, 0.7F, 0.8F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, ImprintConstants.SERVER_PARTICLE_COUNT, 0.3D, 0.3D, 0.3D, 0.0D);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.compose_fail"));
        }
        return false;
    }

    private static void apply(@Nullable ServerPlayer player, CompositionFormula formula) {
        if (player == null) {
            return;
        }
        switch (formula) {
            case UNRECORDED -> player.addEffect(new MobEffectInstance(ModEffects.UNRECORDED, ImprintConstants.UNRECORDED_DURATION_TICKS, 0, false, true));
            case FIRE_TRAIL -> {
                player.addEffect(new MobEffectInstance(ModEffects.FIRE_TRAIL, ImprintConstants.FIRE_TRAIL_DURATION_TICKS, 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ImprintConstants.FIRE_TRAIL_DURATION_TICKS, 0, false, true));
            }
            case LANDING_BURST -> player.addEffect(new MobEffectInstance(ModEffects.LANDING_BURST, ImprintConstants.LANDING_BURST_DURATION_TICKS, 0, false, false));
        }
    }
}
