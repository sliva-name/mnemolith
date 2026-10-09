package com.mnemolith.worldgen.structure;

import com.mnemolith.Mnemolith;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructureTypes {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Mnemolith.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<ObservatoryStructure>> OBSERVATORY = STRUCTURE_TYPES.register(
            "chronicle_observatory",
            () -> () -> ObservatoryStructure.CODEC);

    public static final DeferredHolder<StructureType<?>, StructureType<MnemonicJigsawStructure>> FLOODED_ARCHIVE = STRUCTURE_TYPES.register(
            "flooded_archive",
            () -> () -> MnemonicJigsawStructure.codec(MnemonicJigsawStructure.Kind.FLOODED_ARCHIVE));

    public static final DeferredHolder<StructureType<?>, StructureType<MnemonicJigsawStructure>> HUSH_CHAPEL = STRUCTURE_TYPES.register(
            "hush_chapel",
            () -> () -> MnemonicJigsawStructure.codec(MnemonicJigsawStructure.Kind.HUSH_CHAPEL));

    public static final DeferredHolder<StructureType<?>, StructureType<MnemonicJigsawStructure>> MEMORY_FIELD = STRUCTURE_TYPES.register(
            "memory_field",
            () -> () -> MnemonicJigsawStructure.codec(MnemonicJigsawStructure.Kind.MEMORY_FIELD));

    public static final DeferredHolder<StructureType<?>, StructureType<MnemonicJigsawStructure>> ASHEN_ARCHIVE = STRUCTURE_TYPES.register(
            "ashen_archive",
            () -> () -> MnemonicJigsawStructure.codec(MnemonicJigsawStructure.Kind.ASHEN_ARCHIVE));

    public static final DeferredHolder<StructureType<?>, StructureType<MnemonicJigsawStructure>> MUTE_LIBRARY = STRUCTURE_TYPES.register(
            "mute_library",
            () -> () -> MnemonicJigsawStructure.codec(MnemonicJigsawStructure.Kind.MUTE_LIBRARY));

    public static final DeferredHolder<StructureType<?>, StructureType<MnemonicJigsawStructure>> SUNKEN_ARCHIVE = STRUCTURE_TYPES.register(
            "sunken_archive",
            () -> () -> MnemonicJigsawStructure.codec(MnemonicJigsawStructure.Kind.SUNKEN_ARCHIVE));

    private ModStructureTypes() {}

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
    }
}
