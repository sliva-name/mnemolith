package com.mnemolith.command;

import com.mnemolith.Mnemolith;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.ai.CopiedActionKind;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ImprintTag;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import com.mojang.brigadier.context.CommandContext;

/** {@code /mnemolith spawn} and {@code /mnemolith mobs}. */
public final class MobCommands {
    private MobCommands() {}

    public static int spawn(CommandContext<CommandSourceStack> context, String name) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Entity entity = MobSpawns.summonNamed(level, pos, name);
        if (entity == null) {
            source.sendSuccess(() -> Component.translatable("mnemolith.command.spawn_fail", name), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("mnemolith.command.spawn", name), true);
        return 1;
    }

    public static int mobs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        EchoStrider strider = MobSpawns.summonStrider(level, pos);
        Archivist archivist = MobSpawns.summonArchivist(level, pos.relative(Direction.EAST, 2));
        MomentReplicant replicant = MobSpawns.summonReplicant(level, pos.relative(Direction.WEST, 2));
        boolean striderOk = false;
        if (strider != null) {
            strider.beginCharge();
            striderOk = true;
        }
        boolean stole = false;
        if (archivist != null) {
            SimpleContainer container = new SimpleContainer(1);
            container.setItem(0, ImprintSlips.of(ImprintTag.DEATH, pos));
            stole = archivist.snatch(level, container, null, true);
        }
        boolean replicantOk = false;
        if (replicant != null) {
            replicant.beginTelegraph(CopiedActionKind.MELEE, null);
            replicantOk = true;
        }
        Mnemolith.LOGGER.info("Mnemolith mobs strider={} archivistStole={} replicant={}", striderOk, stole, replicantOk);
        boolean reportedStrider = striderOk;
        boolean reportedStole = stole;
        boolean reportedReplicant = replicantOk;
        source.sendSuccess(() -> Component.translatable("mnemolith.command.mobs", reportedStrider, reportedStole, reportedReplicant), true);
        return (striderOk ? 1 : 0) + (stole ? 1 : 0) + (replicantOk ? 1 : 0);
    }
}
