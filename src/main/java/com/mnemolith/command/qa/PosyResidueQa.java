package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.content.ModItems;
import com.mnemolith.content.item.RemembrancePosy;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.echo.storm.RecollectionStorm;
import com.mnemolith.echo.storm.StormData;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith posy2qa}. The remembrance posy on residual echoes: an unread ordinary residue is laid to rest and
 * leaves nothing (no item, no imprint written back), one posy spent; a storm residue is laid to rest and the storm
 * drops it from its list; a legendary residue refuses and keeps the posy; creative keeps the posy; a removed residue
 * takes nothing; a non-posy item does nothing.
 */
public final class PosyResidueQa {
    private static final String[] NAMES = {"restsUnread", "leavesNothing", "spends", "stormLoses", "legendRefuses",
            "creativeKeeps", "deadIgnored", "needsPosy"};
    private static final UUID PLAYER = UUID.fromString("88888888-1111-2222-3333-aaaaaaaaaaaa");
    private static int salt;

    private PosyResidueQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        BlockPos a = column(level, (spawn.getX() >> 4) + 230 + salt * 3, (spawn.getZ() >> 4) - 120);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "PosyResidueQa"));
        List<Entity> extras = new ArrayList<>();
        StormData data = StormData.get(level.getServer());
        RecollectionStorm storm = null;
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() - 6.5D, 0.0F, 0.0F);
            LoadedChunkMemory.clear(level.getChunkAt(a));
            ItemStack posies = new ItemStack(ModItems.REMEMBRANCE_POSY.get(), 4);

            ResidueEntity plain = spawn(level, a.offset(1, 0, 0), extras);
            boolean unread = plain != null && !plain.isPinned();
            RemembrancePosy.Result rest = plain == null ? RemembrancePosy.Result.NONE : RemembrancePosy.offerResidue(level, player, plain, posies);
            ok[0] = unread && rest == RemembrancePosy.Result.RESTED && plain.isRemoved();
            int items = level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(6.0D)).size();
            var memory = LoadedChunkMemory.existing(level.getChunkAt(a));
            ok[1] = items == 0 && (memory == null || memory.imprintCount() == 0);
            ok[2] = posies.getCount() == 3;
            notes.add("rest=" + rest + " items=" + items + " posies=" + posies.getCount());

            ResidueEntity stormy = spawn(level, a.offset(-1, 0, 0), extras);
            if (stormy != null) {
                storm = Storms.start(level, ChunkPos.containing(a), "qa");
                Storms.adopt(storm, stormy);
                boolean listed = storm.residues().contains(stormy.getUUID());
                RemembrancePosy.Result calm = RemembrancePosy.offerResidue(level, player, stormy, posies);
                ok[3] = listed && calm == RemembrancePosy.Result.RESTED && stormy.isRemoved() && Storms.living(level, storm).isEmpty()
                        && !storm.residues().contains(stormy.getUUID()) && posies.getCount() == 2;
            }

            ResidueEntity legend = spawn(level, a.offset(0, 0, 2), extras);
            if (legend != null) {
                legend.markLegend(PLAYER);
                ok[4] = RemembrancePosy.offerResidue(level, player, legend, posies) == RemembrancePosy.Result.NONE && legend.isAlive()
                        && posies.getCount() == 2;
            }

            player.setGameMode(GameType.CREATIVE);
            ResidueEntity another = spawn(level, a.offset(2, 0, 1), extras);
            ok[5] = another != null && RemembrancePosy.offerResidue(level, player, another, posies) == RemembrancePosy.Result.RESTED
                    && posies.getCount() == 2;
            player.setGameMode(GameType.SURVIVAL);

            ResidueEntity dead = spawn(level, a.offset(-2, 0, 1), extras);
            if (dead != null) {
                dead.discard();
                ok[6] = RemembrancePosy.offerResidue(level, player, dead, posies) == RemembrancePosy.Result.NONE && posies.getCount() == 2;
            }

            ResidueEntity kept = spawn(level, a.offset(0, 0, -2), extras);
            ok[7] = kept != null && RemembrancePosy.offerResidue(level, player, kept, new ItemStack(ModItems.RECOLLITE_SHARD.get()))
                    == RemembrancePosy.Result.NONE && kept.isAlive();
        } finally {
            if (storm != null) {
                Storms.finish(level, storm, Storms.End.PASSED);
            }
            data.removeLulls(lull -> lull.chunkX() == (a.getX() >> 4) && lull.chunkZ() == (a.getZ() >> 4));
            for (Entity entity : extras) {
                if (entity.isAlive()) {
                    entity.discard();
                }
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(6.0D))) {
                item.discard();
            }
            player.setGameMode(GameType.SURVIVAL);
            releaseColumn(level, ChunkPos.containing(a));
        }
        return new QaReport("posy2qa", NAMES, ok, notes);
    }

    private static ResidueEntity spawn(ServerLevel level, BlockPos pos, List<Entity> extras) {
        ResidueEntity residue = Residues.spawn(level, pos, ImprintTag.FALL, 3, false);
        if (residue != null) {
            residue.setNoAi(true);
            extras.add(residue);
        }
        return residue;
    }
}
