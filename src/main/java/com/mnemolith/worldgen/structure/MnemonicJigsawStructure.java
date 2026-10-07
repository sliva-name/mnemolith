package com.mnemolith.worldgen.structure;

import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.mojang.serialization.MapCodec;

import com.mnemolith.Mnemolith;
import com.mnemolith.worldgen.WorldgenTuning;

import com.mojang.datafixers.util.Either;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
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
        // The template has 8 blocks of basalt piers under its floor (tools/build_w2_w3_structures.py, ASHEN_FOUNDATION),
        // so it starts at 24 to keep the floor at 32, just over the Nether's lava sea.
        ASHEN_ARCHIVE("ashen_archive", WorldgenTuning::ashenArchiveEnabled, 24, false),
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
        Optional<GenerationStub> stub = jigsaw.findGenerationPoint(context);
        if (this.kind == Kind.MUTE_LIBRARY) {
            return stub.flatMap(found -> onEndTerrain(context, found));
        }
        return stub;
    }

    /** Like vanilla End cities: a mute library needs outer-island ground under its whole footprint. */
    static final int MIN_END_GROUND_Y = 60;

    /**
     * Over the void {@code WORLD_SURFACE_WG} is the world bottom, so the library would sit at y=0 under nothing
     * (and {@code /locate} would point there). The central {@code minecraft:the_end} biome (dragon island and the
     * empty ring around it) is skipped as well, as vanilla End cities do, so the library never lands in the dragon fight.
     */
    private static Optional<GenerationStub> onEndTerrain(GenerationContext context, GenerationStub stub) {
        BlockPos start = stub.position();
        if (start.getY() < MIN_END_GROUND_Y) {
            return Optional.empty();
        }
        Holder<Biome> biome = context.chunkGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(start.getX()), QuartPos.fromBlock(start.getY()), QuartPos.fromBlock(start.getZ()),
                context.randomState().sampler());
        if (biome.is(Biomes.THE_END)) {
            return Optional.empty();
        }
        StructurePiecesBuilder pieces = stub.getPiecesBuilder();
        if (pieces.isEmpty()) {
            return Optional.empty();
        }
        BoundingBox box = pieces.getBoundingBox();
        int lowest = getLowestY(context, box.minX(), box.minZ(), box.getXSpan() - 1, box.getZSpan() - 1);
        if (lowest < MIN_END_GROUND_Y) {
            return Optional.empty();
        }
        // Hand the already built pieces on so they are not generated twice.
        return Optional.of(new GenerationStub(start, Either.right(pieces)));
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
