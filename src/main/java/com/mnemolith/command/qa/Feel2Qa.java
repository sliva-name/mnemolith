package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.echo.storm.ScarSites;
import com.mnemolith.echo.storm.StormData;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.MemoryFeel;
import com.mnemolith.particle.MemoryFeel.Kind;
import com.mnemolith.particle.MemoryFx;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith feel2qa}. Memory feel, stage 2: the older systems speak the shared language. Writing is a WRITTEN
 * cue, extraction CAUGHT, a mute stone KEPT, a residue dissolving ERASED, a storm settling FORGOTTEN, a mended scar site
 * KEPT; the throttled cue used by Hollows forgetting and lantern keeping sends at most one cue per kind and chunk per
 * window, while another kind or chunk still sends. The live test {@code write_cue_reaches_player} writes by hand.
 */
public final class Feel2Qa {
    private static final String[] NAMES = {"writeWritten", "extractCaught", "muteKept", "residueErased", "stormForgotten", "mendKept",
            "throttleHolds", "throttleOthers", "throttleWindow"};
    private static final UUID PLAYER = UUID.fromString("66666666-2222-2222-3333-aeaeaeaeaeae");
    private static int salt;

    private Feel2Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        BlockPos a = column(level, (spawn.getX() >> 4) + 610 + salt * 3, (spawn.getZ() >> 4) - 600);
        tickColumn(level, a);
        BlockPos heart = a.offset(3, 0, 3);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Feel2Qa"));
        ResidueEntity residue = null;
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() - 2.5D, 0.0F, 0.0F);
            QaSupport.clear(level, a);

            int written = MemoryFeel.sent(Kind.WRITTEN);
            ImprintWriter.write(level, a, List.of(ImprintTag.BUILD), null, false);
            ok[0] = MemoryFeel.sent(Kind.WRITTEN) == written + 1 && MemoryFeel.lastPos().closerThan(a, 2.0D);

            int caught = MemoryFeel.sent(Kind.CAUGHT);
            ok[1] = ImprintWriter.extract(level, a, player).isPresent() && MemoryFeel.sent(Kind.CAUGHT) == caught + 1;

            int kept = MemoryFeel.sent(Kind.KEPT);
            MemoryFx.mute(level, a);
            ok[2] = MemoryFeel.sent(Kind.KEPT) == kept + 1;

            residue = ModEntities.RESIDUE.get().create(level, EntitySpawnReason.COMMAND);
            if (residue != null) {
                residue.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
                level.addFreshEntity(residue);
                int erased = MemoryFeel.sent(Kind.ERASED);
                Residues.dissolve(level, residue);
                ok[3] = MemoryFeel.sent(Kind.ERASED) == erased + 1 && residue.isRemoved();
            }

            int forgotten = MemoryFeel.sent(Kind.FORGOTTEN);
            ChunkPos cp = ChunkPos.containing(a);
            Storms.settle(level, new StormData.Faded(level.dimension(), cp.x(), cp.z(), 0L));
            ok[4] = MemoryFeel.sent(Kind.FORGOTTEN) == forgotten + 1;

            var chunk = level.getChunkAt(heart);
            var memory = LoadedChunkMemory.getOrCreate(chunk);
            memory.setScar(new ScarSite(1, 0L, ScarSites.TENDINGS - 1, level.getGameTime() - ScarSites.RESEED_TICKS * 2, false));
            level.setBlock(heart, ModBlocks.SCAR_HEART.get().defaultBlockState(), 3);
            kept = MemoryFeel.sent(Kind.KEPT);
            ScarSites.Tend tend = ScarSites.tend(level, player, heart, new ItemStack(com.mnemolith.content.ModItems.REMEMBRANCE_POSY.get(), 2));
            ok[5] = tend == ScarSites.Tend.MENDED && MemoryFeel.sent(Kind.KEPT) == kept + 1 && MemoryFeel.lastPos().closerThan(heart, 2.0D);
            notes.add("tend=" + tend);

            BlockPos t = a.offset(0, 0, 0);
            boolean first = MemoryFeel.cueThrottled(level, Kind.FORGOTTEN, t, 4, 20);
            int held = MemoryFeel.throttledCount();
            boolean second = MemoryFeel.cueThrottled(level, Kind.FORGOTTEN, t.offset(3, 0, 3), 4, 20);
            ok[6] = first && !second && MemoryFeel.throttledCount() == held + 1;
            ok[7] = MemoryFeel.cueThrottled(level, Kind.KEPT, t, 3, 60) && MemoryFeel.cueThrottled(level, Kind.FORGOTTEN, t.offset(16, 0, 0), 4, 20);
            ok[8] = MemoryFeel.cueThrottled(level, Kind.FORGOTTEN, t, 4, 0);
        } finally {
            if (residue != null && !residue.isRemoved()) {
                residue.discard();
            }
            level.setBlock(heart, Blocks.AIR.defaultBlockState(), 3);
            LoadedChunkMemory.clear(level.getChunkAt(heart));
            QaSupport.clear(level, a);
            releaseColumn(level, ChunkPos.containing(a));
        }
        return new QaReport("feel2qa", NAMES, ok, notes);
    }
}
