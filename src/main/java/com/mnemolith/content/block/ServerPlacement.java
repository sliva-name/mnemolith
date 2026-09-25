package com.mnemolith.content.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A block that has just been placed on the server, not a piston move of the same block. */
final class ServerPlacement {
    private ServerPlacement() {}

    static ServerLevel ifFresh(Level level, Block block, BlockState oldState) {
        if (level.isClientSide() || oldState.getBlock() == block || !(level instanceof ServerLevel server)) {
            return null;
        }
        return server;
    }
}
