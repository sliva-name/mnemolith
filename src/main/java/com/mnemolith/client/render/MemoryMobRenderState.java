package com.mnemolith.client.render;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;

/** Client pose copied from the mob's synced action id. Armed so a hand layer can read equipment. */
public class MemoryMobRenderState extends ArmedEntityRenderState {
    public int action;
}
