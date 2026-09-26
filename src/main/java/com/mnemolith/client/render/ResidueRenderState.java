package com.mnemolith.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Client view of a residual echo: temper color, strength, pinned and lens reading progress. */
public class ResidueRenderState extends LivingEntityRenderState {
    public int id;
    public int rgb = 0xFF9A5C;
    public int strength;
    public boolean pinned;
    public float read;
    public boolean volatileFlicker;
}
