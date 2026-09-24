package com.mnemolith.content;

import com.mnemolith.Mnemolith;

import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registry. Empty until memory tools and imprint items are added. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mnemolith.MOD_ID);

    private ModItems() {}
}
