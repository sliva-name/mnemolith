package com.mnemolith.imprint;

import com.mnemolith.Mnemolith;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Chunk memory stays on the server. Discovery is saved on the player and synced only to that player.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Mnemolith.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ChunkMemory>> CHUNK_MEMORY = ATTACHMENT_TYPES.register(
            "chunk_memory",
            () -> AttachmentType.builder(ChunkMemory::new).serialize(ChunkMemory.CODEC, memory -> !memory.isEmpty()).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Discovery>> DISCOVERY = ATTACHMENT_TYPES.register(
            "discovery",
            () -> AttachmentType.builder(Discovery::new)
                    .serialize(Discovery.CODEC, discovery -> !discovery.isEmpty())
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player, Discovery.STREAM_CODEC)
                    .build());

    /** Real body of a player who is inside an echo. Saved with the player; kept through death so nothing is lost. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.mnemolith.echo.PossessionState>> ECHO_POSSESSION = ATTACHMENT_TYPES.register(
            "echo_possession",
            () -> AttachmentType.builder(() -> new com.mnemolith.echo.PossessionState())
                    .serialize(com.mnemolith.echo.PossessionState.MAP_CODEC, com.mnemolith.echo.PossessionState::isActive)
                    .copyOnDeath()
                    .build());

    /** Stage 3 echo upgrades absorbed by a player. Saved, kept through death, synced only to that player. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.mnemolith.echo.EchoProgress>> ECHO_PROGRESS = ATTACHMENT_TYPES.register(
            "echo_progress",
            () -> AttachmentType.builder(() -> com.mnemolith.echo.EchoProgress.NONE)
                    .serialize(com.mnemolith.echo.EchoProgress.CODEC, progress -> !progress.isEmpty())
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player, com.mnemolith.echo.EchoProgress.STREAM_CODEC)
                    .build());

    private ModAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
