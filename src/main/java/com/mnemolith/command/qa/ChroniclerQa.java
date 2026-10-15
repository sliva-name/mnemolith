package com.mnemolith.command.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.content.item.TranscriptItem;
import com.mnemolith.content.villager.ChroniclerOffers;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.data.Transcript;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ModAttachments;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith chroniclerqa}. The chronicler's copied notes: the trade is in the journeyman (level 3) pool; a level
 * 3 chronicler offers it (10 emeralds and a paper, 2 uses) and the sheet holds the five body tags, no formula, signed
 * by the chronicler; the author shows as the profession name, a player's as their name; a new reader learns 5, one who
 * knew two learns 3, one who knows all keeps the sheet; a level 1 chronicler never offers it; the guide page says so.
 */
public final class ChroniclerQa {
    private static final String[] NAMES = {"tradePooled", "offered", "sheetNotes", "authorName", "readFull", "readPartial",
            "knowsAllKeeps", "notNovice", "guideText"};
    private static final UUID READER = UUID.fromString("77777777-1111-2222-3333-aaaaaaaaaaaa");

    private ChroniclerQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var registry = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
        ResourceKey<VillagerTrade> key = ResourceKey.create(Registries.VILLAGER_TRADE,
                Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chronicler/3/emerald_and_paper_chronicler_transcript"));
        var tag = registry.get(net.minecraft.tags.TagKey.create(Registries.VILLAGER_TRADE, Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chronicler/level_3")));
        ok[0] = registry.get(key).isPresent() && tag.isPresent() && tag.get().stream().anyMatch(holder -> holder.is(key));

        BlockPos at = spawn.offset(0, 0, -40);
        MerchantOffer offer = null;
        int tries = 0;
        while (offer == null && tries < 30) {
            tries++;
            offer = ChroniclerOffers.transcriptOffer(ChroniclerOffers.chronicler(level, at, 3, false));
        }
        ok[1] = offer != null && offer.getBaseCostA().is(Items.EMERALD) && offer.getBaseCostA().getCount() == 10
                && offer.getCostB().is(Items.PAPER) && offer.getMaxUses() == 2;
        ItemStack bought = offer == null ? ItemStack.EMPTY : offer.assemble();
        Transcript sheet = bought.get(ModDataComponents.TRANSCRIPT.get());
        ok[2] = sheet != null && sheet.tags() == Transcript.CHRONICLER_TAGS && sheet.formulas() == 0 && sheet.byChronicler()
                && sheet.tagCount() == 5;
        notes.add("tries=" + tries + " offer=" + (offer != null) + " sheet=" + sheet);

        Transcript byPlayer = new Transcript(1, 0, "Steve");
        ok[3] = sheet != null && sheet.authorName().getContents() instanceof TranslatableContents t
                && t.getKey().equals("entity.mnemolith.villager.chronicler")
                && byPlayer.authorName().getContents() instanceof PlainTextContents.LiteralContents lit && lit.text().equals("Steve");

        FakePlayer reader = FakePlayerFactory.get(level, new GameProfile(READER, "ChroniclerReader"));
        try {
            reader.setGameMode(GameType.SURVIVAL);
            reader.setData(ModAttachments.DISCOVERY.get(), new Discovery());
            ItemStack one = bought.copy();
            int full = one.isEmpty() ? -1 : TranscriptItem.read(reader, one);
            Discovery after = reader.getData(ModAttachments.DISCOVERY.get());
            ok[4] = full == 5 && one.isEmpty() && after.tags() == Transcript.CHRONICLER_TAGS && after.formulas() == 0;

            Discovery partial = new Discovery();
            partial.noteTag(ImprintTag.FIRE);
            partial.noteTag(ImprintTag.DEATH);
            reader.setData(ModAttachments.DISCOVERY.get(), partial);
            ItemStack two = bought.copy();
            ok[5] = !two.isEmpty() && TranscriptItem.read(reader, two) == 3 && two.isEmpty();

            ItemStack three = bought.copy();
            ok[6] = !three.isEmpty() && TranscriptItem.read(reader, three) == 0 && three.getCount() == 1;
            notes.add("full=" + full + " tags=" + after.tags());
        } finally {
            reader.setData(ModAttachments.DISCOVERY.get(), new Discovery());
        }

        boolean novice = true;
        for (int i = 0; i < 10; i++) {
            Villager first = ChroniclerOffers.chronicler(level, at, 1, false);
            novice &= ChroniclerOffers.transcriptOffer(first) == null;
        }
        ok[7] = novice;

        String body = net.minecraft.network.chat.Component.translatable("mnemolith.guide.transcript.body").getString();
        ok[8] = body.contains("10 emeralds") && GuideBook.pageCount() == GuideBook.PAGE_COUNT
                && new ItemStack(ModItems.CHRONICLE_TRANSCRIPT.get()).getMaxStackSize() == 16;
        return new QaReport("chroniclerqa", NAMES, ok, notes);
    }
}
