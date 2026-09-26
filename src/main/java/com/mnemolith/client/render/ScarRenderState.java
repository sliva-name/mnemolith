package com.mnemolith.client.render;

/** Client view of the Scar: the residue view plus its merged temper colours and whether a recall is being cast. */
public class ScarRenderState extends ResidueRenderState {
    public int[] tempers = new int[0];
    public boolean casting;
}
