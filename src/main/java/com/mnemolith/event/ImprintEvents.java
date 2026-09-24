package com.mnemolith.event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.entity.ModEffects;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.DiscoveryNotes;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.mnemolith.audio.ModSounds;

/** Curated vanilla events. Each handler touches the chunk that changed and then returns. */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class ImprintEvents {
    private ImprintEvents() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getEntity().blockPosition();
        List<ImprintTag> tags = new ArrayList<>();
        if (CommonConfig.WRITE_DEATH.get()) {
            tags.add(ImprintTag.DEATH);
        }
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            tags.add(ImprintTag.FIRE);
        }
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION) && CommonConfig.WRITE_EXPLOSION.get()) {
            tags.add(ImprintTag.EXPLOSION);
        }
        if (event.getEntity() instanceof Player || event.getSource().getEntity() instanceof Player) {
            tags.add(ImprintTag.PLAYER);
        }
        UUID playerId = event.getEntity() instanceof Player player
                ? player.getUUID()
                : event.getSource().getEntity() instanceof Player attacker ? attacker.getUUID() : null;
        ImprintWriter.write(level, pos, ImprintWriter.witnessedTags(level, pos, tags), playerId, false);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!CommonConfig.WRITE_EXPLOSION.get() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getExplosion().center());
        UUID playerId = event.getExplosion().getIndirectSourceEntity() instanceof Player player ? player.getUUID() : null;
        List<ImprintTag> tags = new ArrayList<>();
        tags.add(ImprintTag.EXPLOSION);
        if (playerId != null) {
            tags.add(ImprintTag.PLAYER);
        }
        ImprintWriter.write(level, pos, tags, playerId, false);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer player && player.hasEffect(ModEffects.LANDING_BURST) && event.getDistance() >= ImprintConstants.LANDING_BURST_MIN_DISTANCE) {
            event.setDamageMultiplier(ImprintConstants.LANDING_BURST_DAMAGE_MULTIPLIER);
            player.removeEffect(ModEffects.LANDING_BURST);
            level.playSound(null, player.blockPosition(), ModSounds.COMPOSE_SUCCESS.get(), SoundSource.PLAYERS, 0.5F, 1.4F);
            MemoryFx.landing(level, player.getX(), player.getY(), player.getZ());
        }
        if (!CommonConfig.WRITE_FALL.get() || event.getDistance() < CommonConfig.FALL_DISTANCE_MIN.get()) {
            return;
        }
        List<ImprintTag> tags = new ArrayList<>();
        tags.add(ImprintTag.FALL);
        UUID playerId = null;
        if (entity instanceof Player player) {
            tags.add(ImprintTag.PLAYER);
            playerId = player.getUUID();
        }
        ImprintWriter.write(level, entity.blockPosition(), tags, playerId, false);
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        writeBuild(level, event.getPos(), event.getState(), event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            writeBuild(level, event.getPos(), event.getPlacedBlock(), player);
            if (player instanceof ServerPlayer serverPlayer && event.getPlacedBlock().getBlock() == ModBlocks.MUTE_STONE.get()) {
                DiscoveryNotes.noteMute(serverPlayer);
            }
        } else {
            writeBuild(level, event.getPos(), event.getPlacedBlock(), null);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory != null) {
            memory.fadeQuiet(level.getGameTime(), CommonConfig.QUIET_FADE_TICKS.get());
            MemoryPressure.recompute(chunk, memory);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        coolPressure(level, player);
        if (!player.hasEffect(ModEffects.FIRE_TRAIL)) {
            return;
        }
        if (player.tickCount % ImprintConstants.FIRE_TRAIL_INTERVAL_TICKS != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 2, 0.2D, 0.05D, 0.2D, 0.01D);
        BlockPos below = player.blockPosition().below();
        BlockState state = level.getBlockState(below);
        if (state.getBlock() instanceof SnowLayerBlock) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers <= 1) {
                level.removeBlock(below, false);
            } else {
                level.setBlock(below, state.setValue(SnowLayerBlock.LAYERS, layers - 1), Block.UPDATE_ALL);
            }
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (next != null && next.hasEffect(ModEffects.UNRECORDED)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    private static void coolPressure(ServerLevel level, ServerPlayer player) {
        int interval = CommonConfig.INSTABILITY_DECAY_TICKS.get();
        if (interval <= 0 || level.getGameTime() % interval != 0) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || !memory.markCoolPulse(level.getGameTime())) {
            return;
        }
        boolean changed = memory.coolInstability(CommonConfig.INSTABILITY_DECAY.get()) > 0;
        if (memory.fadeQuiet(level.getGameTime(), CommonConfig.QUIET_FADE_TICKS.get())) {
            changed = true;
        }
        if (!changed) {
            return;
        }
        MemoryPressure.recompute(chunk, memory);
        Mnemolith.LOGGER.info(
                "Mnemolith pressure cool chunk {} {} pressure={} instability={}",
                chunk.getPos().x(),
                chunk.getPos().z(),
                memory.cachedPressure(),
                memory.instability());
    }

    private static void writeBuild(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (!CommonConfig.WRITE_BUILD.get() || state.getBlock() == ModBlocks.MUTE_STONE.get()) {
            return;
        }
        List<ImprintTag> tags = new ArrayList<>();
        tags.add(state.isSignalSource() ? ImprintTag.REDSTONE : ImprintTag.BUILD);
        if (player != null) {
            tags.add(ImprintTag.PLAYER);
        }
        ImprintWriter.write(level, pos, tags, player == null ? null : player.getUUID(), true);
    }
}
