package com.mnemolith.armory;

import java.util.Optional;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Iron-weight blade. A hit in a chunk that still holds an imprint strikes again, then the blade rests.
 * The second strike's flavour follows the loudest tag in that chunk.
 */
public class RecallBladeItem extends Item {
    public RecallBladeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        if (attacker instanceof Player player && player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(target.blockPosition()));
        if (memory == null || memory.imprintCount() == 0) {
            return;
        }
        if (attacker instanceof Player player) {
            player.getCooldowns().addCooldown(stack, CommonConfig.RECALL_BLADE_COOLDOWN.get());
        }
        target.hurtServer(level, attacker.damageSources().mobAttack(attacker), 2.0F);
        Optional<Imprint> loudest = memory.highest();
        ImprintTag tag = loudest.map(Imprint::tag).orElse(null);
        applyTag(level, target, tag);
        level.playSound(null, target.blockPosition(), ModSounds.EXTRACT.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** Client tooltip helper: which tag the blade would currently read under the player. */
    public static Component tagLine(Level level, Player player) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(player.blockPosition()));
        if (memory == null || memory.imprintCount() == 0) {
            return Component.translatable("item.mnemolith.recall_blade.tag_none");
        }
        return memory.highest()
                .map(imprint -> Component.translatable("item.mnemolith.recall_blade.tag", Component.translatable(imprint.tag().translationKey())))
                .orElseGet(() -> Component.translatable("item.mnemolith.recall_blade.tag_none"));
    }

    private static void applyTag(ServerLevel level, LivingEntity target, ImprintTag tag) {
        if (tag == null) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
            return;
        }
        switch (tag) {
            case FIRE -> target.igniteForSeconds(4);
            case FALL -> {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, false));
                Vec3 motion = target.getDeltaMovement();
                target.setDeltaMovement(motion.x, Math.min(motion.y, -0.4D), motion.z);
                target.hurtMarked = true;
            }
            case DEATH -> target.hurtServer(level, level.damageSources().magic(), 4.0F);
            case EXPLOSION -> level.explode(null, target.getX(), target.getY(), target.getZ(), 1.2F, false, Level.ExplosionInteraction.NONE);
            case SILENCE -> {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            }
            default -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        }
    }
}
