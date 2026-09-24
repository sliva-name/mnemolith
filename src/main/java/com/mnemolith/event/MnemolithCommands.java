package com.mnemolith.event;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.composition.Composition;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.ChunkState;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import com.mojang.brigadier.context.CommandContext;

/** {@code /mnemolith inspect} and {@code /mnemolith smoke}. Smoke drives the same write and compose paths as play. */
public final class MnemolithCommands {
    private static final double SMOKE_FALL_DISTANCE = 5.0D;

    private MnemolithCommands() {}

    public static int inspect(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        boolean muted = LoadedChunkMemory.isMuted(level, pos);
        int pressure = memory == null ? 0 : memory.cachedPressure();
        PressureBand band = MemoryPressure.band(pressure);
        ChunkState state = LoadedChunkMemory.stateOf(memory, muted);
        int count = memory == null ? 0 : memory.imprintCount();
        String tags = memory == null ? "-" : memory.tags().toString();
        source.sendSuccess(() -> Component.translatable(
                "mnemolith.command.inspect",
                pressure,
                Component.translatable(band.translationKey()),
                state.name().toLowerCase(),
                count,
                tags), false);
        return pressure;
    }

    public static int smoke(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        BlockPos pos = BlockPos.containing(position);
        LevelChunk chunk = level.getChunkAt(pos);
        LoadedChunkMemory.clear(chunk);

        LivingEntity subject = EntityTypes.CHICKEN.spawn(level, pos.above(), EntitySpawnReason.EVENT);
        if (subject != null) {
            subject.causeFallDamage(SMOKE_FALL_DISTANCE, 1.0F, level.damageSources().fall());
        }
        level.explode(null, position.x, position.y, position.z, 1.0F, Level.ExplosionInteraction.NONE);
        if (subject != null && subject.isAlive()) {
            subject.kill(level);
        }

        chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        int pressure = memory == null ? 0 : memory.cachedPressure();

        level.setBlock(pos, ModBlocks.MUTE_STONE.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        boolean muted = LoadedChunkMemory.isMuted(level, pos);
        boolean writeBlocked = !ImprintWriter.tryWrite(level, pos, ImprintTag.BUILD, null, false);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        ServerPlayer player = source.getPlayer();
        ImprintWriter.extract(level, pos, player);

        int compose = 0;
        if (composePair(level, pos, player, ImprintTag.DEATH, ImprintTag.SILENCE)) {
            compose++;
        }
        if (composePair(level, pos, player, ImprintTag.FIRE, ImprintTag.BUILD)) {
            compose++;
        }
        if (composePair(level, pos, player, ImprintTag.FALL, ImprintTag.PLAYER)) {
            compose++;
        }
        composePair(level, pos, player, ImprintTag.BUILD, ImprintTag.BUILD);

        Mnemolith.LOGGER.info("Mnemolith smoke pressure={} muted={} writeBlocked={} compose={}", pressure, muted, writeBlocked, compose);
        int reported = compose;
        boolean reportedMuted = muted;
        boolean reportedBlocked = writeBlocked;
        source.sendSuccess(() -> Component.translatable("mnemolith.command.smoke", pressure, reportedMuted, reportedBlocked, reported), true);
        return compose;
    }

    private static boolean composePair(ServerLevel level, BlockPos pos, ServerPlayer player, ImprintTag first, ImprintTag second) {
        SimpleContainer container = new SimpleContainer(3);
        container.setItem(0, slip(first, pos));
        container.setItem(1, slip(second, pos));
        return Composition.compose(level, pos, player, container);
    }

    private static ItemStack slip(ImprintTag tag, BlockPos pos) {
        ItemStack stack = new ItemStack(ModItems.IMPRINT_SLIP.get());
        Imprint imprint = new Imprint(tag, ImprintWriter.intensityFor(tag), pos, java.util.Optional.empty(), Imprint.contextHash(tag, pos, 0L), 0L);
        stack.set(ModDataComponents.IMPRINT_CAST.get(), ImprintCast.from(imprint));
        return stack;
    }
}
