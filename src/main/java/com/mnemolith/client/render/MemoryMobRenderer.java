package com.mnemolith.client.render;

import com.mnemolith.entity.MemoryMob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Copies the synced action into the render state and binds one texture. */
public abstract class MemoryMobRenderer<T extends MemoryMob, M extends EntityModel<MemoryMobRenderState>> extends MobRenderer<T, MemoryMobRenderState, M> {
    private final Identifier texture;

    protected MemoryMobRenderer(EntityRendererProvider.Context context, M model, float shadowRadius, Identifier texture) {
        super(context, model, shadowRadius);
        this.texture = texture;
    }

    @Override
    public MemoryMobRenderState createRenderState() {
        return new MemoryMobRenderState();
    }

    @Override
    public void extractRenderState(T entity, MemoryMobRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.action = entity.action();
    }

    @Override
    public Identifier getTextureLocation(MemoryMobRenderState state) {
        return this.texture;
    }
}
