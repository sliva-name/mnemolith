package com.mnemolith.command;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import com.mojang.brigadier.context.CommandContext;

/** {@code /mnemolith smoke}. */
public final class SmokeCommand {
    private SmokeCommand() {}

    private static final double SMOKE_FALL_DISTANCE = 5.0D;

    public static int smoke(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        BlockPos pos = BlockPos.containing(position);
        ServerPlayer player = source.getPlayer();
        LevelChunk chunk = level.getChunkAt(pos);
        LoadedChunkMemory.clear(chunk);
        BlockPos quiet = pos.offset(16, 0, 0);
        LoadedChunkMemory.clear(level.getChunkAt(quiet));
        SmokeCommand.composePair(level, quiet, player, ImprintTag.BUILD, ImprintTag.BUILD);

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

        ImprintWriter.extract(level, pos, player);

        int compose = 0;
        if (SmokeCommand.composePair(level, pos, player, ImprintTag.DEATH, ImprintTag.SILENCE)) {
            compose++;
        }
        if (SmokeCommand.composePair(level, pos, player, ImprintTag.FIRE, ImprintTag.BUILD)) {
            compose++;
        }
        if (SmokeCommand.composePair(level, pos, player, ImprintTag.FALL, ImprintTag.PLAYER)) {
            compose++;
        }
        SmokeCommand.composePair(level, pos, player, ImprintTag.BUILD, ImprintTag.BUILD);

        PressureBand smokeBand = MemoryPressure.band(pressure);
        Mnemolith.LOGGER.info("Mnemolith smoke pressure={} band={} muted={} writeBlocked={} compose={}", pressure, smokeBand, muted, writeBlocked, compose);
        int reported = compose;
        boolean reportedMuted = muted;
        boolean reportedBlocked = writeBlocked;
        source.sendSuccess(() -> Component.translatable("mnemolith.command.smoke", pressure, reportedMuted, reportedBlocked, reported), true);
        return compose;
    }

    static boolean composePair(ServerLevel level, BlockPos pos, ServerPlayer player, ImprintTag first, ImprintTag second) {
        return SlipPair.compose(level, pos, player, first, second).success();
    }
}
