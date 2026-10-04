package com.mnemolith.command.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.echo.graft.EchoGraft;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.ModEffects;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.recall.AnchorKind;
import com.mnemolith.recall.Intervene;
import com.mnemolith.recall.Investigate;
import com.mnemolith.recall.Origin;
import com.mnemolith.recall.UseMemory;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith interveneqa}. A scar fragment's rewrite: silence when nothing is tempered, the echo's temper
 * when one is, a second pass that does not take, and a pale residue at a lie that cannot be kept.
 * Gamemaster text only. A real player with no open work gets one death memory rewritten in front of them.
 */
public final class InterveneQa {
    private static final String[] NAMES = {
            "deathBecomesSilence", "residueFollows", "onceHolds",
            "temperOfScar", "sameTemperCycles", "muteDoesNotStop",
            "fakeSkipped", "lieStaysPale", "lensReadsRewrite"
    };
    private static int salt = 1;

    private InterveneQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (source.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            demonstrate(player);
        }
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        BlockPos site = QaSupport.column(level, (spawn.getX() >> 4) + 120 + salt * 3, (spawn.getZ() >> 4) + 80);
        QaSupport.tickColumn(level, site);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("66666666-6666-6666-6666-666666666668"),
                "InterveneQa"));
        EchoEntity temperEcho = null;
        EchoEntity cycleEcho = null;
        BlockPos temperAt = site.offset(16, 0, 0);
        BlockPos cycleAt = site.offset(0, 0, 16);
        BlockPos muteAt = site.offset(-16, 0, 0);
        BlockPos gateAt = site.offset(0, 0, -16);
        BlockPos lieAt = site.offset(32, 0, 0);
        boolean[] checks = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        try {
            player.removeAllEffects();
            Investigate.forget(player);
            UseMemory.forget(player);
            player.setPos(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D);

            ImprintWriter.write(level, site, List.of(ImprintTag.DEATH), player.getUUID(), false);
            int before = pressure(level, site);
            ResidueEntity residue = Residues.spawn(level, Residues.airAbove(level, site), ImprintTag.DEATH, 3, false);
            boolean rewrote = Intervene.rewrite(player, level, site);
            Imprint after = highest(level, site);
            checks[0] = rewrote
                    && before > 0
                    && after != null
                    && after.tag() == ImprintTag.SILENCE
                    && after.rewritten()
                    && after.intensity() == ImprintWriter.intensityFor(ImprintTag.DEATH)
                    && pressure(level, site) < before;
            notes.add("pressure " + before + " -> " + pressure(level, site));
            checks[1] = residue != null && residue.isAlive() && residue.tag() == ImprintTag.SILENCE && residue.temper() == Temper.HUSHED;
            notes.add("residue=" + (residue == null ? "-" : residue.tag().getSerializedName()));

            boolean again = Intervene.rewrite(player, level, site);
            Imprint held = highest(level, site);
            checks[2] = !again && held != null && held.tag() == ImprintTag.SILENCE && held.rewritten();

            QaSupport.tickColumn(level, temperAt);
            ImprintWriter.write(level, temperAt, List.of(ImprintTag.DEATH), player.getUUID(), false);
            temperEcho = ModEntities.ECHO.get().spawn(level, temperAt.offset(1, 0, 0), EntitySpawnReason.COMMAND);
            if (temperEcho != null) {
                temperEcho.setOwner(player);
                temperEcho.setGraft(new EchoGraft(new ImprintCast(ImprintTag.FIRE, 4, temperAt, Optional.empty(), 0, 0L), 8));
                temperEcho.setScarred(true);
            }
            boolean tempered = temperEcho != null
                    && temperEcho.graftTemper() == Temper.KINDLED
                    && Intervene.rewrite(player, level, temperAt);
            Imprint temperedImprint = highest(level, temperAt);
            checks[3] = tempered && temperedImprint != null && temperedImprint.tag() == ImprintTag.FIRE && temperedImprint.rewritten();
            notes.add("temper=" + (temperedImprint == null ? "-" : temperedImprint.tag().getSerializedName()));

            QaSupport.tickColumn(level, cycleAt);
            ImprintWriter.write(level, cycleAt, List.of(ImprintTag.FIRE), player.getUUID(), false);
            cycleEcho = ModEntities.ECHO.get().spawn(level, cycleAt.offset(1, 0, 0), EntitySpawnReason.COMMAND);
            if (cycleEcho != null) {
                cycleEcho.setOwner(player);
                cycleEcho.setGraft(new EchoGraft(new ImprintCast(ImprintTag.FIRE, 4, cycleAt, Optional.empty(), 0, 0L), 8));
                cycleEcho.setScarred(true);
            }
            boolean cycled = cycleEcho != null && Intervene.rewrite(player, level, cycleAt);
            Imprint cycledImprint = highest(level, cycleAt);
            checks[4] = cycled && cycledImprint != null && cycledImprint.tag() == ImprintTag.FALL && cycledImprint.rewritten();
            notes.add("cycle=" + (cycledImprint == null ? "-" : cycledImprint.tag().getSerializedName()));

            QaSupport.tickColumn(level, muteAt);
            ImprintWriter.write(level, muteAt, List.of(ImprintTag.DEATH), player.getUUID(), false);
            QaSupport.muteStone(level, muteAt);
            boolean throughSilence = LoadedChunkMemory.isMuted(level, muteAt) && Intervene.rewrite(player, level, muteAt);
            Imprint muted = highest(level, muteAt);
            checks[5] = throughSilence && muted != null && muted.tag() == ImprintTag.SILENCE && muted.rewritten();

            QaSupport.tickColumn(level, gateAt);
            ImprintWriter.write(level, gateAt, List.of(ImprintTag.DEATH), player.getUUID(), false);
            player.addEffect(new MobEffectInstance(ModEffects.UNRECORDED, 200, 0, false, false));
            boolean silenced = !Intervene.tryUse(player, level, gateAt) && tagAt(level, gateAt) == ImprintTag.DEATH;
            player.removeAllEffects();
            boolean fake = !Intervene.tryUse(player, level, gateAt) && tagAt(level, gateAt) == ImprintTag.DEATH;
            boolean direct = Intervene.rewrite(player, level, gateAt) && tagAt(level, gateAt) == ImprintTag.SILENCE;
            checks[6] = silenced && fake && direct;
            notes.add("silenced=" + silenced + " fake=" + fake + " direct=" + direct);

            QaSupport.tickColumn(level, lieAt);
            stand(player, lieAt);
            var lie = UseMemory.offer(player, AnchorKind.DEATH, lieAt, 0.0F, true);
            boolean condensed = lie != null && UseMemory.condense(player);
            ResidueEntity truth = lie == null ? null : residueAt(level, player.getUUID(), lie.pos(), false);
            ResidueEntity pale = lie == null ? null : residueAt(level, player.getUUID(), lie.guide(), true);
            boolean paleHeld = false;
            if (pale != null) {
                player.setHealth(player.getMaxHealth());
                pale.pin(Residues.PIN_TICKS);
                boolean slipped = !Residues.capture(player, pale, new ItemStack(com.mnemolith.content.ModItems.EXTRACTION_NEEDLE.get()));
                boolean unstored = !UseMemory.store(player, pale);
                paleHeld = slipped && unstored && pale.isAlive() && pale.washed() && UseMemory.open(player) != null;
            }
            checks[7] = condensed
                    && truth != null
                    && !truth.washed()
                    && truth.tag() == ImprintTag.DEATH
                    && pale != null
                    && pale.washed()
                    && !pale.blockPosition().equals(truth.blockPosition())
                    && paleHeld;
            notes.add("pale=" + (pale != null && pale.washed()) + " true=" + (truth != null && !truth.washed()));
            player.removeAllEffects();

            player.getData(ModAttachments.DISCOVERY.get()).noteTag(ImprintTag.SILENCE);
            Investigate.forgetSession(player.getUUID());
            Optional<Origin> named = Investigate.look(player, site, CommonConfig.INVESTIGATE_READ_TICKS.get());
            checks[8] = named.isPresent()
                    && named.get().source() == Origin.IMPRINT
                    && named.get().kind() == ImprintTag.SILENCE.ordinal()
                    && named.get().distorted();
            notes.add("lens=" + (named.isEmpty() ? "-" : named.get().kind() + " warped=" + named.get().distorted()));
        } catch (RuntimeException ex) {
            Mnemolith.LOGGER.error("Mnemolith interveneqa failed", ex);
            notes.add("exception " + ex);
        } finally {
            if (temperEcho != null && !temperEcho.isRemoved()) {
                temperEcho.discard();
            }
            if (cycleEcho != null && !cycleEcho.isRemoved()) {
                cycleEcho.discard();
            }
            player.removeAllEffects();
            Investigate.forget(player);
            UseMemory.forget(player);
            QaSupport.discardResidues(level, site);
            QaSupport.discardResidues(level, lieAt);
            for (BlockPos pos : List.of(site, temperAt, cycleAt, muteAt, gateAt, lieAt)) {
                QaSupport.clear(level, pos);
            }
            QaSupport.releaseColumn(level, net.minecraft.world.level.ChunkPos.containing(site));
        }
        return new QaReport("interveneqa", NAMES, checks, notes).log();
    }

    /** Rewrites one death memory a few blocks ahead, so a gamemaster can see the residue change color. */
    private static void demonstrate(ServerPlayer player) {
        if (!Intervene.enabled() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos mark = player.blockPosition().relative(player.getDirection(), 3);
        if (LoadedChunkMemory.isMuted(level, mark)) {
            return;
        }
        if (!ImprintWriter.write(level, mark, List.of(ImprintTag.DEATH), player.getUUID(), false)) {
            return;
        }
        Residues.spawn(level, Residues.airAbove(level, mark), ImprintTag.DEATH, 3, false);
        Intervene.rewrite(player, level, mark);
    }

    private static void stand(ServerPlayer player, BlockPos pos) {
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private static int pressure(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? -1 : memory.cachedPressure();
    }

    private static Imprint highest(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? null : memory.highest().orElse(null);
    }

    private static @org.jspecify.annotations.Nullable ImprintTag tagAt(ServerLevel level, BlockPos pos) {
        Imprint imprint = highest(level, pos);
        return imprint == null ? null : imprint.tag();
    }

    private static ResidueEntity residueAt(ServerLevel level, UUID owner, BlockPos pos, boolean washed) {
        AABB box = new AABB(pos).inflate(4.0D);
        for (ResidueEntity residue : level.getEntitiesOfClass(ResidueEntity.class, box, entity -> owner.equals(entity.legendOwner()) && entity.washed() == washed)) {
            return residue;
        }
        return null;
    }
}
