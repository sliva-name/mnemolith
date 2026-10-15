package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.echo.storm.ScarSites;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * {@code /mnemolith mend2qa}. Mending, stage 2, the memorial grove: a memorial grows two forget-me-nots a day on grass
 * near its stone, not again the same day, never past twelve, never on stone; a residue near the stone rests at its
 * fester (gone, nothing written), one far away or legendary does not; a site that is not a memorial calms nothing; the
 * guide page tells it.
 */
public final class Mend2Qa {
    private static final String[] NAMES = {"growsTwo", "dayGate", "cap", "onlyDirt", "restsNear", "farFesters", "legendKept",
            "heartCalmsNothing", "guideText"};
    private static int salt;

    private Mend2Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        BlockPos a = column(level, (spawn.getX() >> 4) + 270 + salt * 3, (spawn.getZ() >> 4) - 180);
        tickColumn(level, a);
        LevelChunk chunk = level.getChunkAt(a);
        List<Entity> extras = new ArrayList<>();
        try {
            LoadedChunkMemory.clear(chunk);
            ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
            platform(level, a, Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlock(a, ModBlocks.MEMORIAL_STONE.get().defaultBlockState(), 3);
            long now = level.getGameTime();
            memory.setScar(new ScarSite(1, now - ScarSites.RESEED_TICKS, 3, now - ScarSites.RESEED_TICKS, true));

            ScarSites.reseed(level, chunk, memory);
            int first = flowers(level, a);
            ok[0] = first == ScarSites.GROVE_PER_DAY;
            ScarSites.reseed(level, chunk, memory);
            ok[1] = flowers(level, a) == first && memory.scar().seededAt() == now;
            notes.add("grove first=" + first + " after=" + flowers(level, a));

            int placed = first;
            for (BlockPos pos : BlockPos.betweenClosed(a.offset(-5, 0, -5), a.offset(5, 0, 5))) {
                if (placed >= ScarSites.GROVE_CAP) {
                    break;
                }
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, ModBlocks.FORGET_ME_NOT.get().defaultBlockState(), 3);
                    placed++;
                }
            }
            ok[2] = ScarSites.grow(level, chunk, memory, memory.scar()) == 0 && flowers(level, a) == ScarSites.GROVE_CAP;

            platform(level, a, Blocks.STONE.defaultBlockState());
            ok[3] = ScarSites.grow(level, chunk, memory, memory.scar()) == 0 && flowers(level, a) == 0;

            ResidueEntity near = residue(level, a.offset(3, 0, 0), extras);
            int imprints = memory.imprintCount();
            Residues.Fester rest = near == null ? null : Residues.fester(level, near);
            ok[4] = rest == Residues.Fester.DISSOLVED && near.isRemoved() && memory.imprintCount() == imprints
                    && ScarSites.nearMemorial(level, a.offset(3, 0, 0));

            ResidueEntity far = residue(level, a.offset(14, 0, 0), extras);
            Residues.Fester farRest = far == null ? null : Residues.fester(level, far);
            ok[5] = far != null && farRest != Residues.Fester.DISSOLVED && far.isAlive() && !ScarSites.nearMemorial(level, a.offset(14, 0, 0));

            ResidueEntity legend = residue(level, a.offset(-3, 0, 0), extras);
            if (legend != null) {
                legend.markLegend(UUID.fromString("99999999-2222-2222-3333-aaaaaaaaaaaa"));
                ok[6] = Residues.fester(level, legend) != Residues.Fester.DISSOLVED && legend.isAlive();
            }
            notes.add("near=" + rest + " far=" + farRest);

            level.setBlock(a, ModBlocks.SCAR_HEART.get().defaultBlockState(), 3);
            memory.setScar(new ScarSite(1, now));
            ok[7] = !ScarSites.nearMemorial(level, a.offset(3, 0, 0));
            level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
        } finally {
            for (Entity entity : extras) {
                if (entity.isAlive()) {
                    entity.discard();
                }
            }
            level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
            platform(level, a, Blocks.GRASS_BLOCK.defaultBlockState());
            LoadedChunkMemory.clear(chunk);
            releaseColumn(level, ChunkPos.containing(a));
        }
        String body = Component.translatable("mnemolith.guide.mending.body").getString();
        ok[8] = body.contains("forget-me-not") && body.contains("rest");
        return new QaReport("mend2qa", NAMES, ok, notes);
    }

    /** A 15x15 floor of {@code ground} under {@code a}, air for four blocks above (the stone spot excepted). */
    private static void platform(ServerLevel level, BlockPos a, BlockState ground) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                level.setBlock(a.offset(dx, -1, dz), ground, 2);
                for (int dy = 0; dy < 4; dy++) {
                    if (dx != 0 || dz != 0 || dy > 0) {
                        level.setBlock(a.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static int flowers(ServerLevel level, BlockPos a) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(a.offset(-7, -3, -7), a.offset(7, 3, 7))) {
            n += level.getBlockState(pos).is(ModBlocks.FORGET_ME_NOT.get()) ? 1 : 0;
        }
        return n;
    }

    private static ResidueEntity residue(ServerLevel level, BlockPos pos, List<Entity> extras) {
        ResidueEntity residue = Residues.spawn(level, pos, ImprintTag.FALL, 3, false);
        if (residue != null) {
            residue.setNoAi(true);
            extras.add(residue);
        }
        return residue;
    }
}
