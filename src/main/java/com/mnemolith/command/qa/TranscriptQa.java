package com.mnemolith.command.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.content.item.TranscriptItem;
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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith transcriptqa}. The chronicle transcript: item and recipe; nothing to write with no discovery; a
 * written sheet carries the author's tags, formulas and name and leaves the rest of the stack blank; a reader learns
 * only what is new and spends the sheet; the author learns nothing and keeps it; creative keeps it; knowledge only (no
 * slip on the sheet); the guide page. Two fake players, no world blocks touched.
 */
public final class TranscriptQa {
    private static final String[] NAMES = {"itemData", "recipe", "nothingToWrite", "writeCarries", "stackSplits", "readTeaches",
            "readSpends", "partialNew", "authorKeeps", "creativeKeeps", "knowledgeOnly", "guidePage"};
    private static final UUID AUTHOR = UUID.fromString("77777777-1111-2222-3333-bbbbbbbbbbbb");
    private static final UUID READER = UUID.fromString("77777777-1111-2222-3333-cccccccccccc");

    private TranscriptQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ok[0] = new ItemStack(ModItems.CHRONICLE_TRANSCRIPT.get()).getMaxStackSize() == 16;
        Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("chronicle_transcript")));
        if (recipe.isPresent() && recipe.get().value() instanceof CraftingRecipe crafting) {
            CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.FEATHER), new ItemStack(Items.PAPER), ItemStack.EMPTY,
                    new ItemStack(Items.INK_SAC)));
            ok[1] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.CHRONICLE_TRANSCRIPT.get())
                    && server.getAdvancements().get(id("recipes/chronicle_transcript")) != null;
        }

        FakePlayer author = FakePlayerFactory.get(level, new GameProfile(AUTHOR, "TranscriptAuthor"));
        FakePlayer reader = FakePlayerFactory.get(level, new GameProfile(READER, "TranscriptReader"));
        try {
            for (FakePlayer p : List.of(author, reader)) {
                p.setGameMode(GameType.SURVIVAL);
                p.getInventory().clearContent();
                p.setData(ModAttachments.DISCOVERY.get(), new Discovery());
            }
            ItemStack blanks = new ItemStack(ModItems.CHRONICLE_TRANSCRIPT.get(), 3);
            ok[2] = TranscriptItem.write(author, blanks).isEmpty() && blanks.getCount() == 3 && !blanks.has(ModDataComponents.TRANSCRIPT.get());

            Discovery known = author.getData(ModAttachments.DISCOVERY.get());
            known.noteTag(ImprintTag.FIRE);
            known.noteTag(ImprintTag.SILENCE);
            known.noteTag(ImprintTag.PORTAL);
            known.noteFormula(2);
            known.noteFormula(5);
            ItemStack sheet = TranscriptItem.write(author, blanks);
            Transcript t = sheet.get(ModDataComponents.TRANSCRIPT.get());
            ok[3] = t != null && t.tags() == known.tags() && t.formulas() == known.formulas() && t.author().equals("TranscriptAuthor")
                    && t.tagCount() == 3 && t.formulaCount() == 2;
            ok[4] = blanks.getCount() == 2 && !blanks.has(ModDataComponents.TRANSCRIPT.get()) && sheet.getCount() == 1 && sheet != blanks;
            notes.add("sheet=" + t + " blanks=" + blanks.getCount());

            // the reader already knows fire: learns silence, portal and two formulas
            reader.getData(ModAttachments.DISCOVERY.get()).noteTag(ImprintTag.FIRE);
            ItemStack readerSheet = sheet.copy();
            int learned = TranscriptItem.read(reader, readerSheet);
            Discovery after = reader.getData(ModAttachments.DISCOVERY.get());
            ok[5] = after.hasTag(ImprintTag.SILENCE) && after.hasTag(ImprintTag.PORTAL) && after.hasFormula(2) && after.hasFormula(5)
                    && !after.hasTag(ImprintTag.DEATH);
            ok[6] = readerSheet.isEmpty();
            ok[7] = learned == 4;
            notes.add("learned=" + learned);

            ItemStack own = sheet.copy();
            ok[8] = TranscriptItem.read(author, own) == 0 && own.getCount() == 1;

            reader.setData(ModAttachments.DISCOVERY.get(), new Discovery());
            reader.setGameMode(GameType.CREATIVE);
            ItemStack creative = sheet.copy();
            ok[9] = TranscriptItem.read(reader, creative) == 5 && creative.getCount() == 1;
            reader.setGameMode(GameType.SURVIVAL);
            ok[10] = !sheet.has(ModDataComponents.IMPRINT_CAST.get()) && TranscriptItem.read(reader, new ItemStack(ModItems.CHRONICLE_TRANSCRIPT.get())) == 0;
        } finally {
            for (FakePlayer p : List.of(author, reader)) {
                p.setGameMode(GameType.SURVIVAL);
                p.getInventory().clearContent();
                p.setData(ModAttachments.DISCOVERY.get(), new Discovery());
            }
        }

        int catalogPage = -1;
        int transcriptPage = -1;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (GuideBook.pageId(i).equals("catalog")) {
                catalogPage = i;
            } else if (GuideBook.pageId(i).equals("transcript")) {
                transcriptPage = i;
            }
        }
        ok[11] = catalogPage >= 0 && transcriptPage == catalogPage + 1 && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("transcriptqa", NAMES, ok, notes);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
