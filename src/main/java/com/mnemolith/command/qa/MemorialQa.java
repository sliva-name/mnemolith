package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.MemorialNotes;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.data.Transcript;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith memorialqa}. Notes carved into a memorial: the inscription saves and old sites load without one; a
 * blank memorial says so; a written sheet is carved in and spent; a second joins it (bits OR, newest author); a reader
 * learns the carved entries and the stone keeps them, a second read finds nothing new; a Scar heart site takes and
 * gives nothing; a blank sheet is refused; creative keeps the sheet; the guide page tells it.
 */
public final class MemorialQa {
    private static final String[] NAMES = {"codec", "blankStone", "carves", "joins", "readKeeps", "notMemorial", "blankSheet",
            "creativeKeeps", "guideText"};
    private static final UUID CARVER = UUID.fromString("aaaaaaaa-1111-2222-3333-aaaaaaaaaaaa");
    private static final UUID READER = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");
    private static int salt;

    private MemorialQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        ScarSite carvedSite = new ScarSite(1, 2L, 3, 4L, true, Optional.of(new Transcript(5, 2, "Ann")));
        ScarSite old = ScarSite.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"tempers\":1,\"memorial\":true}")).getOrThrow();
        ok[0] = old.inscription().isEmpty() && old.memorial()
                && ScarSite.CODEC.parse(JsonOps.INSTANCE, ScarSite.CODEC.encodeStart(JsonOps.INSTANCE, carvedSite).getOrThrow()).getOrThrow().equals(carvedSite);

        BlockPos a = column(level, (spawn.getX() >> 4) + 290 + salt * 3, (spawn.getZ() >> 4) - 210);
        tickColumn(level, a);
        LevelChunk chunk = level.getChunkAt(a);
        FakePlayer carver = FakePlayerFactory.get(level, new GameProfile(CARVER, "MemorialCarver"));
        FakePlayer reader = FakePlayerFactory.get(level, new GameProfile(READER, "MemorialReader"));
        try {
            carver.setGameMode(GameType.SURVIVAL);
            reader.setGameMode(GameType.SURVIVAL);
            reader.setData(ModAttachments.DISCOVERY.get(), new Discovery());
            LoadedChunkMemory.clear(chunk);
            ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
            level.setBlock(a, ModBlocks.MEMORIAL_STONE.get().defaultBlockState(), 3);
            memory.setScar(new ScarSite(1, level.getGameTime(), 3, level.getGameTime(), true));

            ok[1] = MemorialNotes.read(level, reader, a) == MemorialNotes.Read.BLANK;

            ItemStack sheets = sheet(new Transcript(1 | 4, 2, "Ann"), 2);
            ok[2] = MemorialNotes.inscribe(level, carver, a, sheets) && sheets.getCount() == 1
                    && memory.scar().inscription().equals(Optional.of(new Transcript(5, 2, "Ann")));

            ItemStack other = sheet(new Transcript(32, 1, "Bo"), 1);
            ok[3] = MemorialNotes.inscribe(level, carver, a, other) && other.isEmpty()
                    && memory.scar().inscription().equals(Optional.of(new Transcript(37, 3, "Bo")));

            MemorialNotes.Read first = MemorialNotes.read(level, reader, a);
            Discovery learned = reader.getData(ModAttachments.DISCOVERY.get());
            MemorialNotes.Read second = MemorialNotes.read(level, reader, a);
            ok[4] = first == MemorialNotes.Read.LEARNED && learned.tags() == 37 && learned.formulas() == 3
                    && second == MemorialNotes.Read.KNOWN && memory.scar().inscription().isPresent();
            notes.add("read " + first + "," + second + " tags=" + learned.tags() + " formulas=" + learned.formulas());

            ItemStack blank = new ItemStack(ModItems.CHRONICLE_TRANSCRIPT.get());
            ok[6] = !MemorialNotes.inscribe(level, carver, a, blank) && blank.getCount() == 1;

            carver.setGameMode(GameType.CREATIVE);
            ItemStack kept = sheet(new Transcript(2, 0, "Cy"), 1);
            ok[7] = MemorialNotes.inscribe(level, carver, a, kept) && kept.getCount() == 1;
            carver.setGameMode(GameType.SURVIVAL);

            level.setBlock(a, ModBlocks.SCAR_HEART.get().defaultBlockState(), 3);
            memory.setScar(new ScarSite(1, level.getGameTime()));
            ItemStack refused = sheet(new Transcript(2, 0, "Cy"), 1);
            ok[5] = !MemorialNotes.inscribe(level, carver, a, refused) && refused.getCount() == 1
                    && MemorialNotes.read(level, reader, a) == MemorialNotes.Read.NONE;
        } finally {
            carver.setGameMode(GameType.SURVIVAL);
            reader.setData(ModAttachments.DISCOVERY.get(), new Discovery());
            level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
            LoadedChunkMemory.clear(chunk);
            releaseColumn(level, ChunkPos.containing(a));
        }
        ok[8] = Component.translatable("mnemolith.guide.mending.body").getString().contains("carve");
        return new QaReport("memorialqa", NAMES, ok, notes);
    }

    private static ItemStack sheet(Transcript notes, int count) {
        ItemStack stack = new ItemStack(ModItems.CHRONICLE_TRANSCRIPT.get(), count);
        stack.set(ModDataComponents.TRANSCRIPT.get(), notes);
        return stack;
    }
}
