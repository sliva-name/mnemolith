package com.mnemolith.command.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.ArchiveVaultBlockEntity;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.recall.AnchorKind;
import com.mnemolith.recall.Choice;
import com.mnemolith.recall.FoundMemory;
import com.mnemolith.recall.Gesture;
import com.mnemolith.recall.GestureKind;
import com.mnemolith.recall.GestureLog;
import com.mnemolith.recall.Investigate;
import com.mnemolith.recall.Legend;
import com.mnemolith.recall.LivingMemory;
import com.mnemolith.recall.UseMemory;
import com.mnemolith.vault.ArchiveVaults;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith useqa}. Gamemaster pass over a legendary offer: help and a lie, and the cost of
 * leaving, muting, taking or storing it. When a real player runs it and has no open offer, one is
 * planted a few blocks ahead. The suite itself uses a fake player.
 */
public final class UseQa {
    private static final String[] NAMES = {
            "picksDeath", "helpPointsTrue", "liePointsAside", "residueCondenses", "helpNoThreat",
            "lieStrikesOnce", "leaveSours", "farMuteIgnores", "muteSpendsKind",
            "harvestCosts", "storeBanks", "echoGoesQuiet", "fakeSkipped"
    };
    private static int salt = 1;

    private UseQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (source.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            demonstrate(player);
        }
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        BlockPos site = QaSupport.column(level, (spawn.getX() >> 4) + 96 + salt * 3, (spawn.getZ() >> 4) + 64);
        QaSupport.tickColumn(level, site);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                "UseQa"));
        FakePlayer other = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("66666666-6666-6666-6666-666666666667"),
                "UseFake"));
        reset(player);
        reset(other);
        Investigate.forget(player);
        Investigate.forget(other);
        UseMemory.forget(player);
        UseMemory.forget(other);
        EchoEntity echo = null;
        BlockPos vaultPos = site.offset(3, 0, 16);
        boolean[] checks = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        try {
            String dimension = level.dimension().identifier().toString();
            long now = level.getGameTime();
            int offset = CommonConfig.USE_LIE_OFFSET.get();
            player.setPos(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D);

            Investigate.keep(player, AnchorKind.DEATH, site, -1, false, 0, 0.0F);
            Investigate.keep(player, AnchorKind.MUTE, site.offset(0, 0, 24), -1, false, 0, 0.0F);
            Legend formed = UseMemory.form(player);
            checks[0] = formed != null
                    && formed.source() == AnchorKind.DEATH
                    && formed.choice().open()
                    && (formed.lie()
                            ? formed.guide().distSqr(formed.pos()) == (double) offset * offset
                            : formed.guide().equals(formed.pos()));
            notes.add("formed=" + (formed == null ? "-" : formed.source().getSerializedName() + " lie=" + formed.lie()));
            UseMemory.forget(player);

            Legend help = UseMemory.offer(player, AnchorKind.DEATH, site, 0.0F, false);
            checks[1] = help != null && !help.lie() && help.guide().equals(help.pos()) && help.tag() == ImprintTag.DEATH.ordinal();
            UseMemory.forget(player);

            Legend lie = UseMemory.offer(player, AnchorKind.DEATH, site.offset(0, 0, 48), 0.0F, true);
            checks[2] = lie != null
                    && lie.lie()
                    && !lie.guide().equals(lie.pos())
                    && lie.guide().distSqr(lie.pos()) == (double) offset * offset
                    && lie.guide().getY() == lie.pos().getY();
            UseMemory.forget(player);

            BlockPos truePos = site.offset(0, 0, -16);
            ready(level, truePos);
            help = UseMemory.offer(player, AnchorKind.DEATH, truePos, 0.0F, false);
            stand(player, truePos);
            ResidueEntity residue = help == null ? null : (UseMemory.condense(player) ? residueAt(level, player.getUUID(), truePos) : null);
            checks[3] = residue != null
                    && residue.legendary()
                    && player.getUUID().equals(residue.legendOwner())
                    && residue.tag() == ImprintTag.DEATH
                    && residue.strength() == CommonConfig.USE_RESIDUE_STRENGTH.get();
            int replicants = QaSupport.replicantCount(level, truePos);
            boolean struck = UseMemory.approach(player);
            checks[4] = checks[3] && !struck && QaSupport.replicantCount(level, truePos) == replicants;
            UseMemory.forget(player);
            QaSupport.discardResidues(level, truePos);

            BlockPos liePos = site.offset(6, 0, 6);
            lie = UseMemory.offer(player, AnchorKind.FRACTURE, liePos, 90.0F, true);
            BlockPos guide = lie == null ? liePos : lie.guide();
            stand(player, guide);
            int before = QaSupport.replicantCount(level, guide);
            boolean first = UseMemory.approach(player);
            int mid = QaSupport.replicantCount(level, guide);
            boolean second = UseMemory.approach(player);
            checks[5] = lie != null && first && !second && mid == before + 1 && QaSupport.replicantCount(level, guide) == mid;
            notes.add("strike before=" + before + " mid=" + mid);
            QaSupport.discardReplicants(level, guide);
            UseMemory.forget(player);

            BlockPos leftPos = site.offset(-16, 0, 0);
            ready(level, leftPos);
            UseMemory.offer(player, AnchorKind.DEATH, leftPos, 0.0F, false);
            stand(player, leftPos.offset(0, 0, 80));
            boolean notYet = !UseMemory.depart(player);
            stand(player, leftPos);
            boolean saw = UseMemory.witness(player);
            boolean stays = !UseMemory.depart(player);
            boolean condensed = UseMemory.condense(player);
            stand(player, leftPos.offset(0, 0, 80));
            boolean left = UseMemory.depart(player);
            checks[6] = notYet && saw && stays && condensed && left
                    && UseMemory.soured(player)
                    && UseMemory.open(player) == null
                    && residueAt(level, player.getUUID(), leftPos) == null
                    && !UseMemory.spent(player, AnchorKind.DEATH);
            UseMemory.forget(player);

            BlockPos mutePos = site.offset(16, 0, 0);
            ready(level, mutePos);
            Investigate.forget(player);
            Investigate.keep(player, AnchorKind.DEATH, mutePos, -1, false, 0, 0.0F);
            Investigate.keep(player, AnchorKind.MUTE, mutePos.offset(0, 0, 24), -1, false, 0, 0.0F);
            UseMemory.offer(player, AnchorKind.DEATH, mutePos, 0.0F, false);
            stand(player, mutePos);
            UseMemory.condense(player);
            boolean far = !UseMemory.mute(player, mutePos.offset(200, 0, 0)) && UseMemory.open(player) != null;
            checks[7] = far;
            boolean muted = UseMemory.mute(player, mutePos);
            Legend again = UseMemory.form(player);
            checks[8] = muted
                    && UseMemory.spent(player, AnchorKind.DEATH)
                    && !UseMemory.spent(player, AnchorKind.MUTE)
                    && residueAt(level, player.getUUID(), mutePos) == null
                    && again != null
                    && again.source() == AnchorKind.MUTE
                    && again.choice().open();
            notes.add("afterMute=" + (again == null ? "-" : again.source().getSerializedName()));
            UseMemory.forget(player);
            Investigate.forget(player);

            BlockPos takePos = site.offset(0, 0, 32);
            ready(level, takePos);
            UseMemory.offer(player, AnchorKind.DEATH, takePos, 0.0F, false);
            stand(player, takePos);
            boolean rose = UseMemory.condense(player);
            ResidueEntity unread = residueAt(level, player.getUUID(), takePos);
            player.setHealth(player.getMaxHealth());
            boolean slipped = unread != null && !Residues.capture(player, unread, new ItemStack(ModItems.EXTRACTION_NEEDLE.get()));
            boolean still = slipped && unread.isAlive() && UseMemory.open(player) != null && UseMemory.open(player).choice() == Choice.OPEN;
            boolean taken = false;
            if (still) {
                unread.pin(Residues.PIN_TICKS);
                taken = Residues.capture(player, unread, new ItemStack(ModItems.EXTRACTION_NEEDLE.get()));
            }
            checks[9] = rose && still && taken
                    && !unread.isAlive()
                    && UseMemory.open(player) == null
                    && UseMemory.spent(player, AnchorKind.DEATH);
            notes.add("slipped=" + slipped + " taken=" + taken);
            player.removeAllEffects();
            UseMemory.forget(player);

            BlockPos bankPos = site.offset(0, 0, 16);
            ready(level, bankPos);
            UseMemory.offer(player, AnchorKind.ECHO, bankPos, 0.0F, false);
            stand(player, bankPos);
            boolean bankedResidue = UseMemory.condense(player);
            ResidueEntity bank = residueAt(level, player.getUUID(), bankPos);
            level.setBlock(vaultPos, ModBlocks.ARCHIVE_VAULT.get().defaultBlockState(), 3);
            ArchiveVaultBlockEntity vault = level.getBlockEntity(vaultPos) instanceof ArchiveVaultBlockEntity entity ? entity : null;
            if (vault != null) {
                ArchiveVaults.track(level, vaultPos);
                ArchiveVaults.tick(level, vaultPos, vault);
            }
            boolean stored = bank != null && UseMemory.store(player, bank);
            checks[10] = bankedResidue && vault != null && stored
                    && !bank.isAlive()
                    && vault.count() == 1
                    && vault.stored().get(0).tag() == ImprintTag.FIRE
                    && UseMemory.open(player) == null
                    && UseMemory.spent(player, AnchorKind.ECHO);
            notes.add("vault=" + (vault == null ? -1 : vault.count()) + " stored=" + stored);
            UseMemory.forget(player);

            for (int i = 0; i < 3; i++) {
                LivingMemory.plant(player, gesture(GestureKind.ATTACK, dimension, site, now, false));
            }
            echo = ModEntities.ECHO.get().spawn(level, site.offset(1, 0, 1), EntitySpawnReason.COMMAND);
            boolean voiced = false;
            boolean quietResidue = false;
            boolean harvested = false;
            boolean blank = false;
            boolean staysBlank = false;
            boolean restored = false;
            if (echo != null) {
                echo.setOwner(player);
                UseMemory.stamp(echo, player);
                voiced = UseMemory.voiceOf(player) == GestureKind.ATTACK.ordinal() && echo.bearing() == GestureKind.ATTACK.ordinal();
                BlockPos quietPos = site.offset(-32, 0, 0);
                ready(level, quietPos);
                UseMemory.offer(player, AnchorKind.DEATH, quietPos, 0.0F, false);
                stand(player, quietPos);
                echo.snapTo(quietPos.getX() + 0.5D, quietPos.getY(), quietPos.getZ() + 0.5D, 0.0F, 0.0F);
                quietResidue = UseMemory.condense(player);
                ResidueEntity quiet = residueAt(level, player.getUUID(), quietPos);
                harvested = quiet != null && UseMemory.onHarvest(player, quiet);
                blank = echo.bearing() < 0 && UseMemory.sealed(player) == 3;
                UseMemory.stamp(echo, player);
                staysBlank = echo.bearing() < 0;
                LivingMemory.plant(player, gesture(GestureKind.ATTACK, dimension, site, now, false));
                UseMemory.stamp(echo, player);
                restored = echo.bearing() == GestureKind.ATTACK.ordinal();
            }
            checks[11] = voiced && quietResidue && harvested && blank && staysBlank && restored;
            notes.add("bearing=" + (echo == null ? "-" : Integer.toString(echo.bearing())) + " sealed=" + UseMemory.sealed(player));

            stand(other, site);
            Investigate.keep(other, AnchorKind.DEATH, site.offset(8, 0, 8), -1, false, 0, 0.0F);
            Investigate.keep(other, AnchorKind.MUTE, site.offset(8, 0, 12), -1, false, 0, 0.0F);
            UseMemory.tick(other);
            boolean ignored = UseMemory.open(other) == null && !UseMemory.onMute(other, site.offset(8, 0, 8));
            checks[12] = ignored && UseMemory.offer(other, AnchorKind.DEATH, site.offset(8, 0, 8), 0.0F, false) != null;
        } catch (RuntimeException ex) {
            Mnemolith.LOGGER.error("Mnemolith useqa failed", ex);
            notes.add("exception " + ex);
        } finally {
            if (echo != null && !echo.isRemoved()) {
                echo.discard();
            }
            QaSupport.discardResidues(level, site);
            QaSupport.discardResidues(level, site.offset(0, 0, 48));
            QaSupport.discardResidues(level, site.offset(0, 0, -16));
            QaSupport.discardResidues(level, site.offset(-16, 0, 0));
            QaSupport.discardResidues(level, site.offset(16, 0, 0));
            QaSupport.discardResidues(level, site.offset(0, 0, 32));
            QaSupport.discardResidues(level, site.offset(-32, 0, 0));
            QaSupport.discardReplicants(level, site);
            QaSupport.discardReplicants(level, site.offset(0, 0, 48));
            if (level.getBlockState(vaultPos).is(ModBlocks.ARCHIVE_VAULT.get())) {
                level.setBlock(vaultPos, Blocks.AIR.defaultBlockState(), 3);
            }
            for (net.minecraft.world.entity.item.ItemEntity dropped : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(site).inflate(64.0D))) {
                dropped.discard();
            }
            reset(player);
            reset(other);
            Investigate.forget(player);
            Investigate.forget(other);
            UseMemory.forget(player);
            UseMemory.forget(other);
            for (BlockPos extra : List.of(
                    site,
                    site.offset(0, 0, -16),
                    site.offset(-16, 0, 0),
                    site.offset(16, 0, 0),
                    site.offset(0, 0, 16),
                    site.offset(0, 0, 32),
                    site.offset(-32, 0, 0))) {
                QaSupport.releaseColumn(level, net.minecraft.world.level.ChunkPos.containing(extra));
            }
        }
        return new QaReport("useqa", NAMES, checks, notes).log();
    }

    /** Plants a true offer a few blocks ahead, when the player does not already have one. */
    private static void demonstrate(ServerPlayer player) {
        if (!UseMemory.enabled() || UseMemory.open(player) != null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos mark = player.blockPosition().relative(player.getDirection(), 4);
        if (UseMemory.offer(player, AnchorKind.DEATH, mark, player.getYRot(), false) != null) {
            player.setPos(mark.getX() + 0.5D, mark.getY(), mark.getZ() + 0.5D);
            UseMemory.condense(player);
            player.setPos(mark.getX() - player.getDirection().getStepX() * 4 + 0.5D, mark.getY(), mark.getZ() - player.getDirection().getStepZ() * 4 + 0.5D);
        }
    }

    /** Neighbor columns are inside the site ticket, but they are not entity-ticking until this wait finishes. */
    private static void ready(ServerLevel level, BlockPos pos) {
        QaSupport.tickColumn(level, pos);
    }

    private static void stand(ServerPlayer player, BlockPos pos) {
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private static ResidueEntity residueAt(ServerLevel level, UUID owner, BlockPos pos) {
        for (ResidueEntity residue : level.getEntitiesOfClass(ResidueEntity.class, new AABB(pos).inflate(8.0D), entity -> owner.equals(entity.legendOwner()))) {
            return residue;
        }
        return null;
    }

    private static void reset(ServerPlayer player) {
        player.setData(ModAttachments.GESTURE_LOG.get(), new GestureLog());
        player.setData(ModAttachments.FOUND_MEMORY.get(), new FoundMemory());
        player.removeAllEffects();
    }

    private static Gesture gesture(GestureKind kind, String dimension, BlockPos pos, long time, boolean distorted) {
        return new Gesture(kind, dimension, pos, 0.0F, 0.0F, "", "", -1, "", time, distorted, List.of());
    }
}
