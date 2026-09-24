package com.mnemolith.content;

import com.mnemolith.Mnemolith;

import net.neoforged.neoforge.registries.DeferredRegister;

/** Block registry. Empty until imprint-bearing blocks are added. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mnemolith.MOD_ID);

    private ModBlocks() {}
}
