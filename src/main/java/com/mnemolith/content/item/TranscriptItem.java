package com.mnemolith.content.item;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.data.Transcript;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.DiscoveryNotes;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The chronicle transcript. A blank one, used, copies what you have discovered (tags and formulas, the bits your
 * catalog draws) onto one sheet, signed with your name. A written one, used by anyone who does not know all of it yet,
 * teaches them the rest and is used up. Knowledge only: it unlocks catalog entries, never slips or grafts.
 */
public class TranscriptItem extends Item {
    public TranscriptItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!CommonConfig.CATALOG_ENABLED.get()) {
            serverPlayer.sendOverlayMessage(Component.translatable("mnemolith.message.catalog_disabled"));
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        Transcript written = stack.get(ModDataComponents.TRANSCRIPT.get());
        if (written == null) {
            ItemStack copy = write(serverPlayer, stack);
            if (copy.isEmpty()) {
                serverPlayer.sendOverlayMessage(Component.translatable("item.mnemolith.chronicle_transcript.nothing"));
                return InteractionResult.FAIL;
            }
            Transcript t = copy.get(ModDataComponents.TRANSCRIPT.get());
            serverPlayer.sendOverlayMessage(Component.translatable("item.mnemolith.chronicle_transcript.written", t.tagCount(), t.formulaCount()));
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        int learned = read(serverPlayer, stack);
        if (learned == 0) {
            serverPlayer.sendOverlayMessage(Component.translatable("item.mnemolith.chronicle_transcript.known"));
            return InteractionResult.FAIL;
        }
        serverPlayer.sendOverlayMessage(Component.translatable("item.mnemolith.chronicle_transcript.learned", learned, written.authorName()));
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        return InteractionResult.SUCCESS;
    }

    /**
     * Writes {@code player}'s discovery onto one sheet taken from {@code blank} (the rest of the stack stays blank). The
     * written sheet replaces a single blank in hand, or goes to the inventory (dropped if full). Returns the written
     * sheet, or empty when there is nothing to write.
     */
    public static ItemStack write(ServerPlayer player, ItemStack blank) {
        Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
        if (discovery.tags() == 0 && discovery.formulas() == 0) {
            return ItemStack.EMPTY;
        }
        Transcript notes = new Transcript(discovery.tags(), discovery.formulas(), player.getGameProfile().name());
        if (blank.getCount() == 1) {
            blank.set(ModDataComponents.TRANSCRIPT.get(), notes);
            return blank;
        }
        ItemStack sheet = blank.split(1);
        sheet.set(ModDataComponents.TRANSCRIPT.get(), notes);
        ItemStack written = sheet.copy();
        // Inventory.add moves the stack (leaving it empty), so hand back the copy
        if (!player.getInventory().add(sheet)) {
            player.drop(sheet, false);
        }
        return written;
    }

    /** {@code player} reads a written {@code sheet}: returns how many tags and formulas were new; spends it if any. */
    public static int read(ServerPlayer player, ItemStack sheet) {
        Transcript notes = sheet.get(ModDataComponents.TRANSCRIPT.get());
        if (notes == null) {
            return 0;
        }
        Discovery discovery = player.getData(ModAttachments.DISCOVERY.get());
        int learned = 0;
        for (ImprintTag tag : ImprintTag.values()) {
            if ((notes.tags() & (1 << tag.ordinal())) != 0 && !discovery.hasTag(tag)) {
                DiscoveryNotes.noteTag(player, tag);
                learned++;
            }
        }
        for (int i = 0; i < Discovery.FORMULA_COUNT; i++) {
            if ((notes.formulas() & (1 << i)) != 0 && !discovery.hasFormula(i)) {
                DiscoveryNotes.noteFormula(player, i);
                learned++;
            }
        }
        if (learned > 0 && !player.isCreative()) {
            sheet.shrink(1);
        }
        return learned;
    }
}
