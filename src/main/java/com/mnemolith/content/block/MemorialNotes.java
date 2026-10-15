package com.mnemolith.content.block;

import com.mnemolith.content.item.TranscriptItem;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.data.Transcript;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Notes carved into a memorial stone: a written chronicle transcript used on the stone is carved in (its tags and
 * formulas join what is there; the sheet is spent, not in creative). Anyone who touches the stone with an empty hand
 * learns what is carved, as often as they like; nothing is spent. Knowledge only, like the transcript.
 */
public final class MemorialNotes {
    public enum Read { NONE, BLANK, LEARNED, KNOWN }

    private MemorialNotes() {}

    /** Carves {@code sheet} into the memorial at {@code pos}. False when it is no memorial or the sheet is blank. */
    public static boolean inscribe(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack sheet) {
        Transcript notes = sheet.get(ModDataComponents.TRANSCRIPT.get());
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        ScarSite site = memory == null ? null : memory.scar();
        if (notes == null || site == null || !site.memorial()) {
            return false;
        }
        ScarSite carved = site.inscribe(notes);
        memory.setScar(carved);
        chunk.markUnsaved();
        if (!player.isCreative()) {
            sheet.shrink(1);
        }
        Transcript all = carved.inscription().orElse(notes);
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D, 20, 0.3D, 0.3D, 0.3D, 0.2D);
        player.sendOverlayMessage(Component.translatable("mnemolith.memorial.inscribed", all.tagCount(), all.formulaCount()));
        return true;
    }

    /** {@code player} touches the memorial at {@code pos}: learns what is carved. Public for QA. */
    public static Read read(ServerLevel level, ServerPlayer player, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        ScarSite site = memory == null ? null : memory.scar();
        if (site == null || !site.memorial()) {
            return Read.NONE;
        }
        if (site.inscription().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("mnemolith.memorial.blank"));
            return Read.BLANK;
        }
        Transcript notes = site.inscription().get();
        int learned = TranscriptItem.learn(player, notes);
        if (learned > 0) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
            player.sendOverlayMessage(Component.translatable("mnemolith.memorial.learned", learned, notes.authorName()));
            return Read.LEARNED;
        }
        player.sendOverlayMessage(Component.translatable("mnemolith.memorial.known", notes.authorName()));
        return Read.KNOWN;
    }
}
