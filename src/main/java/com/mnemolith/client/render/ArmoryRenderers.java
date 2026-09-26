package com.mnemolith.client.render;

import com.mnemolith.Mnemolith;
import com.mnemolith.client.model.FractureStalkerModel;
import com.mnemolith.client.model.KinWitnessModel;
import com.mnemolith.client.model.LedgerMiteModel;
import com.mnemolith.entity.mob.FractureStalker;
import com.mnemolith.entity.mob.KinWitness;
import com.mnemolith.entity.mob.LedgerMite;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public final class ArmoryRenderers {
    public static final Identifier MITE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/ledger_mite.png");
    public static final Identifier WITNESS = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/kin_witness.png");
    public static final Identifier STALKER = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/fracture_stalker.png");
    public static final Identifier STALKER_ELITE = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "textures/entity/fracture_stalker_elite.png");

    private ArmoryRenderers() {}

    public static class MiteRenderer extends MobRenderer<LedgerMite, LedgerMiteModel.State, LedgerMiteModel> {
        public MiteRenderer(EntityRendererProvider.Context context) {
            super(context, new LedgerMiteModel(context.bakeLayer(ModEntityRenderers.LEDGER_MITE)), 0.35F);
        }

        @Override
        public LedgerMiteModel.State createRenderState() {
            return new LedgerMiteModel.State();
        }

        @Override
        public void extractRenderState(LedgerMite entity, LedgerMiteModel.State state, float partialTick) {
            super.extractRenderState(entity, state, partialTick);
            state.attack = entity.getAttackAnim(partialTick);
        }

        @Override
        public Identifier getTextureLocation(LedgerMiteModel.State state) {
            return MITE;
        }
    }

    public static class WitnessRenderer extends MobRenderer<KinWitness, KinWitnessModel.State, KinWitnessModel> {
        public WitnessRenderer(EntityRendererProvider.Context context) {
            super(context, new KinWitnessModel(context.bakeLayer(ModEntityRenderers.KIN_WITNESS)), 0.35F);
        }

        @Override
        public KinWitnessModel.State createRenderState() {
            return new KinWitnessModel.State();
        }

        @Override
        public void extractRenderState(KinWitness entity, KinWitnessModel.State state, float partialTick) {
            super.extractRenderState(entity, state, partialTick);
            state.attack = entity.getAttackAnim(partialTick);
        }

        @Override
        public Identifier getTextureLocation(KinWitnessModel.State state) {
            return WITNESS;
        }
    }

    public static class StalkerRenderer extends MobRenderer<FractureStalker, FractureStalkerModel.State, FractureStalkerModel> {
        public StalkerRenderer(EntityRendererProvider.Context context) {
            super(context, new FractureStalkerModel(context.bakeLayer(ModEntityRenderers.FRACTURE_STALKER)), 0.55F);
        }

        @Override
        public FractureStalkerModel.State createRenderState() {
            return new FractureStalkerModel.State();
        }

        @Override
        public void extractRenderState(FractureStalker entity, FractureStalkerModel.State state, float partialTick) {
            super.extractRenderState(entity, state, partialTick);
            state.elite = entity.elite();
            state.attack = entity.getAttackAnim(partialTick);
            state.scale = entity.elite() ? 1.2F : 1.0F;
        }

        @Override
        public Identifier getTextureLocation(FractureStalkerModel.State state) {
            return state.elite ? STALKER_ELITE : STALKER;
        }
    }
}
