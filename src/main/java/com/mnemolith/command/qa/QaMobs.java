package com.mnemolith.command.qa;

import com.mnemolith.data.ImprintSlips;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.ai.CopiedActionKind;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ImprintTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;

/** Echo strider, archivist, and moment replicant checks. */
public final class QaMobs {
    private QaMobs() {}

    static boolean strider(ServerLevel level, BlockPos pos) {
        EchoStrider strider = MobSpawns.summonStrider(level, pos);
        if (strider == null) {
            return false;
        }
        strider.beginCharge();
        boolean charged = strider.action() == MobActions.TELEGRAPH;
        strider.discard();
        return charged;
    }

    static boolean archivist(ServerLevel level, BlockPos pos) {
        Archivist archivist = MobSpawns.summonArchivist(level, pos);
        if (archivist == null) {
            return false;
        }
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, ImprintSlips.of(ImprintTag.DEATH, pos));
        boolean stole = archivist.snatch(level, container, null, true);
        boolean fleeing = archivist.action() == MobActions.FLEE;
        archivist.discard();
        return stole && fleeing && container.getItem(0).isEmpty();
    }

    static boolean replicant(ServerLevel level, BlockPos pos) {
        MomentReplicant replicant = MobSpawns.summonReplicant(level, pos);
        if (replicant == null) {
            return false;
        }
        replicant.beginTelegraph(CopiedActionKind.MELEE, null);
        boolean telegraph = replicant.action() == MobActions.TELEGRAPH;
        replicant.discard();
        return telegraph;
    }
}
