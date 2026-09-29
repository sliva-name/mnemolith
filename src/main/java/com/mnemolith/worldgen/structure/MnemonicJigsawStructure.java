package com.mnemolith.worldgen.structure;

import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.mojang.serialization.MapCodec;

import com.mnemolith.Mnemolith;
import com.mnemolith.worldgen.WorldgenTuning;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * One rigid template via jigsaw, gated by a worldgen config toggle.
 * Heightmap projection is optional so underground / nether absolute starts work.
 */
public final class MnemonicJigsawStructure extends Structure {
    public enum Kind {
        FLOODED_ARCHIVE("flooded_archive", WorldgenTuning::floodedArchiveEnabled, -24, false),
        HUSH_CHAPEL("hush_chapel", WorldgenTuning::hushChapelEnabled, 0, true),
        MEMORY_FIELD("memory_field", WorldgenTuning::memoryFieldEnabled, 0, true),
        ASHEN_ARCHIVE("ashen_archive", WorldgenTuning::ashenArchiveEnabled, 32, false),
        MUTE_LIBRARY("mute_library", WorldgenTuning::muteLibraryEnabled, 0, true);

        final String id;
        final BooleanSupplier enabled;
        final int absoluteY;
        final boolean projectToSurface;

        Kind(String id, BooleanSupplier enabled, int absoluteY, boolean projectToSurface) {
            this.id = id;
            this.enabled = enabled;
            this.absoluteY = absoluteY;
            this.projectToSurface = projectToSurface;
        }

        ResourceKey<StructureTemplatePool> pool() {
            return ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, this.id));
        }

        HeightProvider height() {
            if (this.projectToSurface) {
                return ConstantHeight.ZERO;
            }
            return ConstantHeight.of(VerticalAnchor.absolute(this.absoluteY));
        }
    }

    private final Kind kind;
    private final StructureSettings settings;

    public MnemonicJigsawStructure(StructureSettings settings, Kind kind) {
        super(settings);
        this.settings = settings;
        this.kind = kind;
    }

    public static MapCodec<MnemonicJigsawStructure> codec(Kind kind) {
        return simpleCodec(settings -> new MnemonicJigsawStructure(settings, kind));
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!this.kind.enabled.getAsBoolean()) {
            return Optional.empty();
        }
        Optional<Holder.Reference<StructureTemplatePool>> pool = context.registryAccess()
                .lookupOrThrow(Registries.TEMPLATE_POOL)
                .get(this.kind.pool());
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        HeightProvider height = this.kind.height();
        JigsawStructure jigsaw;
        if (this.kind.projectToSurface) {
            jigsaw = new JigsawStructure(this.settings, pool.get(), 1, height, false, Heightmap.Types.WORLD_SURFACE_WG);
        } else {
            jigsaw = new JigsawStructure(this.settings, pool.get(), 1, height, false);
        }
        return jigsaw.findGenerationPoint(context);
    }

    @Override
    public StructureType<?> type() {
        return switch (this.kind) {
            case FLOODED_ARCHIVE -> ModStructureTypes.FLOODED_ARCHIVE.get();
            case HUSH_CHAPEL -> ModStructureTypes.HUSH_CHAPEL.get();
            case MEMORY_FIELD -> ModStructureTypes.MEMORY_FIELD.get();
            case ASHEN_ARCHIVE -> ModStructureTypes.ASHEN_ARCHIVE.get();
            case MUTE_LIBRARY -> ModStructureTypes.MUTE_LIBRARY.get();
        };
    }
}
