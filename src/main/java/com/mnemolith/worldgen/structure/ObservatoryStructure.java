package com.mnemolith.worldgen.structure;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import com.mnemolith.Mnemolith;
import com.mnemolith.worldgen.WorldgenTuning;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/** One rigid template on the surface. Spacing lives in the structure set. */
public class ObservatoryStructure extends Structure {
    public static final MapCodec<ObservatoryStructure> CODEC = simpleCodec(ObservatoryStructure::new);
    public static final ResourceKey<StructureTemplatePool> START_POOL = ResourceKey.create(
            Registries.TEMPLATE_POOL,
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chronicle_observatory"));

    private final StructureSettings settings;

    public ObservatoryStructure(StructureSettings settings) {
        super(settings);
        this.settings = settings;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!WorldgenTuning.observatoryEnabled()) {
            return Optional.empty();
        }
        Optional<net.minecraft.core.Holder.Reference<StructureTemplatePool>> pool = context.registryAccess()
                .lookupOrThrow(Registries.TEMPLATE_POOL)
                .get(START_POOL);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        JigsawStructure jigsaw = new JigsawStructure(this.settings, pool.get(), 1, ConstantHeight.ZERO, false, Heightmap.Types.WORLD_SURFACE_WG);
        return jigsaw.findGenerationPoint(context);
    }

    @Override
    public StructureType<?> type() {
        return ModStructureTypes.OBSERVATORY.get();
    }
}
