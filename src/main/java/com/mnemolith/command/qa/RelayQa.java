package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.ArchiveVaultBlock;
import com.mnemolith.content.block.ArchiveVaultBlockEntity;
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
import com.mnemolith.echo.relay.EchoRelays;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.MemoryAvatar;
import com.mnemolith.entity.echo.ResidueEntity;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Discovery;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.vault.ArchiveVaults;
import com.mnemolith.vault.VaultContents;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith relayqa}. Dedicated-server pass over the echo relay and the archive vault.
 * <p>
 * Relay: tying two echoes with a real relay thread (and the range refusal), cutting it, the aura conduit (a hushed end
 * far away swallows the other end's work imprint), a residue drunk for the partner, a fracture making the link noise,
 * the death shock, hopping between ends while possessing (and its cooldown), and mirroring a break and a place.
 * <p>
 * Vault: drawing the loudest imprint, the bleed load in the chunk's pressure, a rupture on fracture, an explosion
 * spilling everything, the needle extracting, discharging (and a mute stone refusing), feeding a grafted echo, a carried
 * vault leaking, an archivist raid, the item keeping its contents through a real pickaxe break and placement, and
 * persistence of both the link and the vault.
 */
public final class RelayQa {
    private static final String[] NAMES = {"threadLinks", "cut", "auraConduit", "residueDrink", "noisy", "deathShock", "hop", "mirror",
            "vaultDraws", "bleedLoad", "rupture", "explosionSpill", "needleExtract", "discharge", "echoFeed", "carryLeak", "archivistRaid",
            "itemKeepsContents", "persistence"};
    private static final UUID OWNER = UUID.fromString("99999999-8888-8888-8888-888888888888");
    private static final int PAD = 9;
    private static int salt;

    private RelayQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    /** Runs the suite near {@code spawn} and returns its report. Shared by the command and the game test. */
    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        // Relay site and its far end (three chunks apart), two vault sites; away from the other suites.
        BlockPos a = column(level, (spawn.getX() >> 4) + 60 + salt * 3, (spawn.getZ() >> 4) - 40);
        BlockPos far = column(level, (a.getX() >> 4) + 3, a.getZ() >> 4);
        BlockPos b = column(level, (a.getX() >> 4) + 7, a.getZ() >> 4);
        BlockPos c = column(level, (a.getX() >> 4) + 11, a.getZ() >> 4);
        List<BlockPos> sites = List.of(a, far, b, c);
        for (BlockPos p : sites) {
            tickColumn(level, p);
            flatten(level, p, PAD, 8);
            reset(level, p);
        }
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(OWNER, "RelayQaOwner"));
        owner.setGameMode(GameType.SURVIVAL);
        MemoryAvatar.STAND_INS.put(OWNER, owner);
        EchoRegistry.get(level.getServer()).forget(OWNER);
        EchoRelays.forget(OWNER);
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.setData(ModAttachments.ECHO_PROGRESS.get(), EchoProgress.NONE);
        owner.setData(ModAttachments.DISCOVERY.get(), new Discovery());
        owner.getInventory().clearContent();
        owner.removeAllEffects();
        owner.setHealth(owner.getMaxHealth());
        owner.setShiftKeyDown(false);
        park(owner, a.offset(-4, 0, -4));
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        List<Entity> extras = new ArrayList<>();
        int range = CommonConfig.RELAY_LINK_RANGE.get();
        try {
            // ---------- a real relay thread: first end, too far, linked ----------
            EchoEntity e1 = echo(level, owner, a.offset(-3, 0, 0));
            EchoEntity e2 = echo(level, owner, far.offset(3, 0, 0));
            ItemStack thread = new ItemStack(ModItems.RELAY_THREAD.get(), 2);
            owner.setItemInHand(InteractionHand.MAIN_HAND, thread);
            if (e1 != null && e2 != null) {
                InteractionResult firstUse = e1.interact(owner, InteractionHand.MAIN_HAND, e1.position());
                boolean remembered = e1.getUUID().equals(thread.get(ModDataComponents.RELAY_FIRST.get()));
                InteractionResult tooFar = e2.interact(owner, InteractionHand.MAIN_HAND, e2.position());
                boolean refused = e1.relay() == null && e2.relay() == null && thread.getCount() == 2;
                place(e2, a.offset(3, 0, 0));
                e2.interact(owner, InteractionHand.MAIN_HAND, e2.position());
                boolean linked = e1.relay() != null && e1.relay().equals(e2.relay()) && thread.getCount() == 1
                        && !thread.has(ModDataComponents.RELAY_FIRST.get()) && EchoRelays.partner(level, e1) == e2;
                ok[0] = EchoRelays.enabled() ? remembered && refused && linked : e1.relay() == null;
                notes.add("threadLinks first=" + firstUse + " remembered=" + remembered + " farResult=" + tooFar + " refused=" + refused + " linked=" + linked
                        + " range=" + range + " threads=" + thread.getCount());

                // ---------- sneak with the thread cuts both ends ----------
                owner.setShiftKeyDown(true);
                e1.interact(owner, InteractionHand.MAIN_HAND, e1.position());
                owner.setShiftKeyDown(false);
                ok[1] = e1.relay() == null && e2.relay() == null;
                notes.add("cut a=" + e1.relay() + " b=" + e2.relay());

                // ---------- aura conduit: a hushed end 48 blocks away swallows the other end's work imprint ----------
                place(e2, far.offset(3, 0, 0));
                e2.setGraft(new EchoGraft(cast(ImprintTag.SILENCE, far), 6));
                ImprintTag unlinked = EchoGrafts.workImprint(level, e1, e1.blockPosition());
                EchoRelays.link(level, e1, e2);
                ImprintTag conduit = EchoGrafts.workImprint(level, e1, e1.blockPosition());
                ok[2] = unlinked == ImprintTag.BUILD && conduit == null && e2.graftCharge() == 5;
                notes.add("auraConduit unlinked=" + unlinked + " linked=" + conduit + " farCharge=" + e2.graftCharge() + " distance=" + Math.round(e1.distanceTo(e2)));

                // ---------- a silence residue next to one end is drunk for the hushed far end ----------
                e2.setGraft(new EchoGraft(cast(ImprintTag.SILENCE, far), 1));
                ResidueEntity residue = Residues.spawn(level, a.offset(-2, 1, 1), ImprintTag.SILENCE, 3, false);
                Residues.Fester drank = residue == null ? null : Residues.stormWave(level, residue);
                ok[3] = drank == Residues.Fester.FED && e2.graftCharge() > 1 && e1.graft() == null;
                notes.add("residueDrink result=" + drank + " farCharge=" + e2.graftCharge());
                QaSupport.discardResidues(level, a);

                // ---------- a fracture under one end: the link is noise ----------
                e2.setGraft(new EchoGraft(cast(ImprintTag.SILENCE, far), 6));
                PressureBand band = loud(level, a, PressureBand.FRACTURE);
                ImprintTag noisyWork = EchoGrafts.workImprint(level, e1, e1.blockPosition());
                boolean noisyHop = !EchoRelays.carries(level, e1, e2);
                reset(level, a);
                boolean clearAgain = EchoRelays.carries(level, e1, e2);
                ok[4] = band == PressureBand.FRACTURE && noisyWork == ImprintTag.BUILD && noisyHop && clearAgain && e2.graftCharge() == 6;
                notes.add("noisy band=" + band + " work=" + noisyWork + " carries=" + !noisyHop + " afterReset=" + clearAgain);

                // ---------- one end dies: the other is shocked, a death memory under it, link snapped ----------
                e2.setGraft(null);
                float before = e2.getHealth();
                e1.kill(level);
                boolean death = QaSupport.hasTag(level, e2.blockPosition(), ImprintTag.DEATH);
                ok[5] = !e1.isAlive() && e2.relay() == null && e2.getHealth() <= before - EchoRelays.SHOCK_DAMAGE + 0.01F && death;
                notes.add("deathShock health " + before + "->" + e2.getHealth() + " death=" + death + " relay=" + e2.relay());
                clearDrops(level, a);
            } else {
                notes.add("threadLinks echoes=" + (e1 != null) + "/" + (e2 != null));
            }
            discardEchoes(level, a);
            reset(level, a);
            reset(level, far);

            // ---------- hop: possessing one end, sneak + return key moves into the other ----------
            EchoEntity h1 = echo(level, owner, a.offset(-3, 0, 0));
            EchoEntity h2 = echo(level, owner, far.offset(3, 0, 0));
            if (h1 != null && h2 != null) {
                EchoRelays.link(level, h1, h2);
                UUID id = h1.relay();
                UUID target = h2.getUUID();
                park(owner, a.offset(-4, 0, -4));
                EchoPossession.Result result = EchoPossession.possess(owner, h1);
                owner.setShiftKeyDown(true);
                EchoRelays.returnKey(owner);
                PossessionState.Data data = EchoPossession.state(owner).data();
                boolean movedIn = EchoPossession.isPossessing(owner) && data != null && data.body().echo().equals(target)
                        && owner.distanceToSqr(Vec3.atBottomCenterOf(far.offset(3, 0, 0))) < 4.0D && h2.isRemoved();
                EchoEntity left = EchoRelays.partnerOfPossessed(owner);
                boolean leftBody = left != null && id.equals(left.relay()) && left.distanceToSqr(Vec3.atBottomCenterOf(a.offset(-3, 0, 0))) < 4.0D;
                EchoRelays.HopResult again = EchoRelays.hop(owner);
                boolean cooldownOk = CommonConfig.RELAY_HOP_COOLDOWN_SECONDS.get() > 0 ? again == EchoRelays.HopResult.COOLDOWN : again == EchoRelays.HopResult.HOPPED;
                owner.setShiftKeyDown(false);
                ok[6] = result == EchoPossession.Result.POSSESSED && movedIn && leftBody && cooldownOk && EchoRelays.possessedRelay(owner) != null;
                notes.add("hop possess=" + result + " movedIn=" + movedIn + " leftBody=" + leftBody + " again=" + again);

                // ---------- mirror: what the possessing player breaks and places, the other end repeats ----------
                EchoEntity partner = EchoRelays.partnerOfPossessed(owner);
                if (partner != null && EchoPossession.isPossessing(owner)) {
                    partner.stopReplay();
                    BlockPos mine = owner.blockPosition().offset(1, 0, 0);
                    BlockPos theirs = partner.blockPosition().offset(1, 0, 0);
                    level.setBlock(mine, Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(theirs, Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(mine, Blocks.AIR.defaultBlockState(), 3);
                    long t0 = System.nanoTime();
                    EchoRelays.MirrorResult broke = EchoRelays.mirror(owner, mine, null);
                    long breakUs = (System.nanoTime() - t0) / 1000L;
                    boolean brokeOk = level.getBlockState(theirs).isAir();
                    partner.inventory().setItem(0, new ItemStack(Items.COBBLESTONE, 4));
                    BlockPos placeMine = owner.blockPosition().offset(0, 0, 1);
                    BlockPos placeTheirs = partner.blockPosition().offset(0, 0, 1);
                    EchoRelays.MirrorResult placed = EchoRelays.mirror(owner, placeMine, Blocks.COBBLESTONE.defaultBlockState());
                    boolean placedOk = level.getBlockState(placeTheirs).is(Blocks.COBBLESTONE);
                    EchoRelays.MirrorResult farAway = EchoRelays.mirror(owner, owner.blockPosition().offset(12, 0, 0), null);
                    ok[7] = !CommonConfig.RELAY_MIRROR.get() || broke == EchoRelays.MirrorResult.DONE && brokeOk && placed == EchoRelays.MirrorResult.DONE && placedOk
                            && farAway == EchoRelays.MirrorResult.FAR;
                    notes.add("mirror break=" + broke + "/" + brokeOk + " place=" + placed + "/" + placedOk + " far=" + farAway + " breakUs=" + breakUs);
                } else {
                    notes.add("mirror partner=" + (partner != null));
                }
                EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
            } else {
                notes.add("hop echoes=" + (h1 != null) + "/" + (h2 != null));
            }
            discardEchoes(level, a);
            reset(level, a);
            reset(level, far);

            // ---------- vault: draws the loudest imprint from the area ----------
            BlockPos v = b.offset(0, 0, 0);
            ArchiveVaultBlockEntity vault = placeVault(level, v);
            ImprintWriter.write(level, b.offset(4, 0, 4), List.of(ImprintTag.DEATH), null, false);
            ImprintWriter.write(level, b.offset(-4, 0, 4), List.of(ImprintTag.FIRE), null, false);
            long d0 = System.nanoTime();
            ArchiveVaults.Draw drew = ArchiveVaults.draw(level, v, vault);
            long drawUs = (System.nanoTime() - d0) / 1000L;
            boolean tookDeath = vault.count() == 1 && vault.stored().getFirst().tag() == ImprintTag.DEATH && !QaSupport.hasTag(level, b, ImprintTag.DEATH);
            ArchiveVaults.Draw second = ArchiveVaults.draw(level, v, vault);
            ArchiveVaults.Draw third = ArchiveVaults.draw(level, v, vault);
            // Toggling with the real block: drawing lights it.
            level.setBlock(v, level.getBlockState(v).setValue(ArchiveVaultBlock.DRAWING, true), 3);
            boolean drawingState = vault.drawing();
            ok[8] = drew == ArchiveVaults.Draw.DREW && tookDeath && second == ArchiveVaults.Draw.DREW && third == ArchiveVaults.Draw.NOTHING && drawingState;
            notes.add("vaultDraws first=" + drew + " second=" + second + " third=" + third + " held=" + vault.count() + " drawing=" + drawingState + " drawUs=" + drawUs);

            // ---------- bleed: what it holds counts in its chunk's pressure ----------
            int pressureBefore = pressure(level, v);
            int loadBefore = ArchiveVaults.chunkLoad(level, v);
            for (int i = 0; i < 6; i++) {
                vault.add(imprint(ImprintTag.DEATH, v.offset(i, 0, 0)));
            }
            ArchiveVaults.refreshLoad(level, v);
            int load = ArchiveVaults.chunkLoad(level, v);
            int pressureAfter = pressure(level, v);
            ok[9] = load == ArchiveVaults.loadOf(vault.stored()) && load > 0 && pressureAfter - pressureBefore == load - loadBefore;
            notes.add("bleedLoad held=" + vault.count() + " load " + loadBefore + "->" + load + " pressure " + pressureBefore + "->" + pressureAfter);

            // ---------- rupture: the chunk fractures, half spills back at the vault's own tick ----------
            int held = vault.count();
            PressureBand fractured = loud(level, v.offset(3, 0, 3), PressureBand.FRACTURE);
            level.setBlock(v, level.getBlockState(v).setValue(ArchiveVaultBlock.DRAWING, false), 3);
            int ticks = CommonConfig.VAULT_DRAW_SECONDS.get() * 20;
            for (int i = 0; i < ticks; i++) {
                ArchiveVaults.tick(level, v, vault);
            }
            ok[10] = fractured == PressureBand.FRACTURE && vault.count() == held - (held + 1) / 2 && QaSupport.hasTag(level, v.above(), ImprintTag.DEATH);
            notes.add("rupture band=" + fractured + " held " + held + "->" + vault.count());
            reset(level, b);

            // ---------- an explosion spills everything ----------
            BlockPos boom = c.offset(0, 0, 0);
            ArchiveVaultBlockEntity blown = placeVault(level, boom);
            for (int i = 0; i < 3; i++) {
                blown.add(imprint(ImprintTag.FALL, boom.offset(i, 0, 1)));
            }
            park(owner, c.offset(-40, 0, 0));
            level.explode(null, boom.getX() + 0.5D, boom.getY() + 0.5D, boom.getZ() + 0.5D, 4.0F, Level.ExplosionInteraction.BLOCK);
            boolean gone = !level.getBlockState(boom).is(ModBlocks.ARCHIVE_VAULT.get());
            boolean spilled = QaSupport.hasTag(level, boom, ImprintTag.FALL);
            ok[11] = gone && spilled && blown.isEmpty();
            notes.add("explosionSpill gone=" + gone + " spilled=" + spilled + " left=" + blown.count());
            clearDrops(level, c);
            flatten(level, c, PAD, 8);
            reset(level, c);

            // ---------- the needle on a vault: the loudest imprint as a slip (real item use) ----------
            vault.replace(List.of(imprint(ImprintTag.FIRE, v), imprint(ImprintTag.DEATH, v)));
            park(owner, v.offset(-2, 0, 0));
            ItemStack needle = new ItemStack(ModItems.EXTRACTION_NEEDLE.get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, needle);
            owner.getCooldowns().removeCooldown(ModItems.EXTRACTION_NEEDLE.getId());
            InteractionResult pricked = needle.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(v), Direction.WEST, v, false)));
            boolean slip = QaSupport.holdsTag(owner, ImprintTag.DEATH);
            ok[12] = pricked == InteractionResult.SUCCESS_SERVER && slip && vault.count() == 1 && vault.stored().getFirst().tag() == ImprintTag.FIRE;
            notes.add("needleExtract result=" + pricked + " slip=" + slip + " left=" + vault.count());
            owner.getInventory().clearContent();

            // ---------- discharge: everything into the chunk; a mute stone refuses ----------
            reset(level, b);
            vault.replace(List.of(imprint(ImprintTag.FIRE, v), imprint(ImprintTag.DEATH, v), imprint(ImprintTag.FALL, v)));
            LoadedChunkMemory.addMuteStone(level, b.offset(-6, 0, -6));
            int refused = ArchiveVaults.discharge(level, v, vault, null);
            int keptWhileMuted = vault.count();
            LoadedChunkMemory.removeMuteStone(level, b.offset(-6, 0, -6));
            int discharged = ArchiveVaults.discharge(level, v, vault, null);
            boolean wrote = QaSupport.hasTag(level, v.above(), ImprintTag.FALL) && QaSupport.hasTag(level, v.above(), ImprintTag.DEATH);
            ok[13] = refused == -1 && keptWhileMuted == 3 && discharged == 3 && vault.isEmpty() && wrote && ArchiveVaults.chunkLoad(level, v) == 0;
            notes.add("discharge muted=" + refused + " kept=" + keptWhileMuted + " discharged=" + discharged + " wrote=" + wrote);
            reset(level, b);

            // ---------- an idle vault feeds a grafted echo nearby ----------
            EchoEntity fed = echo(level, owner, v.offset(2, 0, 0));
            boolean feedOk = false;
            if (fed != null) {
                fed.setGraft(new EchoGraft(cast(ImprintTag.SILENCE, v), 1));
                vault.replace(List.of(imprint(ImprintTag.SILENCE, v), imprint(ImprintTag.DEATH, v)));
                boolean feed = ArchiveVaults.feed(level, v, vault);
                feedOk = feed && fed.graftCharge() > 1 && vault.count() == 1 && vault.stored().getFirst().tag() == ImprintTag.DEATH;
                notes.add("echoFeed fed=" + feed + " charge=" + fed.graftCharge() + " left=" + vault.count());
            }
            ok[14] = feedOk;
            discardEchoes(level, b);

            // ---------- a carried vault leaks one imprint where its carrier stands ----------
            ItemStack carried = new ItemStack(ModItems.ARCHIVE_VAULT.get());
            carried.set(ModDataComponents.VAULT_CONTENTS.get(), new VaultContents(List.of(imprint(ImprintTag.DEATH, v), imprint(ImprintTag.DEATH, v))));
            park(owner, b.offset(-5, 0, 5));
            boolean leaked = ArchiveVaults.leak(level, owner, carried);
            VaultContents afterLeak = carried.get(ModDataComponents.VAULT_CONTENTS.get());
            ok[15] = leaked && afterLeak != null && afterLeak.imprints().size() == 1 && QaSupport.hasTag(level, b.offset(-5, 0, 5), ImprintTag.DEATH);
            notes.add("carryLeak leaked=" + leaked + " left=" + (afterLeak == null ? 0 : afterLeak.imprints().size()) + " every=" + CommonConfig.VAULT_LEAK_SECONDS.get() + "s");
            reset(level, b);

            // ---------- an archivist with free hands raids the vault ----------
            vault.replace(List.of(imprint(ImprintTag.FIRE, v), imprint(ImprintTag.DEATH, v)));
            Archivist archivist = ModEntities.ARCHIVIST.get().create(level, EntitySpawnReason.COMMAND);
            boolean raidOk = false;
            if (archivist != null) {
                archivist.snapTo(Vec3.atBottomCenterOf(v.offset(1, 0, 0)), 0.0F, 0.0F);
                level.addFreshEntity(archivist);
                extras.add(archivist);
                BlockPos aimed = ArchiveVaults.raidTarget(level, archivist.blockPosition());
                boolean raided = archivist.raidVault(level, v);
                ImprintCast loot = archivist.echoLoot().get(ModDataComponents.IMPRINT_CAST.get());
                raidOk = v.equals(aimed) && raided && loot != null && loot.tag() == ImprintTag.DEATH && vault.count() == 1 && !archivist.raidVault(level, v);
                notes.add("archivistRaid target=" + (aimed == null ? "-" : aimed.toShortString()) + " raided=" + raided + " loot="
                        + (loot == null ? "-" : loot.tag().getSerializedName()) + " left=" + vault.count());
                archivist.discard();
            }
            ok[16] = raidOk;

            // ---------- a real pickaxe break keeps the contents; placing it puts them back ----------
            vault.replace(List.of(imprint(ImprintTag.FIRE, v), imprint(ImprintTag.DEATH, v), imprint(ImprintTag.FALL, v)));
            List<Imprint> kept = List.copyOf(vault.stored());
            clearDrops(level, b);
            park(owner, v.offset(-2, 0, 0));
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            boolean broke = owner.gameMode.destroyBlock(v);
            ItemEntity dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(v).inflate(3.0D), e -> e.getItem().is(ModItems.ARCHIVE_VAULT.get()))
                    .stream().findFirst().orElse(null);
            VaultContents onItem = dropped == null ? null : dropped.getItem().get(ModDataComponents.VAULT_CONTENTS.get());
            boolean itemOk = broke && onItem != null && onItem.imprints().equals(kept) && ArchiveVaults.chunkLoad(level, v) == 0;
            boolean placedBack = false;
            if (dropped != null) {
                ItemStack item = dropped.getItem().copy();
                dropped.discard();
                owner.setItemInHand(InteractionHand.MAIN_HAND, item);
                BlockPos ground = v.below();
                item.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false)));
                if (level.getBlockEntity(v) instanceof ArchiveVaultBlockEntity back) {
                    ArchiveVaults.tick(level, v, back); // the load is counted on its first tick
                    placedBack = back.stored().equals(kept) && ArchiveVaults.chunkLoad(level, v) == ArchiveVaults.loadOf(kept);
                }
            }
            ok[17] = itemOk && placedBack;
            notes.add("itemKeepsContents broke=" + broke + " onItem=" + (onItem == null ? 0 : onItem.imprints().size()) + " placedBack=" + placedBack);

            // ---------- persistence: the vault's contents and an echo's link survive a save ----------
            boolean vaultSaved = false;
            if (level.getBlockEntity(v) instanceof ArchiveVaultBlockEntity back) {
                CompoundTag tag = back.saveCustomOnly(level.registryAccess());
                ArchiveVaultBlockEntity copy = new ArchiveVaultBlockEntity(v, back.getBlockState());
                copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                vaultSaved = copy.stored().equals(back.stored()) && copy.count() == 3;
            }
            EchoEntity p1 = echo(level, owner, a.offset(-3, 0, 0));
            EchoEntity p2 = echo(level, owner, a.offset(3, 0, 0));
            boolean linkSaved = false;
            if (p1 != null && p2 != null) {
                UUID id = EchoRelays.link(level, p1, p2);
                EchoEntity copy = reload(level, p1);
                linkSaved = copy != null && id.equals(copy.relay()) && EchoRelays.partner(level, p2) == copy;
            }
            ok[18] = vaultSaved && linkSaved;
            notes.add("persistence vault=" + vaultSaved + " link=" + linkSaved);
        } catch (RuntimeException ex) {
            notes.add("exception " + ex);
            com.mnemolith.Mnemolith.LOGGER.warn("Mnemolith relayqa threw", ex);
        } finally {
            if (EchoPossession.isPossessing(owner)) {
                EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
            }
            owner.setShiftKeyDown(false);
            for (Entity extra : extras) {
                if (!extra.isRemoved()) {
                    extra.discard();
                }
            }
            for (BlockPos p : sites) {
                discardEchoes(level, p);
                clearDrops(level, p);
                QaSupport.discardResidues(level, p);
                flatten(level, p, PAD, 8);
                reset(level, p);
                releaseColumn(level, ChunkPos.containing(p));
            }
            EchoRegistry.get(level.getServer()).forget(OWNER);
            EchoRelays.forget(OWNER);
            owner.getInventory().clearContent();
            owner.removeAllEffects();
            owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
            owner.setData(ModAttachments.ECHO_PROGRESS.get(), EchoProgress.NONE);
            MemoryAvatar.STAND_INS.remove(OWNER);
        }
        return new QaReport("relayqa", NAMES, ok, notes).log();
    }

    // ---------- helpers ----------

    private static @Nullable EchoEntity echo(ServerLevel level, FakePlayer owner, BlockPos at) {
        EchoEntity echo = EchoLife.spawn(level, owner, plainRecording(level, at), EchoLesson.NONE);
        if (echo != null) {
            echo.stopReplay();
            place(echo, at);
        }
        return echo;
    }

    private static ArchiveVaultBlockEntity placeVault(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.ARCHIVE_VAULT.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof ArchiveVaultBlockEntity vault)) {
            throw new IllegalStateException("no vault block entity at " + pos.toShortString());
        }
        ArchiveVaults.tick(level, pos, vault); // its first tick: tracked, load counted
        return vault;
    }

    private static Imprint imprint(ImprintTag tag, BlockPos pos) {
        return new Imprint(tag, ImprintWriter.intensityFor(tag), pos, Optional.empty(), Imprint.contextHash(tag, pos, pos.asLong()), 0L);
    }

    private static ImprintCast cast(ImprintTag tag, BlockPos pos) {
        return ImprintCast.from(imprint(tag, pos));
    }

    private static int pressure(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
        MemoryPressure.recompute(chunk, memory);
        return memory.cachedPressure();
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

    /** Clears the memory of the chunk of {@code pos} and its neighbours. */
    private static void reset(ServerLevel level, BlockPos pos) {
        ChunkPos center = ChunkPos.containing(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LoadedChunkMemory.clear(level.getChunk(center.x() + dx, center.z() + dz));
            }
        }
    }

    private static void park(FakePlayer player, BlockPos at) {
        player.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void place(EchoEntity echo, BlockPos at) {
        echo.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        echo.setDeltaMovement(Vec3.ZERO);
    }

    private static void discardEchoes(ServerLevel level, BlockPos pos) {
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, new AABB(pos).inflate(80.0D, 40.0D, 40.0D), e -> e.isOwnedBy(OWNER))) {
            echo.discardSilently();
        }
        EchoRegistry.get(level.getServer()).forget(OWNER);
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

    private static @Nullable EchoEntity reload(ServerLevel level, EchoEntity echo) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        echo.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        echo.discardSilently();
        EchoEntity copy = ModEntities.ECHO.get().create(level, EntitySpawnReason.LOAD);
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
        return new EchoRecording(OWNER, "RelayQaOwner", level.dimension(), origin, buffer.array(), List.of());
    }
}
