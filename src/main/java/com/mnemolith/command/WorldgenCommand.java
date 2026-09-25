package com.mnemolith.command;

import com.mnemolith.Mnemolith;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.ModFeatures;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import com.mojang.brigadier.context.CommandContext;

/** {@code /mnemolith worldgen}. */
public final class WorldgenCommand {
    private WorldgenCommand() {}

    public static int worldgen(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        RandomSource random = level.getRandom();
        boolean vein = ModFeatures.ARCHIVAL_VEIN.get().placeVein(level, pos.below(8), random, true);
        boolean pocket = ModFeatures.MUTE_POCKET.get().placePocket(level, pos, random, true);
        LevelChunk chunk = level.getChunkAt(pos.below(4));
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        boolean muted = memory != null && memory.hasMuteStone();
        int strata = memory == null ? 0 : memory.strataCount();
        Mnemolith.LOGGER.info("Mnemolith worldgen vein={} pocket={} muted={} strata={}", vein, pocket, muted, strata);
        source.sendSuccess(() -> Component.translatable("mnemolith.command.worldgen", vein, pocket, muted, strata), true);
        return (vein ? 1 : 0) + (pocket ? 1 : 0);
    }
}
