package com.mnemolith.content.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Placement checks shared by blocks that register themselves on chunk memory. */
final class ServerPlacement {
    private ServerPlacement() {}

    /** The server level when the block is new at this position, not a state change of the same block; else null. */
    static ServerLevel ifFresh(Level level, Block block, BlockState oldState) {
        if (level.isClientSide() || oldState.getBlock() == block || !(level instanceof ServerLevel server)) {
            return null;
        }
        return server;
    }

    /**
     * True when the block arrived by piston rather than by a player or a command. A finished push lands with plain
     * update flags, so the {@code moving_piston} it replaces is the tell, not only {@code movedByPiston}.
     */
    static boolean byPiston(BlockState oldState, boolean movedByPiston) {
        return movedByPiston || oldState.is(Blocks.MOVING_PISTON);
    }
}
