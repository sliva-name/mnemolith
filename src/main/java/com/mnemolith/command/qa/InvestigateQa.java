package com.mnemolith.command.qa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.recall.Anchor;
import com.mnemolith.recall.AnchorKind;
import com.mnemolith.recall.FoundMemory;
import com.mnemolith.recall.Gesture;
import com.mnemolith.recall.GestureKind;
import com.mnemolith.recall.GestureLog;
import com.mnemolith.recall.Investigate;
import com.mnemolith.recall.LivingMemory;
import com.mnemolith.recall.Origin;
import com.mnemolith.recall.PlayAnchors;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith investigateqa}. Gamemaster pass over anchors, a lens reading, and the catalog bits.
 * When a real player runs it, a gesture trace is also planted a few blocks ahead of them. The suite itself
 * uses a fake player and does not chat about what the place was.
 */
public final class InvestigateQa {
    private static final String[] NAMES = {
            "deathOnce", "muteOnce", "blankOnce", "echoOnce", "fractureOnce", "loudRises",
            "tracesPlanted", "fakeSkipped", "missStaysShut", "glanceTooShort",
            "lensGesture", "lensAnchor", "lensImprint", "expiredDrops", "cappedKeepsDeath"
    };
    private static int salt = 1;

    private InvestigateQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (source.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            demonstrate(player);
        }
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        BlockPos site = QaSupport.column(level, (spawn.getX() >> 4) + 72 + salt * 3, (spawn.getZ() >> 4) + 48);
        QaSupport.tickColumn(level, site);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "InvestigateQa"));
        FakePlayer other = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("55555555-5555-5555-5555-555555555556"),
                "InvestigateFake"));
        Investigate.forget(player);
        Investigate.forget(other);
        reset(player);
        reset(other);
        EchoEntity echo = null;
        BlockPos imprintPos = site.offset(8, 0, 8);
        boolean[] checks = new boolean[NAMES.length];
        java.util.List<String> notes = new java.util.ArrayList<>();
        try {
            String dimension = level.dimension().identifier().toString();
            long now = level.getGameTime();
            int readTicks = CommonConfig.INVESTIGATE_READ_TICKS.get();

            BlockPos deathPos = site;
            checks[0] = Investigate.keep(player, AnchorKind.DEATH, deathPos, -1, false, 0, 0.0F)
                    && !Investigate.keep(player, AnchorKind.DEATH, deathPos.offset(4, 0, 0), -1, false, 0, 0.0F)
                    && Investigate.count(player, AnchorKind.DEATH) == 1;

            BlockPos mutePos = site.offset(6, 0, 0);
            checks[1] = Investigate.keep(player, AnchorKind.MUTE, mutePos, -1, false, 0, 0.0F)
                    && !Investigate.keep(player, AnchorKind.MUTE, mutePos.offset(1, 0, 0), -1, false, 0, 0.0F)
                    && Investigate.count(player, AnchorKind.MUTE) == 1;

            checks[2] = Investigate.keep(player, AnchorKind.BLANK, site.offset(-6, 0, 0), -1, false, 0, 0.0F)
                    && !Investigate.keep(player, AnchorKind.BLANK, site.offset(-6, 0, 3), -1, false, 0, 0.0F)
                    && Investigate.count(player, AnchorKind.BLANK) == 1;

            player.setPos(site.getX() + 0.5D, site.getY(), site.getZ() + 6.5D);
            boolean quiet = !Investigate.noticeEcho(player);
            echo = ModEntities.ECHO.get().spawn(level, site.offset(0, 0, 6), EntitySpawnReason.COMMAND);
            boolean saw = echo != null && Investigate.noticeEcho(player);
            boolean again = echo != null && !Investigate.noticeEcho(player);
            checks[3] = quiet && saw && again && Investigate.count(player, AnchorKind.ECHO) == 1;
            notes.add("echo=" + (echo != null));

            BlockPos fracture = site.offset(0, 0, 16);
            checks[4] = Investigate.keep(player, AnchorKind.FRACTURE, fracture, -1, false, 80, 0.0F)
                    && !Investigate.keep(player, AnchorKind.FRACTURE, fracture.offset(2, 0, 1), -1, false, 90, 0.0F)
                    && Investigate.keep(player, AnchorKind.FRACTURE, fracture.offset(0, 0, 20), -1, false, 90, 0.0F)
                    && Investigate.count(player, AnchorKind.FRACTURE) == 2;

            BlockPos loudPos = site.offset(-8, 0, 4);
            checks[5] = Investigate.keep(player, AnchorKind.LOUD, site.offset(-8, 0, 0), -1, false, 30, 0.0F)
                    && !Investigate.keep(player, AnchorKind.LOUD, site.offset(-4, 0, 0), -1, false, 20, 0.0F)
                    && Investigate.keep(player, AnchorKind.LOUD, loudPos, -1, false, 50, 0.0F)
                    && Investigate.count(player, AnchorKind.LOUD) == 1
                    && Investigate.loudPressure(player) == 50
                    && Investigate.nearest(player, loudPos, 2.0D).isPresent();

            BlockPos flashPos = site.offset(4, 0, -6);
            BlockPos recallPos = site.offset(-4, 0, -6);
            Gesture flash = gesture(GestureKind.USE, dimension, flashPos, now - 40L, false);
            Gesture recall = gesture(GestureKind.FALL, dimension, recallPos, now - 40L, true);
            checks[6] = Investigate.onFlash(player, flash)
                    && Investigate.onRecall(player, recall)
                    && !Investigate.onFlash(player, flash)
                    && Investigate.count(player, AnchorKind.FLASH) == 1
                    && Investigate.count(player, AnchorKind.RECALL) == 1;

            int deaths = Investigate.count(player, AnchorKind.DEATH);
            checks[7] = !Investigate.onDeath(other)
                    && !Investigate.onMute(other, site)
                    && !Investigate.onBlank(other)
                    && !Investigate.onEcho(other)
                    && Investigate.size(other) == 0
                    && Investigate.count(player, AnchorKind.DEATH) == deaths;

            FoundMemory found = player.getData(ModAttachments.FOUND_MEMORY.get());
            Optional<Origin> miss = Investigate.look(player, site.above(6), readTicks);
            checks[8] = miss.isEmpty() && found.isEmpty();

            BlockPos gesturePos = site.offset(0, 0, -10);
            LivingMemory.plant(player, gesture(GestureKind.ATTACK, dimension, gesturePos, now - 80L, true));
            checks[9] = Investigate.look(player, gesturePos, 1).isEmpty() && !found.hasGesture(GestureKind.ATTACK.ordinal());

            Optional<Origin> named = Investigate.look(player, gesturePos, readTicks);
            checks[10] = named.isPresent()
                    && named.get().source() == Origin.GESTURE
                    && named.get().kind() == GestureKind.ATTACK.ordinal()
                    && named.get().personal()
                    && named.get().distorted()
                    && named.get().age() == 0
                    && found.hasGesture(GestureKind.ATTACK.ordinal())
                    && found.distorted()
                    && Investigate.count(player, AnchorKind.FLASH) == 2;

            Optional<Origin> death = Investigate.look(player, deathPos, readTicks);
            checks[11] = death.isPresent()
                    && death.get().source() == Origin.ANCHOR
                    && death.get().kind() == AnchorKind.DEATH.ordinal()
                    && death.get().personal()
                    && !death.get().distorted()
                    && found.hasAnchor(AnchorKind.DEATH.ordinal());

            boolean wrote = ImprintWriter.tryWrite(level, imprintPos, ImprintTag.FIRE, player.getUUID(), false);
            Optional<Origin> unread = Investigate.look(player, imprintPos, readTicks);
            player.getData(ModAttachments.DISCOVERY.get()).noteTag(ImprintTag.FIRE);
            Optional<Origin> read = Investigate.look(player, imprintPos, readTicks);
            checks[12] = wrote && unread.isEmpty() && read.isPresent()
                    && read.get().source() == Origin.IMPRINT
                    && read.get().kind() == ImprintTag.FIRE.ordinal()
                    && read.get().personal()
                    && found.imprint()
                    && !found.hasAnchor(AnchorKind.FRACTURE.ordinal());
            notes.add("imprint=" + wrote);

            int maxAge = CommonConfig.INVESTIGATE_MAX_AGE.get();
            int cap = CommonConfig.INVESTIGATE_MAX_ANCHORS.get();
            BlockPos oldPos = site.offset(10, 0, -4);
            Anchor stale = new Anchor(AnchorKind.FLASH, player.getUUID(), dimension, oldPos, now - maxAge - 50L, -1, false, 0, 0.0F);
            boolean rejected = !PlayAnchors.get(level.getServer()).add(stale, now, cap, maxAge);
            BlockPos freshPos = oldPos.offset(4, 0, 0);
            boolean kept = Investigate.onFlash(player, gesture(GestureKind.PLACE, dimension, freshPos, now, false));
            checks[13] = rejected && kept
                    && Investigate.nearest(player, oldPos, 2.0D).isEmpty()
                    && Investigate.nearest(player, freshPos, 2.0D).isPresent();

            for (int i = 0; i < cap + 8; i++) {
                Investigate.keep(player, AnchorKind.FLASH, site.offset(120 + i * 3, 0, 0), GestureKind.ATTACK.ordinal(), false, 0, 0.0F);
            }
            checks[14] = Investigate.count(player, AnchorKind.DEATH) == 1
                    && Investigate.count(player, AnchorKind.MUTE) == 1
                    && Investigate.count(player, AnchorKind.ECHO) == 1
                    && Investigate.size(player) == cap;
        } catch (RuntimeException ex) {
            Mnemolith.LOGGER.error("Mnemolith investigateqa failed", ex);
            notes.add("exception " + ex);
        } finally {
            if (echo != null && !echo.isRemoved()) {
                echo.discard();
            }
            QaSupport.clear(level, imprintPos);
            reset(player);
            reset(other);
            Investigate.forget(player);
            Investigate.forget(other);
            QaSupport.releaseColumn(level, net.minecraft.world.level.ChunkPos.containing(site));
        }
        return new QaReport("investigateqa", NAMES, checks, notes).log();
    }

    /** Plants one gesture and its trace a few blocks ahead of whoever ran the command. */
    private static void demonstrate(ServerPlayer player) {
        if (!Investigate.enabled() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos mark = player.blockPosition().relative(player.getDirection(), 3);
        Gesture gesture = new Gesture(
                GestureKind.ATTACK,
                level.dimension().identifier().toString(),
                mark,
                player.getYRot(),
                player.getXRot(),
                "",
                "",
                -1,
                "",
                level.getGameTime() - 80L,
                false,
                List.of());
        LivingMemory.plant(player, gesture);
        Investigate.onFlash(player, gesture);
    }

    private static void reset(ServerPlayer player) {
        player.setData(ModAttachments.GESTURE_LOG.get(), new GestureLog());
        player.setData(ModAttachments.FOUND_MEMORY.get(), new FoundMemory());
        player.setData(ModAttachments.DISCOVERY.get(), new Discovery());
        Investigate.forgetSession(player.getUUID());
    }

    private static Gesture gesture(GestureKind kind, String dimension, BlockPos pos, long time, boolean distorted) {
        return new Gesture(kind, dimension, pos, 10.0F, 0.0F, "", "", -1, "", time, distorted, List.of());
    }
}
