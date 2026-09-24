package com.mnemolith.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Client pose copied from the mob's synced action id. */
public class MemoryMobRenderState extends LivingEntityRenderState {
    public int action;
}
