package com.mnemolith.content;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.block.CompositionReelBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mnemolith.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompositionReelBlockEntity>> COMPOSITION_REEL = BLOCK_ENTITIES.register(
            "composition_reel",
            () -> new BlockEntityType<>(CompositionReelBlockEntity::new, ModBlocks.COMPOSITION_REEL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mnemolith.content.block.ArchiveVaultBlockEntity>> ARCHIVE_VAULT = BLOCK_ENTITIES.register(
            "archive_vault",
            () -> new BlockEntityType<>(com.mnemolith.content.block.ArchiveVaultBlockEntity::new, ModBlocks.ARCHIVE_VAULT.get()));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
