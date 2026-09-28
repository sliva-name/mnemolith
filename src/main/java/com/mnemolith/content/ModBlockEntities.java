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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mnemolith.content.block.SelectiveMuteStoneBlockEntity>> SELECTIVE_MUTE_STONE = BLOCK_ENTITIES.register(
            "selective_mute_stone",
            () -> new BlockEntityType<>(com.mnemolith.content.block.SelectiveMuteStoneBlockEntity::new, ModBlocks.SELECTIVE_MUTE_STONE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mnemolith.content.block.PlayerMemorialBlockEntity>> PLAYER_MEMORIAL = BLOCK_ENTITIES.register(
            "player_memorial",
            () -> new BlockEntityType<>(com.mnemolith.content.block.PlayerMemorialBlockEntity::new, ModBlocks.PLAYER_MEMORIAL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.mnemolith.content.block.EchoHomeBlockEntity>> ECHO_HOME = BLOCK_ENTITIES.register(
            "echo_home",
            () -> new BlockEntityType<>(com.mnemolith.content.block.EchoHomeBlockEntity::new, ModBlocks.ECHO_HOME.get()));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
