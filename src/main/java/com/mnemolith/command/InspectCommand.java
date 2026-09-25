package com.mnemolith.command;

import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.ChunkState;
import com.mnemolith.world.LoadedChunkMemory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import com.mojang.brigadier.context.CommandContext;

/** {@code /mnemolith inspect}. */
public final class InspectCommand {
    private InspectCommand() {}

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
}
