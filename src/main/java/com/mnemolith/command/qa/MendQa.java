package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.echo.storm.ScarSites;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith mendqa}. Mending the Scar, stage 1: old site data loads with no mending; a posy tends the heart
 * once (posy spent, that day's seeding reset); a second tending the same day is refused; the third (a day apart) mends
 * the site (memorial stone, memorial site, still storm-proof); a memorial seeds no residue; breaking the stone forgets
 * the site; no heart or no posy does nothing; creative keeps the posy; the guide page follows the Scar page.
 */
public final class MendQa {
    private static final String[] NAMES = {"codecCompat", "tendOnce", "tooSoon", "thirdMends", "memorialNoSeed", "breakForgets",
            "noHeart", "creativeKeeps", "needsPosy", "guidePage"};
    private static final UUID PLAYER = UUID.fromString("99999999-1111-2222-3333-aaaaaaaaaaaa");
    private static int salt;

    private MendQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();

        ScarSite old = ScarSite.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"tempers\":3,\"seeded_at\":77}")).getOrThrow();
        ScarSite full = new ScarSite(5, 10L, 2, 20L, true);
        ok[0] = old.tempers() == 3 && old.seededAt() == 77L && old.tended() == 0 && !old.memorial()
                && ScarSite.CODEC.parse(JsonOps.INSTANCE, ScarSite.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow()).getOrThrow().equals(full);

        BlockPos a = column(level, (spawn.getX() >> 4) + 250 + salt * 3, (spawn.getZ() >> 4) - 150);
        tickColumn(level, a);
        LevelChunk chunk = level.getChunkAt(a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "MendQa"));
        BlockPos heart = a.offset(0, 0, 0);
        try {
            player.setGameMode(GameType.SURVIVAL);
            LoadedChunkMemory.clear(chunk);
            ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
            long start = level.getGameTime();
            memory.setScar(new ScarSite(1, start - ScarSites.RESEED_TICKS));
            level.setBlock(heart, ModBlocks.SCAR_HEART.get().defaultBlockState(), 3);
            ItemStack posies = new ItemStack(ModItems.REMEMBRANCE_POSY.get(), 5);

            ScarSites.Tend first = ScarSites.tend(level, player, heart, posies);
            ScarSite s1 = memory.scar();
            ok[1] = first == ScarSites.Tend.TENDED && s1 != null && s1.tended() == 1 && s1.seededAt() == start && posies.getCount() == 4;

            ScarSites.Tend again = ScarSites.tend(level, player, heart, posies);
            ok[2] = again == ScarSites.Tend.TOO_SOON && memory.scar().tended() == 1 && posies.getCount() == 4;

            memory.setScar(back(memory.scar()));
            ScarSites.Tend second = ScarSites.tend(level, player, heart, posies);
            memory.setScar(back(memory.scar()));
            ScarSites.Tend third = ScarSites.tend(level, player, heart, posies);
            ScarSite mended = memory.scar();
            ok[3] = second == ScarSites.Tend.TENDED && third == ScarSites.Tend.MENDED && mended != null && mended.memorial()
                    && level.getBlockState(heart).is(ModBlocks.MEMORIAL_STONE.get()) && memory.stormProof() && posies.getCount() == 2;
            notes.add("tends " + first + "," + again + "," + second + "," + third + " posies=" + posies.getCount());

            memory.setScar(mended.withSeededAt(level.getGameTime() - ScarSites.RESEED_TICKS * 2));
            ok[4] = ScarSites.reseed(level, chunk, memory) == null
                    && ScarSites.tend(level, player, heart, posies) == ScarSites.Tend.NONE && posies.getCount() == 2;

            level.setBlock(heart, Blocks.AIR.defaultBlockState(), 3);
            ok[5] = memory.scar() == null;

            memory.setScar(new ScarSite(1, start));
            ok[6] = ScarSites.tend(level, player, heart, posies) == ScarSites.Tend.NONE && posies.getCount() == 2;

            level.setBlock(heart, ModBlocks.SCAR_HEART.get().defaultBlockState(), 3);
            memory.setScar(new ScarSite(1, start));
            player.setGameMode(GameType.CREATIVE);
            ok[7] = ScarSites.tend(level, player, heart, posies) == ScarSites.Tend.TENDED && posies.getCount() == 2;
            player.setGameMode(GameType.SURVIVAL);

            memory.setScar(back(memory.scar()));
            ok[8] = ScarSites.tend(level, player, heart, new ItemStack(ModItems.RECOLLITE_SHARD.get())) == ScarSites.Tend.NONE
                    && memory.scar().tended() == 1;
        } finally {
            player.setGameMode(GameType.SURVIVAL);
            level.setBlock(heart, Blocks.AIR.defaultBlockState(), 3);
            LoadedChunkMemory.clear(chunk);
            releaseColumn(level, ChunkPos.containing(a));
        }

        int scarPage = -1;
        int mendPage = -1;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (GuideBook.pageId(i).equals("scar")) {
                scarPage = i;
            } else if (GuideBook.pageId(i).equals("mending")) {
                mendPage = i;
            }
        }
        ok[9] = scarPage >= 0 && mendPage == scarPage + 1 && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("mendqa", NAMES, ok, notes);
    }

    /** The same site with its last tending a full day ago. */
    private static ScarSite back(ScarSite site) {
        return new ScarSite(site.tempers(), site.seededAt(), site.tended(), site.tendedAt() - ScarSites.RESEED_TICKS, site.memorial());
    }
}
