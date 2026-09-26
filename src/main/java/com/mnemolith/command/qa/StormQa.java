package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoProgress;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.graft.EchoGraft;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.echo.storm.RecollectionStorm;
import com.mnemolith.echo.storm.ScarSites;
import com.mnemolith.echo.storm.StormData;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.MemoryAvatar;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.imprint.ScarSite;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith stormqa}. Dedicated-server pass over recollection storms and the Scar: the start gate (calm,
 * fracture, muted, warded, cap/busy), a residual shard calling a storm through the real item, a mute stone containing
 * a gathering storm, a wave condensing the area's memories, a hushed echo swallowing an act-out, a muted centre
 * choking the storm and starving its residues, a storm with nothing to feed on spending itself, two survivors passing
 * and three merging into the Scar (site, heart, glass ring, boss), mobGriefing off placing no blocks, the Scar only
 * hurt once read through a real lens reading, its recall and a grave-set decoy, its drops, a scar fragment setting an
 * echo, scar glass warding, the site seeding its daily residue, breaking the heart healing it, and persistence.
 * <p>
 * Storms already running on the server are set aside for the pass and put back afterwards; everything the pass builds
 * (storms, sites, blocks, entities) is removed again.
 */
public final class StormQa {
    private static final String[] NAMES = {"gates", "shardCalls", "muteContains", "waveCondenses", "hushSwallows", "chokeStarves", "spent", "passed",
            "scarForms", "griefingOff", "scarNeedsReading", "scarRecall", "graveDecoy", "scarDrops", "fragmentSetsEcho", "wardBlocks", "siteReseeds",
            "heartHeals", "persistence"};
    private static final UUID OWNER = UUID.fromString("99999999-7777-7777-7777-777777777777");
    private static final int PAD = 9;
    private static int salt;

    private StormQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    /** Runs the suite near {@code spawn} and returns its report. Shared by the command and the game test. */
    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        // Sites five chunks apart (a storm's area plus its busy margin), well away from the other suites.
        BlockPos a = column(level, (spawn.getX() >> 4) + 44 + salt * 4, (spawn.getZ() >> 4) + 36);
        BlockPos b = column(level, (a.getX() >> 4) + 5, a.getZ() >> 4);
        BlockPos c = column(level, (a.getX() >> 4) + 10, a.getZ() >> 4);
        BlockPos d = column(level, (a.getX() >> 4) + 15, a.getZ() >> 4);
        List<BlockPos> sites = List.of(a, b, c, d);
        for (BlockPos p : sites) {
            tickColumn(level, p);
            flatten(level, p, PAD, 10);
            resetArea(level, p);
        }
        StormData data = StormData.get(level.getServer());
        List<RecollectionStorm> parked = data.storms();
        for (RecollectionStorm storm : parked) {
            data.remove(storm);
        }
        boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(OWNER, "StormQaOwner"));
        owner.setGameMode(GameType.SURVIVAL);
        MemoryAvatar.STAND_INS.put(OWNER, owner);
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.setData(ModAttachments.ECHO_PROGRESS.get(), EchoProgress.NONE);
        owner.setData(ModAttachments.DISCOVERY.get(), new Discovery());
        owner.getInventory().clearContent();
        owner.removeAllEffects();
        owner.setHealth(owner.getMaxHealth());
        park(owner, a.offset(-6, 0, -6));
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        List<Entity> extras = new ArrayList<>();
        try {
            // ---------- gates: calm, fracture, muted, warded ----------
            ChunkPos ca = ChunkPos.containing(a);
            Storms.Gate calm = Storms.gate(level, ca);
            loud(level, a, PressureBand.FRACTURE);
            Storms.Gate open = Storms.gate(level, ca);
            LoadedChunkMemory.addMuteStone(level, a.offset(-6, 0, -6));
            Storms.Gate muted = Storms.gate(level, ca);
            LoadedChunkMemory.removeMuteStone(level, a.offset(-6, 0, -6));
            BlockPos ward = a.offset(18, 0, 0);
            level.setBlock(ward, ModBlocks.SCAR_GLASS.get().defaultBlockState(), 3);
            Storms.Gate warded = Storms.gate(level, ca);
            level.setBlock(ward, Blocks.AIR.defaultBlockState(), 3);
            Storms.Gate reopened = Storms.gate(level, ca);
            Storms.Gate expectOpen = Storms.maxPerDimension() > 0 ? Storms.Gate.OK : Storms.Gate.CAP;
            ok[0] = !Storms.enabled() ? calm == Storms.Gate.DISABLED
                    : calm == Storms.Gate.CALM && open == expectOpen && muted == (expectOpen == Storms.Gate.OK ? Storms.Gate.MUTED : Storms.Gate.CAP)
                            && warded == (expectOpen == Storms.Gate.OK ? Storms.Gate.WARDED : Storms.Gate.CAP) && reopened == expectOpen;
            notes.add("gates calm=" + calm + " fracture=" + open + " muted=" + muted + " warded=" + warded + " reopened=" + reopened
                    + " enabled=" + Storms.enabled() + " cap=" + Storms.maxPerDimension());

            // ---------- a residual shard freed in the fracture calls a storm (real item use) ----------
            ItemStack shard = Residues.shard(ImprintTag.FALL, 4, a, level.getGameTime());
            owner.setItemInHand(InteractionHand.MAIN_HAND, shard);
            park(owner, a.offset(2, 0, 2));
            BlockPos ground = a.below();
            InteractionResult used = shard.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false)));
            RecollectionStorm called = Storms.at(level, ca);
            ResidueEntity freed = called == null ? null : first(Storms.living(level, called));
            Storms.Gate busy = Storms.gate(level, ChunkPos.containing(a.offset(16, 0, 0)));
            Storms.Gate far = Storms.gate(level, ChunkPos.containing(c));
            boolean capOk = Storms.maxPerDimension() <= 1 ? busy == Storms.Gate.CAP : busy == Storms.Gate.BUSY || busy == Storms.Gate.CALM;
            ok[1] = expectOpen != Storms.Gate.OK && called == null
                    || used == InteractionResult.SUCCESS_SERVER && called != null && called.phase() == RecollectionStorm.Phase.GATHERING
                            && "shard".equals(called.cause()) && freed != null && freed.tag() == ImprintTag.FALL && freed.storm() == called.id()
                            && shard.isEmpty() && capOk;
            notes.add("shardCalls result=" + used + " storm=" + (called == null ? "-" : called.id() + "/" + called.phase()) + " residue="
                    + (freed == null ? "-" : freed.tag().getSerializedName() + "/" + freed.storm()) + " neighbour=" + busy + " far=" + far);

            // ---------- a mute stone placed while it gathers contains it ----------
            boolean contained = false;
            if (called != null) {
                for (int i = 0; i < 25; i++) {
                    Storms.step(level, data, called);
                }
                boolean stillGathering = data.byId(called.id()) != null && called.phase() == RecollectionStorm.Phase.GATHERING;
                LoadedChunkMemory.addMuteStone(level, a.offset(5, 0, 5));
                for (int i = 0; i < 20 && data.byId(called.id()) != null; i++) {
                    Storms.step(level, data, called);
                }
                Storms.Ended end = Storms.lastEnd();
                contained = stillGathering && data.byId(called.id()) == null && end != null && end.id() == called.id() && end.end() == Storms.End.CONTAINED
                        && freed != null && freed.isAlive() && freed.storm() == 0L;
                notes.add("muteContains gatheringBefore=" + stillGathering + " end=" + end + " residueFlag=" + (freed == null ? -1 : freed.storm()));
                LoadedChunkMemory.removeMuteStone(level, a.offset(5, 0, 5));
            }
            ok[2] = contained;
            clearStorms(data, level);
            resetArea(level, a);
            QaSupport.discardResidues(level, a);

            // ---------- a wave condenses the area's loudest memories into storm residues ----------
            ChunkPos cb = ChunkPos.containing(b);
            ImprintWriter.write(level, b.offset(-3, 0, -3), List.of(ImprintTag.SILENCE), null, false);
            ImprintWriter.write(level, b.offset(3, 0, 3), List.of(ImprintTag.FALL), null, false);
            ImprintWriter.write(level, b.offset(16, 0, 0), List.of(ImprintTag.SILENCE), null, false);
            RecollectionStorm raging = Storms.start(level, cb, "qa");
            long gatherNanos = 0L;
            long waveNanos = 0L;
            for (int i = 0; i < Storms.GATHER_TICKS && data.byId(raging.id()) != null; i++) {
                long t0 = System.nanoTime();
                Storms.step(level, data, raging);
                long spent = System.nanoTime() - t0;
                if (raging.phase() == RecollectionStorm.Phase.RAGING) {
                    waveNanos += spent; // the tick that breaks the storm runs its first wave
                } else {
                    gatherNanos += spent;
                }
            }
            Storms.Wave first = Storms.lastWave();
            List<ResidueEntity> stormResidues = Storms.living(level, raging);
            boolean drained = !QaSupport.hasTag(level, b, ImprintTag.SILENCE) && !QaSupport.hasTag(level, b, ImprintTag.FALL)
                    && !QaSupport.hasTag(level, b.offset(16, 0, 0), ImprintTag.SILENCE);
            ok[3] = raging.phase() == RecollectionStorm.Phase.RAGING && first != null && first.condensed() == 3 && stormResidues.size() == 3
                    && raging.wavesLeft() == Storms.WAVES - 1 && drained && stormResidues.stream().allMatch(r -> r.storm() == raging.id());
            notes.add("waveCondenses phase=" + raging.phase() + " wave=" + first + " residues=" + stormResidues.size() + " drained=" + drained
                    + " left=" + raging.wavesLeft() + " gatherTickNs=" + gatherNanos / Math.max(1, Storms.GATHER_TICKS - 1) + " firstWaveUs=" + waveNanos / 1000L);

            // ---------- a hushed echo within its aura swallows a storm residue's act-out (one charge) ----------
            EchoEntity echo = EchoLife.spawn(level, owner, plainRecording(level, b.offset(2, 0, -2)), EchoLesson.NONE);
            ResidueEntity fall = find(stormResidues, ImprintTag.FALL);
            if (echo != null) {
                echo.stopReplay();
                place(echo, b.offset(2, 0, -2));
                echo.setGraft(new EchoGraft(cast(ImprintTag.SILENCE, b), 6));
            }
            if (echo != null && fall != null) {
                hold(fall, b.offset(3, 1, 0));
                Residues.Fester hushed = Residues.stormWave(level, fall);
                ok[4] = hushed == Residues.Fester.HUSHED && echo.graftCharge() == 5 && fall.isAlive();
                notes.add("hushSwallows result=" + hushed + " charge=" + echo.graftCharge());
                echo.setGraft(null);
                place(echo, b.offset(-40, 0, 0));
            } else {
                notes.add("hushSwallows echo=" + (echo != null) + " fall=" + (fall != null));
            }

            // ---------- a muted centre chokes the storm (two waves at once) and starves its residues ----------
            LoadedChunkMemory.addMuteStone(level, b.offset(-6, 0, 6));
            int leftBefore = raging.wavesLeft();
            List<ResidueEntity> inCentre = new ArrayList<>();
            for (ResidueEntity residue : Storms.living(level, raging)) {
                hold(residue, b.offset(level.getRandom().nextInt(9) - 4, 1, level.getRandom().nextInt(9) - 4));
                inCentre.add(residue);
            }
            int strengthBefore = inCentre.stream().mapToInt(ResidueEntity::strength).sum();
            Storms.wave(level, data, raging);
            Storms.Wave choked = Storms.lastWave();
            int strengthAfter = inCentre.stream().mapToInt(ResidueEntity::strength).sum();
            ok[5] = choked != null && choked.choked() && raging.wavesLeft() == leftBefore - 2 && choked.starved() == inCentre.size() && choked.acted() == 0
                    && strengthAfter == strengthBefore - inCentre.size();
            notes.add("chokeStarves wave=" + choked + " left " + leftBefore + "->" + raging.wavesLeft() + " strength " + strengthBefore + "->" + strengthAfter);
            LoadedChunkMemory.removeMuteStone(level, b.offset(-6, 0, 6));
            clearStorms(data, level);
            QaSupport.discardResidues(level, b);
            resetArea(level, b);

            // ---------- nothing to feed on: the storm is spent at its first wave ----------
            RecollectionStorm hungry = Storms.start(level, cb, "qa");
            drive(level, data, hungry, Storms.GATHER_TICKS + 5);
            Storms.Ended spent = Storms.lastEnd();
            ok[6] = data.byId(hungry.id()) == null && spent != null && spent.id() == hungry.id() && spent.end() == Storms.End.SPENT;
            notes.add("spent end=" + spent);

            // ---------- two survivors: the storm passes, they stay as ordinary residues ----------
            RecollectionStorm pair = Storms.start(level, cb, "qa");
            List<ResidueEntity> two = List.of(held(level, pair, b.offset(-3, 1, 0), ImprintTag.FALL), held(level, pair, b.offset(3, 1, 0), ImprintTag.SILENCE));
            drive(level, data, pair, Storms.GATHER_TICKS + Storms.WAVES * Storms.WAVE_TICKS + 5);
            Storms.Ended passed = Storms.lastEnd();
            ok[7] = data.byId(pair.id()) == null && passed != null && passed.id() == pair.id() && passed.end() == Storms.End.PASSED
                    && two.stream().allMatch(r -> r.isAlive() && r.storm() == 0L) && scar(level, b) == null;
            notes.add("passed end=" + passed + " survivors=" + two.stream().filter(Entity::isAlive).count());
            QaSupport.discardResidues(level, b);
            resetArea(level, b);

            // ---------- three survivors merge: the Scar (site, heart, glass, boss) ----------
            ChunkPos cc = ChunkPos.containing(c);
            RecollectionStorm three = Storms.start(level, cc, "qa");
            List<ResidueEntity> merging = List.of(held(level, three, c.offset(-3, 1, 0), ImprintTag.FALL), held(level, three, c.offset(3, 1, 0), ImprintTag.SILENCE),
                    held(level, three, c.offset(0, 1, 3), ImprintTag.DEATH));
            drive(level, data, three, Storms.GATHER_TICKS + Storms.WAVES * Storms.WAVE_TICKS + 5);
            Storms.Ended merged = Storms.lastEnd();
            ScarSite site = scar(level, c);
            int mask = (1 << Temper.PLUNGING.id()) | (1 << Temper.HUSHED.id()) | (1 << Temper.GRAVE.id());
            BlockPos heart = ScarSites.findHeart(level, level.getChunkAt(c));
            int glass = count(level, c, PAD, ModBlocks.SCAR_GLASS.get());
            List<ScarEntity> bosses = level.getEntitiesOfClass(ScarEntity.class, new AABB(c).inflate(24.0D), Entity::isAlive);
            boolean bossOk = level.getDifficulty() == Difficulty.PEACEFUL ? bosses.isEmpty()
                    : bosses.size() == 1 && bosses.getFirst().merged() == 3 && bosses.getFirst().temperMask() == mask
                            && bosses.getFirst().getMaxHealth() == (float) ScarEntity.maxHealthFor(3);
            boolean blocksOk = griefing ? heart != null && glass > 0 : heart == null && glass == 0;
            ok[8] = merged != null && merged.id() == three.id() && merged.end() == Storms.End.SCAR && site != null && site.tempers() == mask
                    && merging.stream().allMatch(Entity::isRemoved) && blocksOk && bossOk && LoadedChunkMemory.stormProof(level, c.offset(20, 0, 0));
            notes.add("scarForms end=" + merged + " site=" + (site == null ? "-" : Storms.describe(site.tempers())) + " heart="
                    + (heart == null ? "-" : heart.toShortString()) + " glass=" + glass + " griefing=" + griefing + " bosses=" + bosses.size()
                    + " difficulty=" + level.getDifficulty() + " stormProof=" + LoadedChunkMemory.stormProof(level, c.offset(20, 0, 0)));
            bosses.forEach(Entity::discard);

            // ---------- mobGriefing off: the site forms but places no blocks ----------
            ChunkPos cd = ChunkPos.containing(d);
            level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
            BlockPos quietHeart = ScarSites.form(level, cd, mask);
            level.getGameRules().set(GameRules.MOB_GRIEFING, griefing, level.getServer());
            ScarSite quiet = scar(level, d);
            int quietGlass = count(level, d, PAD, ModBlocks.SCAR_GLASS.get());
            boolean noHeart = ScarSites.findHeart(level, level.getChunkAt(d)) == null;
            ok[9] = quiet != null && quiet.tempers() == mask && quietGlass == 0 && noHeart;
            notes.add("griefingOff site=" + (quiet != null) + " heart=" + !noHeart + " glass=" + quietGlass + " at " + quietHeart.toShortString());

            // ---------- the Scar: untouchable until a real lens reading pins it ----------
            ScarEntity boss = spawnScar(level, d.offset(0, 2, 0), mask, 3, d, extras);
            boolean readOk = false;
            if (boss != null) {
                float full = boss.getHealth();
                boolean unreadHurt = boss.hurtServer(level, level.damageSources().playerAttack(owner), 10.0F);
                float afterUnread = boss.getHealth();
                // Stay on the flattened pad (radius PAD): off it, real terrain can block the lens line of sight.
                park(owner, d.offset(-(PAD - 1), 0, 0));
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CHRONICLE_LENS.get()));
                owner.startUsingItem(InteractionHand.MAIN_HAND);
                int ticks = 0;
                for (; ticks < ScarEntity.READ_TICKS + 20 && !boss.isPinned(); ticks++) {
                    owner.lookAt(EntityAnchorArgument.Anchor.EYES, boss.getBoundingBox().getCenter());
                    boss.tickCount++;
                    boss.sense(level, List.of(owner));
                }
                owner.stopUsingItem();
                boolean pinned = boss.isPinned();
                boss.invulnerableTime = 0;
                boolean readHurt = boss.hurtServer(level, level.damageSources().playerAttack(owner), 10.0F);
                readOk = !unreadHurt && afterUnread == full && pinned && readHurt && boss.getHealth() < full;
                notes.add("scarNeedsReading unreadHurt=" + unreadHurt + " pinnedAfter=" + ticks + "t readHurt=" + readHurt + " health " + full + "->" + boss.getHealth());
            }
            ok[10] = readOk;

            // ---------- recall: the memory lashes players within 10 blocks ----------
            ScarEntity caster = spawnScar(level, d.offset(0, 2, 0), 1 << Temper.HUSHED.id(), 3, d, extras);
            if (boss != null) {
                boss.discard();
            }
            if (caster != null) {
                owner.removeAllEffects();
                park(owner, d.offset(4, 0, 0));
                caster.recall(level, List.of(owner));
                ok[11] = "players".equals(caster.lastRecallTarget()) && caster.lastRecall() == ImprintTag.SILENCE
                        && owner.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && caster.recallIndex() == 1;
                notes.add("scarRecall target=" + caster.lastRecallTarget() + " blind=" + owner.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS));

                // ---------- a grave-set echo draws the recall onto itself (one charge) ----------
                owner.removeAllEffects();
                if (echo != null) {
                    place(echo, d.offset(-5, 0, 0));
                    echo.setGraft(new EchoGraft(cast(ImprintTag.DEATH, d), 6));
                    caster.recall(level, List.of(owner));
                    ok[12] = "decoy".equals(caster.lastRecallTarget()) && echo.graftCharge() == 5
                            && !owner.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
                    notes.add("graveDecoy target=" + caster.lastRecallTarget() + " charge=" + echo.graftCharge() + " ownerBlind="
                            + owner.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS));
                    echo.setGraft(null);
                    place(echo, d.offset(-40, 0, 0));
                }

                // ---------- defeated: a scar fragment and a shard per merged temper ----------
                clearDrops(level, d);
                caster.pin(ScarEntity.PIN_TICKS);
                caster.setHealth(1.0F);
                caster.invulnerableTime = 0;
                caster.hurtServer(level, level.damageSources().playerAttack(owner), 20.0F);
                int fragments = 0;
                int shards = 0;
                for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(caster.blockPosition()).inflate(6.0D))) {
                    if (drop.getItem().is(ModItems.SCAR_FRAGMENT.get())) {
                        fragments += drop.getItem().getCount();
                    }
                    ImprintCast dropped = drop.getItem().get(ModDataComponents.IMPRINT_CAST.get());
                    if (Residues.isShard(drop.getItem()) && dropped != null && dropped.tag() == ImprintTag.SILENCE && dropped.intensity() == 4) {
                        shards++;
                    }
                }
                boolean mobDrops = level.getGameRules().get(GameRules.MOB_DROPS);
                List<ItemStack> big = ScarEntity.drops(level, 6, List.of(Temper.GRAVE, Temper.KINDLED), d);
                boolean bigOk = big.size() == 3 && big.getFirst().is(ModItems.SCAR_FRAGMENT.get()) && big.getFirst().getCount() == 2;
                ok[13] = caster.isDeadOrDying() && (mobDrops ? fragments == 1 && shards == 1 : fragments == 0) && bigOk;
                notes.add("scarDrops dead=" + caster.isDeadOrDying() + " fragments=" + fragments + " shards=" + shards + " mobDrops=" + mobDrops
                        + " mergedSix=" + big.size());
                clearDrops(level, d);
            }

            // ---------- a scar fragment sets an echo: three slips of graft, and a fracture no longer rejects it ----------
            if (echo != null) {
                ItemStack fragment = new ItemStack(ModItems.SCAR_FRAGMENT.get(), 2);
                owner.setItemInHand(InteractionHand.MAIN_HAND, fragment);
                place(echo, a.offset(3, 0, 3));
                park(owner, a.offset(3, 0, 5));
                echo.setGraft(new EchoGraft(cast(ImprintTag.FIRE, a), 1));
                int capBefore = echo.graftCapacity();
                InteractionResult set = echo.interact(owner, InteractionHand.MAIN_HAND, echo.position());
                InteractionResult again = echo.interact(owner, InteractionHand.MAIN_HAND, echo.position());
                int capAfter = echo.graftCapacity();
                loud(level, a, PressureBand.FRACTURE);
                EchoGrafts.checkFracture(level, echo);
                boolean kept = echo.graft() != null;
                boolean savedFlag = save(level, echo).getBooleanOr("echo_scarred", false);
                ok[14] = set == InteractionResult.SUCCESS_SERVER && again == InteractionResult.FAIL && fragment.getCount() == 1
                        && capBefore == EchoGrafts.slipCharge(Temper.KINDLED) * 2 && capAfter == EchoGrafts.slipCharge(Temper.KINDLED) * 3 && kept
                        && savedFlag;
                notes.add("fragmentSetsEcho result=" + set + " again=" + again + " cap " + capBefore + "->" + capAfter + " keptInFracture=" + kept
                        + " saved=" + savedFlag);
                owner.getInventory().clearContent();
                resetArea(level, a);
            }

            // ---------- scar glass: a placed pane wards (tracked in chunk memory), and stops warding when broken ----------
            BlockPos pane = a.offset(4, 0, 4);
            level.setBlock(pane, ModBlocks.SCAR_GLASS.get().defaultBlockState(), 3);
            ChunkMemory paneMemory = LoadedChunkMemory.existing(level.getChunkAt(pane));
            boolean warding = paneMemory != null && paneMemory.hasWard(pane) && LoadedChunkMemory.stormProof(level, a.offset(20, 0, 0));
            level.destroyBlock(pane, false);
            ChunkMemory afterMemory = LoadedChunkMemory.existing(level.getChunkAt(pane));
            boolean released = (afterMemory == null || !afterMemory.hasWard(pane)) && !LoadedChunkMemory.stormProof(level, a.offset(20, 0, 0));
            ok[15] = warding && released;
            notes.add("wardBlocks warding=" + warding + " released=" + released);

            // ---------- the site seeds one old residue per day (not muted, not twice) ----------
            LevelChunk quietChunk = level.getChunkAt(d);
            ChunkMemory quietMemory = LoadedChunkMemory.getOrCreate(quietChunk);
            QaSupport.discardResidues(level, d);
            quietMemory.setScar(new ScarSite(mask, level.getGameTime() - ScarSites.RESEED_TICKS));
            ResidueEntity seeded = ScarSites.reseed(level, quietChunk, quietMemory);
            boolean seededOk = seeded != null && seeded.isOld() && seeded.strength() == Residues.OLD_STRENGTH
                    && (mask & (1 << seeded.temper().id())) != 0;
            discard(seeded);
            ResidueEntity tooSoon = ScarSites.reseed(level, quietChunk, quietMemory);
            quietMemory.setScar(new ScarSite(mask, level.getGameTime() - ScarSites.RESEED_TICKS));
            LoadedChunkMemory.addMuteStone(level, d.offset(-5, 0, -5));
            ResidueEntity mutedSeed = ScarSites.reseed(level, quietChunk, quietMemory);
            LoadedChunkMemory.removeMuteStone(level, d.offset(-5, 0, -5));
            ok[16] = seededOk && tooSoon == null && mutedSeed == null;
            notes.add("siteReseeds seeded=" + (seeded == null ? "-" : seeded.tag().getSerializedName() + "/" + seeded.strength()) + " tooSoon="
                    + (tooSoon != null) + " muted=" + (mutedSeed != null));
            discard(tooSoon);
            discard(mutedSeed);

            // ---------- breaking the heart heals the site ----------
            BlockPos toBreak = heart;
            if (toBreak == null) {
                toBreak = c.offset(0, 0, 0);
                level.setBlock(toBreak, ModBlocks.SCAR_HEART.get().defaultBlockState(), 3);
            }
            boolean hadSite = scar(level, c) != null;
            level.destroyBlock(toBreak, false);
            ok[17] = hadSite && scar(level, c) == null && !level.getBlockState(toBreak).is(ModBlocks.SCAR_HEART.get());
            notes.add("heartHeals had=" + hadSite + " after=" + (scar(level, c) != null));

            // ---------- persistence: storm, chunk site and wards, the Scar, a storm residue ----------
            RecollectionStorm saved = new RecollectionStorm(77L, level.dimension(), cb, "qa");
            saved.setPhase(RecollectionStorm.Phase.RAGING);
            saved.spendWaves(2);
            saved.residues().add(OWNER);
            var stormJson = RecollectionStorm.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
            RecollectionStorm stormBack = RecollectionStorm.CODEC.parse(JsonOps.INSTANCE, stormJson).getOrThrow();
            boolean stormOk = stormBack.id() == 77L && stormBack.phase() == RecollectionStorm.Phase.RAGING && stormBack.wavesLeft() == Storms.WAVES - 2
                    && stormBack.center().equals(cb) && stormBack.residues().equals(List.of(OWNER)) && stormBack.dimension().equals(level.dimension());
            ChunkMemory withSite = LoadedChunkMemory.getOrCreate(level.getChunkAt(b));
            withSite.setScar(new ScarSite(mask, 1234L));
            withSite.addWard(b.offset(1, 0, 1));
            var memJson = ChunkMemory.CODEC.codec().encodeStart(JsonOps.INSTANCE, withSite).getOrThrow();
            ChunkMemory memBack = ChunkMemory.CODEC.codec().parse(JsonOps.INSTANCE, memJson).getOrThrow();
            memJson.getAsJsonObject().remove("scar");
            memJson.getAsJsonObject().remove("wards");
            ChunkMemory legacy = ChunkMemory.CODEC.codec().parse(JsonOps.INSTANCE, memJson).getOrThrow();
            boolean memOk = memBack.scar() != null && memBack.scar().tempers() == mask && memBack.scar().seededAt() == 1234L && memBack.hasWard(b.offset(1, 0, 1))
                    && legacy.scar() == null && legacy.wardCount() == 0;
            resetArea(level, b);
            ScarEntity toSave = spawnScar(level, b.offset(0, 2, 0), mask, 4, b, extras);
            ScarEntity scarBack = toSave == null ? null : reloadScar(level, toSave);
            if (scarBack != null) {
                extras.add(scarBack);
            }
            boolean scarOk = scarBack != null && scarBack.temperMask() == mask && scarBack.merged() == 4 && scarBack.home().equals(b)
                    && scarBack.getMaxHealth() == (float) ScarEntity.maxHealthFor(4);
            ResidueEntity stormBorn = Residues.spawn(level, b.offset(2, 1, 2), ImprintTag.FALL, 3, false);
            if (stormBorn != null) {
                stormBorn.setStorm(42L);
            }
            ResidueEntity residueBack = stormBorn == null ? null : reloadResidue(level, stormBorn);
            boolean residueOk = residueBack != null && residueBack.storm() == 42L;
            discard(residueBack);
            ok[18] = stormOk && memOk && scarOk && residueOk;
            notes.add("persistence storm=" + stormOk + " chunk=" + memOk + " scar=" + scarOk + " residue=" + residueOk);
        } catch (RuntimeException e) {
            Mnemolith.LOGGER.error("Mnemolith stormqa failed", e);
            notes.add("exception " + e);
        } finally {
            level.getGameRules().set(GameRules.MOB_GRIEFING, griefing, level.getServer());
            clearStorms(data, level);
            for (RecollectionStorm storm : parked) {
                data.add(storm);
            }
            for (Entity extra : extras) {
                discard(extra);
            }
            cleanup(level, owner, a);
            for (BlockPos p : sites) {
                for (ScarEntity scar : level.getEntitiesOfClass(ScarEntity.class, new AABB(p).inflate(48.0D))) {
                    scar.discard();
                }
                for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, new AABB(p).inflate(48.0D))) {
                    zombie.discard();
                }
                clearDrops(level, p);
                QaSupport.discardResidues(level, p);
                QaSupport.discardReplicants(level, p);
                flatten(level, p, PAD, 10);
                resetArea(level, p);
                releaseColumn(level, ChunkPos.containing(p));
            }
        }
        return new QaReport("stormqa", NAMES, ok, notes).log();
    }

    // ---------- helpers ----------

    /** Steps {@code storm} until it ends or {@code ticks} pass. */
    private static void drive(ServerLevel level, StormData data, RecollectionStorm storm, int ticks) {
        for (int i = 0; i < ticks && data.byId(storm.id()) != null; i++) {
            Storms.step(level, data, storm);
        }
    }

    /** A storm residue that is read (pinned) for good, so every wave it holds still. */
    private static ResidueEntity held(ServerLevel level, RecollectionStorm storm, BlockPos at, ImprintTag tag) {
        ResidueEntity residue = Residues.spawn(level, at, tag, 3, false);
        if (residue == null) {
            throw new IllegalStateException("could not place a " + tag + " residue at " + at.toShortString());
        }
        residue.pin(1_000_000);
        Storms.adopt(storm, residue);
        return residue;
    }

    private static void clearStorms(StormData data, ServerLevel level) {
        // Storms running before the pass were set aside, so every storm left is one this pass made.
        for (RecollectionStorm storm : data.storms()) {
            ServerLevel in = level.getServer().getLevel(storm.dimension());
            Storms.finish(in == null ? level : in, storm, Storms.End.DISABLED);
        }
    }

    private static @Nullable ScarSite scar(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? null : memory.scar();
    }

    private static PressureBand loud(ServerLevel level, BlockPos pos, PressureBand target) {
        LevelChunk chunk = level.getChunkAt(pos);
        for (int i = 0; i < 80; i++) {
            PressureBand band = MemoryPressure.band(LoadedChunkMemory.getOrCreate(chunk).cachedPressure());
            if (band.ordinal() >= target.ordinal()) {
                return band;
            }
            ImprintWriter.spike(level, pos, 3);
        }
        return MemoryPressure.band(LoadedChunkMemory.getOrCreate(chunk).cachedPressure());
    }

    /** Clears the memory of the 3x3 chunks around {@code pos} (a storm's area). */
    private static void resetArea(ServerLevel level, BlockPos pos) {
        ChunkPos center = ChunkPos.containing(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LoadedChunkMemory.clear(level.getChunk(center.x() + dx, center.z() + dz));
            }
        }
    }

    private static int count(ServerLevel level, BlockPos center, int radius, net.minecraft.world.level.block.Block block) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 6, radius))) {
            if (level.getBlockState(pos).is(block)) {
                found++;
            }
        }
        return found;
    }

    private static @Nullable ResidueEntity first(List<ResidueEntity> list) {
        return list.isEmpty() ? null : list.getFirst();
    }

    private static @Nullable ResidueEntity find(List<ResidueEntity> list, ImprintTag tag) {
        for (ResidueEntity residue : list) {
            if (residue.tag() == tag) {
                return residue;
            }
        }
        return null;
    }

    private static @Nullable ScarEntity spawnScar(ServerLevel level, BlockPos at, int mask, int merged, BlockPos home, List<Entity> extras) {
        ScarEntity scar = ModEntities.SCAR.get().create(level, EntitySpawnReason.COMMAND);
        if (scar == null) {
            return null;
        }
        scar.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        scar.setup(mask, merged, home);
        if (!level.addFreshEntity(scar)) {
            return null;
        }
        extras.add(scar);
        return scar;
    }

    private static ImprintCast cast(ImprintTag tag, BlockPos pos) {
        return ImprintCast.from(new Imprint(tag, ImprintWriter.intensityFor(tag), pos, java.util.Optional.empty(), Imprint.contextHash(tag, pos, 0L), 0L));
    }

    private static void park(FakePlayer player, BlockPos at) {
        player.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void place(EchoEntity echo, BlockPos at) {
        echo.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        echo.setDeltaMovement(Vec3.ZERO);
    }

    private static void hold(ResidueEntity residue, BlockPos at) {
        residue.snapTo(at.getX() + 0.5D, at.getY() + 0.25D, at.getZ() + 0.5D, residue.getYRot(), 0.0F);
        residue.setDeltaMovement(Vec3.ZERO);
    }

    private static void clearDrops(ServerLevel level, BlockPos pos) {
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(24.0D))) {
            drop.discard();
        }
        for (net.minecraft.world.entity.ExperienceOrb orb : level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new AABB(pos).inflate(24.0D))) {
            orb.discard();
        }
    }

    private static void flatten(ServerLevel level, BlockPos center, int radius, int height) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.setBlock(center.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
                level.setBlock(center.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
                for (int dy = 0; dy <= height; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
    }

    private static void discard(@Nullable Entity entity) {
        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
    }

    private static CompoundTag save(ServerLevel level, Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    private static @Nullable ScarEntity reloadScar(ServerLevel level, ScarEntity scar) {
        CompoundTag tag = save(level, scar);
        scar.discard();
        ScarEntity copy = ModEntities.SCAR.get().create(level, EntitySpawnReason.LOAD);
        if (copy == null) {
            return null;
        }
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        return level.addFreshEntity(copy) ? copy : null;
    }

    private static @Nullable ResidueEntity reloadResidue(ServerLevel level, ResidueEntity residue) {
        CompoundTag tag = save(level, residue);
        residue.discard();
        ResidueEntity copy = ModEntities.RESIDUE.get().create(level, EntitySpawnReason.LOAD);
        if (copy == null) {
            return null;
        }
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        return level.addFreshEntity(copy) ? copy : null;
    }

    private static EchoRecording plainRecording(ServerLevel level, BlockPos base) {
        Vec3 origin = Vec3.atBottomCenterOf(base);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        return new EchoRecording(OWNER, "StormQaOwner", level.dimension(), origin, buffer.array(), List.of());
    }

    private static void cleanup(ServerLevel level, FakePlayer owner, BlockPos base) {
        if (EchoPossession.isPossessing(owner)) {
            EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
        }
        AABB area = new AABB(base).inflate(400.0D, 128.0D, 64.0D);
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, area, e -> e.isOwnedBy(OWNER))) {
            echo.discardSilently();
        }
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.getInventory().clearContent();
        owner.removeAllEffects();
        owner.clearFire();
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.setData(ModAttachments.ECHO_PROGRESS.get(), EchoProgress.NONE);
        MemoryAvatar.STAND_INS.remove(OWNER);
    }
}
