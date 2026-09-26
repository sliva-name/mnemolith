package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.count;
import static com.mnemolith.command.qa.QaSupport.hasTag;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ImprintSlips;
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
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith residueqa}. Dedicated-server pass over residual echoes: condensing out of an overloaded chunk
 * (and not from faint tags), the four fester outcomes (write, mute starve, calm fade, a matching echo drinking), the
 * lash, a real lens reading (fake player raising the lens and looking at it while the residue ticks), the needle
 * slipping off an unread residue and capturing a read one, grafting and releasing a shard, a graft condensing in a loud
 * chunk, an archivist archiving one on its own and dropping it on death, a possessed body absorbing one, the
 * observatory seeding once, acting out in a fracture, and save/reload of the entity and of the chunk flag.
 */
public final class ResidueQa {
    private static final String[] NAMES = {"form", "faintNoForm", "festerWrites", "muteStarves", "calmFades", "lash", "lensRead", "needleSlips",
            "needleCaptures", "shardGraft", "shardRelease", "echoDrinks", "graftCondenses", "archivist", "possessionAbsorb", "observatorySeed",
            "actOut", "persistence"};
    private static final UUID OWNER = UUID.fromString("99999999-6666-6666-6666-666666666666");
    private static int salt;

    private ResidueQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        salt++;
        BlockPos spawn = BlockPos.containing(source.getPosition());
        BlockPos a = column(level, (spawn.getX() >> 4) - 40 - salt * 4, (spawn.getZ() >> 4) + 30);
        BlockPos b = a.offset(16, 0, 0);
        BlockPos c = a.offset(32, 0, 0);
        for (BlockPos p : List.of(a, b, c)) {
            tickColumn(level, p);
            flatten(level, p, 7, 10);
            reset(level, p);
        }
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(OWNER, "ResidueQaOwner"));
        owner.setGameMode(GameType.SURVIVAL);
        MemoryAvatar.STAND_INS.put(OWNER, owner);
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.setData(ModAttachments.ECHO_PROGRESS.get(), EchoProgress.NONE);
        owner.setData(ModAttachments.DISCOVERY.get(), new Discovery());
        owner.getInventory().clearContent();
        owner.removeAllEffects();
        owner.clearFire();
        owner.setHealth(owner.getMaxHealth());
        park(owner, a.offset(-6, 0, -6));
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        List<Entity> extras = new ArrayList<>();
        try {
            // ---------- form: the loudest graftable imprint condenses, and leaves the chunk ----------
            ImprintWriter.write(level, b, List.of(ImprintTag.DEATH), null, false);
            ImprintWriter.write(level, b.offset(2, 0, 0), List.of(ImprintTag.FIRE), null, false);
            PressureBand band = loud(level, b, PressureBand.OVERLOADED);
            int before = pressure(level, b);
            ResidueEntity formed = Residues.condense(level, level.getChunkAt(b));
            int after = pressure(level, b);
            ok[0] = band == PressureBand.OVERLOADED && formed != null && formed.tag() == ImprintTag.DEATH && !hasTag(level, b, ImprintTag.DEATH)
                    && hasTag(level, b, ImprintTag.FIRE) && after < before && formed.strength() >= 2 && Residues.condense(level, level.getChunkAt(b)) == null;
            notes.add("form band=" + band + " residue=" + (formed == null ? "-" : formed.tag().getSerializedName() + "/" + formed.strength())
                    + " pressure " + before + "->" + after + " secondBlocked=" + (formed != null));

            // ---------- faint tags do not condense ----------
            reset(level, a);
            ImprintWriter.write(level, a, List.of(ImprintTag.PATH, ImprintTag.BUILD), null, false);
            PressureBand faintBand = loud(level, a, PressureBand.OVERLOADED);
            ResidueEntity faint = Residues.condense(level, level.getChunkAt(a));
            ok[1] = faintBand == PressureBand.OVERLOADED && faint == null;
            notes.add("faint band=" + faintBand + " residue=" + (faint != null));
            reset(level, a);

            // ---------- fester: writes its memory back into a loud chunk ----------
            ResidueEntity fester = formed;
            boolean wrote = false;
            if (fester != null) {
                hold(fester, b.offset(0, 2, 0));
                Residues.Fester result = Residues.fester(level, fester);
                wrote = result == Residues.Fester.WROTE && hasTag(level, b, ImprintTag.DEATH) && fester.isAlive();
                notes.add("fester result=" + result + " deathBack=" + hasTag(level, b, ImprintTag.DEATH));
            }
            ok[2] = wrote;

            // ---------- mute: a mute stone starves it (no write) ----------
            boolean starved = false;
            if (fester != null) {
                reset(level, b);
                loud(level, b, PressureBand.OVERLOADED);
                LoadedChunkMemory.addMuteStone(level, b.offset(-3, 0, -3));
                int strength = fester.strength();
                Residues.Fester result = Residues.fester(level, fester);
                starved = result == Residues.Fester.STARVED && fester.strength() == strength - 1 && !hasTag(level, b, ImprintTag.DEATH);
                notes.add("mute result=" + result + " strength " + strength + "->" + fester.strength());
                LoadedChunkMemory.removeMuteStone(level, b.offset(-3, 0, -3));
                fester.discard();
            }
            ok[3] = starved;
            reset(level, b);

            // ---------- calm: fades to nothing; an old one holds ----------
            ResidueEntity faded = Residues.spawn(level, a.offset(2, 1, 2), ImprintTag.FALL, 1, false);
            ResidueEntity held = Residues.spawn(level, a.offset(-4, 1, 4), ImprintTag.FALL, 5, true);
            Residues.Fester fade = faded == null ? null : Residues.fester(level, faded);
            Residues.Fester hold = held == null ? null : Residues.fester(level, held);
            ok[4] = fade == Residues.Fester.DISSOLVED && faded.isRemoved() && hold == Residues.Fester.HELD && held.strength() == 5;
            notes.add("calm fade=" + fade + " old=" + hold);
            discard(held);

            // ---------- lash: an unread fire residue sets you alight ----------
            ResidueEntity fire = Residues.spawn(level, a.offset(0, 1, 0), ImprintTag.FIRE, 3, false);
            boolean lashed = false;
            if (fire != null) {
                park(owner, a.offset(0, 0, 1));
                for (int i = 0; i < 20 && !owner.isOnFire(); i++) {
                    hold(fire, a.offset(0, 1, 0));
                    // The fake player is not in the level's player list, so it is handed to the same sensing code.
                    level.tickNonPassenger(fire);
                    fire.sense(level, List.of(owner));
                }
                lashed = owner.isOnFire();
                notes.add("lash onFire=" + lashed);
                owner.clearFire();
                discard(fire);
            }
            ok[5] = lashed;
            park(owner, a.offset(-6, 0, -6));

            // ---------- lens: raise the lens, look at it for 3 s, it is read and pinned ----------
            ResidueEntity read = Residues.spawn(level, a.offset(0, 1, 0), ImprintTag.SILENCE, 4, false);
            boolean pinned = false;
            if (read != null) {
                park(owner, a.offset(-6, 0, 0));
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CHRONICLE_LENS.get()));
                owner.startUsingItem(InteractionHand.MAIN_HAND);
                boolean focusing = com.mnemolith.content.item.ChronicleLensItem.isFocusing(owner);
                int ticks = 0;
                for (; ticks < Residues.READ_TICKS + 20 && !read.isPinned(); ticks++) {
                    owner.lookAt(EntityAnchorArgument.Anchor.EYES, read.getBoundingBox().getCenter());
                    // Only the sensing step: a full tick would first sense the level's (empty) player list and undo
                    // this tick's progress, since reading decays when nobody is looking.
                    read.tickCount++;
                    read.sense(level, List.of(owner));
                }
                pinned = focusing && read.isPinned() && owner.getData(ModAttachments.DISCOVERY.get()).hasTag(ImprintTag.SILENCE);
                notes.add("lens focusing=" + focusing + " pinned=" + read.isPinned() + " after=" + ticks + "t discovered="
                        + owner.getData(ModAttachments.DISCOVERY.get()).hasTag(ImprintTag.SILENCE));
                owner.stopUsingItem();
            }
            ok[6] = pinned;

            // ---------- needle: slips off an unread residue (and it lashes), captures a read one ----------
            ItemStack needle = new ItemStack(ModItems.EXTRACTION_NEEDLE.get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, needle);
            owner.getCooldowns().removeCooldown(owner.getCooldowns().getCooldownGroup(needle));
            ResidueEntity wild = Residues.spawn(level, a.offset(3, 1, 3), ImprintTag.SILENCE, 3, false);
            boolean slipped = false;
            if (wild != null) {
                park(owner, a.offset(3, 0, 5));
                InteractionResult result = wild.interact(owner, InteractionHand.MAIN_HAND, wild.position());
                slipped = result == InteractionResult.SUCCESS_SERVER && wild.isAlive() && owner.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                        && needle.getDamageValue() == 0;
                notes.add("needleSlips result=" + result + " alive=" + wild.isAlive() + " blind=" + owner.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS));
                owner.removeAllEffects();
                discard(wild);
            }
            ok[7] = slipped;
            boolean captured = false;
            if (read != null) {
                if (!read.isPinned()) {
                    Residues.pinned(level, read, null);
                }
                park(owner, a.offset(-2, 0, 0));
                owner.getCooldowns().removeCooldown(owner.getCooldowns().getCooldownGroup(needle));
                int strength = read.strength();
                InteractionResult result = read.interact(owner, InteractionHand.MAIN_HAND, read.position());
                ItemStack shard = findShard(owner);
                ImprintCast cast = shard.get(ModDataComponents.IMPRINT_CAST.get());
                int wear = CommonConfig.EXTRACTION_DURABILITY_COST.get() * 2;
                captured = result == InteractionResult.SUCCESS_SERVER && read.isRemoved() && cast != null && cast.tag() == ImprintTag.SILENCE
                        && cast.intensity() == strength && needle.getDamageValue() == wear && !ImprintSlips.isSlip(shard);
                notes.add("needleCaptures result=" + result + " shard=" + (cast == null ? "-" : cast.tag().getSerializedName() + "/" + cast.intensity())
                        + " wear=" + needle.getDamageValue() + "/" + wear);
            }
            ok[8] = captured;
            owner.getInventory().clearContent();

            // ---------- shard graft: full capacity from strength 4; release puts it back into the world ----------
            EchoEntity echo = EchoLife.spawn(level, owner, plainRecording(level, a.offset(4, 0, -4)), EchoLesson.NONE);
            if (echo == null) {
                notes.add("echo spawn failed");
            } else {
                echo.stopReplay();
                place(echo, a.offset(4, 0, -4));
                ItemStack shard = Residues.shard(ImprintTag.EXPLOSION, 4, a, level.getGameTime());
                owner.setItemInHand(InteractionHand.MAIN_HAND, shard);
                park(owner, a.offset(4, 0, -2));
                InteractionResult result = echo.interact(owner, InteractionHand.MAIN_HAND, echo.position());
                ok[9] = result == InteractionResult.SUCCESS_SERVER && echo.graftTemper() == Temper.VOLATILE
                        && echo.graftCharge() == EchoGrafts.capacity(Temper.VOLATILE) && shard.isEmpty();
                notes.add("shardGraft result=" + result + " temper=" + echo.graftTemper() + " charge=" + echo.graftCharge() + "/" + EchoGrafts.capacity(Temper.VOLATILE));
                owner.getInventory().clearContent();
            }
            ItemStack loose = Residues.shard(ImprintTag.FALL, 3, a, level.getGameTime());
            ResidueEntity released = Residues.release(level, loose, a.offset(-3, 0, 3));
            ok[10] = released != null && released.tag() == ImprintTag.FALL && released.strength() == 3;
            notes.add("shardRelease residue=" + (released == null ? "-" : released.tag().getSerializedName() + "/" + released.strength()));
            discard(released);

            // ---------- a grafted echo of the same temper drinks the residue ----------
            if (echo != null) {
                echo.setGraft(new EchoGraft(cast(ImprintTag.FIRE, a), 5));
                ResidueEntity drink = Residues.spawn(level, a.offset(4, 1, -1), ImprintTag.FIRE, 3, false);
                Residues.Fester result = drink == null ? null : Residues.fester(level, drink);
                int expected = 5 + EchoGrafts.slipCharge(Temper.KINDLED) / 2;
                ok[11] = result == Residues.Fester.FED && echo.graftCharge() == expected && drink.strength() == 2;
                notes.add("drink result=" + result + " charge=" + echo.graftCharge() + "/" + expected + " residueStrength=" + (drink == null ? -1 : drink.strength()));
                discard(drink);
            }

            // ---------- a graft released in a loud chunk condenses into a residue ----------
            if (echo != null) {
                echo.setGraft(new EchoGraft(cast(ImprintTag.EXPLOSION, b), EchoGrafts.capacity(Temper.VOLATILE)));
                place(echo, b.offset(3, 0, 3));
                reset(level, b);
                loud(level, b, PressureBand.OVERLOADED);
                String result = EchoGrafts.release(level, echo, echo.blockPosition(), "qa");
                List<ResidueEntity> found = residues(level, b, ImprintTag.EXPLOSION);
                ok[12] = "residue".equals(result) && echo.graft() == null && found.size() == 1 && found.get(0).strength() == Residues.MAX_STRENGTH;
                notes.add("graftCondenses result=" + result + " residues=" + found.size() + " strength=" + (found.isEmpty() ? -1 : found.get(0).strength()));
                found.forEach(Entity::discard);
                reset(level, b);
                place(echo, a.offset(4, 0, -4));
            }

            // ---------- archivist: walks up to an unread residue on its own, archives it, drops the shard on death ----------
            ResidueEntity bait = Residues.spawn(level, a.offset(-4, 0, -4), ImprintTag.DEATH, 4, false);
            Archivist archivist = spawnArchivist(level, a.offset(-2, 0, -4), extras);
            boolean archived = false;
            if (bait != null && archivist != null) {
                park(owner, a.offset(6, 0, 6));
                int ticks = 0;
                for (; ticks < 200 && bait.isAlive(); ticks++) {
                    hold(bait, a.offset(-4, 0, -4));
                    level.tickNonPassenger(archivist);
                }
                ImprintCast loot = archivist.echoLoot().get(ModDataComponents.IMPRINT_CAST.get());
                boolean took = bait.isRemoved() && loot != null && loot.tag() == ImprintTag.DEATH && archivist.echoLoot().is(ModItems.RESIDUAL_SHARD.get());
                BlockPos at = archivist.blockPosition();
                archivist.kill(level);
                boolean dropped = shardDropped(level, at, ImprintTag.DEATH);
                archived = took && dropped;
                notes.add("archivist took=" + took + " after=" + ticks + "t droppedOnDeath=" + dropped);
                clearDrops(level, at);
            }
            ok[13] = archived;
            discard(bait);

            // ---------- possession: an empty hand in a possessed body absorbs it as the body's graft ----------
            if (echo != null) {
                echo.setGraft(null);
                owner.getInventory().clearContent();
                park(owner, a.offset(4, 0, -2));
                EchoPossession.Result possessed = EchoPossession.possess(owner, echo);
                ResidueEntity absorb = Residues.spawn(level, owner.blockPosition().offset(1, 0, 0), ImprintTag.FALL, 4, false);
                InteractionResult result = absorb == null ? InteractionResult.PASS : absorb.interact(owner, InteractionHand.MAIN_HAND, absorb.position());
                EchoGraft inBody = EchoGrafts.possessedGraft(owner);
                boolean back = EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
                EchoEntity returned = findEcho(level, a);
                ok[14] = possessed == EchoPossession.Result.POSSESSED && result == InteractionResult.SUCCESS_SERVER && absorb.isRemoved() && inBody != null
                        && inBody.temper() == Temper.PLUNGING && inBody.charge() == EchoGrafts.capacity(Temper.PLUNGING) && back && returned != null
                        && returned.graftTemper() == Temper.PLUNGING;
                notes.add("possession result=" + possessed + " interact=" + result + " inBody=" + (inBody == null ? "-" : inBody.temper() + "/" + inBody.charge())
                        + " returned=" + (returned == null ? "-" : returned.graftTemper()));
                discard(absorb);
            }

            // ---------- observatory: one old residue, once ----------
            LevelChunk observatory = level.getChunkAt(c);
            level.setBlock(c.offset(1, 0, 1), com.mnemolith.content.ModBlocks.COMPOSITION_REEL.get().defaultBlockState(), 3);
            LoadedChunkMemory.markObservatory(observatory);
            ResidueEntity seeded = Residues.trySeed(level, observatory);
            ResidueEntity again = Residues.trySeed(level, observatory);
            ChunkMemory seededMemory = LoadedChunkMemory.existing(observatory);
            ok[15] = seeded != null && seeded.isOld() && seeded.strength() == Residues.OLD_STRENGTH && again == null && seededMemory != null
                    && seededMemory.residueSeeded() && seeded.blockPosition().closerThan(c.offset(1, 1, 1), 3.0D);
            notes.add("observatory seeded=" + (seeded == null ? "-" : seeded.tag().getSerializedName() + "/" + seeded.strength() + " at " + seeded.blockPosition().toShortString())
                    + " again=" + (again != null));

            // ---------- act out: a death residue in a fracture raises a zombie ----------
            reset(level, b);
            PressureBand fractureBand = loud(level, b, PressureBand.FRACTURE);
            ResidueEntity grave = Residues.spawn(level, b.offset(0, 1, 0), ImprintTag.DEATH, 4, false);
            int zombiesBefore = zombies(level, b);
            Residues.Fester actResult = grave == null ? null : Residues.fester(level, grave);
            int zombiesAfter = zombies(level, b);
            ok[16] = fractureBand == PressureBand.FRACTURE && actResult == Residues.Fester.WROTE && zombiesAfter == zombiesBefore + 1;
            notes.add("actOut band=" + fractureBand + " result=" + actResult + " zombies " + zombiesBefore + "->" + zombiesAfter);
            for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, new AABB(b).inflate(24.0D))) {
                zombie.discard();
            }
            discard(grave);
            QaSupport.discardReplicants(level, b);

            // ---------- persistence: the entity and the chunk flag survive a reload; old saves load unseeded ----------
            boolean entityOk = false;
            if (seeded != null) {
                seeded.setStrength(4);
                ResidueEntity copy = reload(level, seeded);
                entityOk = copy != null && copy.tag() == seeded.tag() && copy.strength() == 4 && copy.isOld() && copy.origin().equals(seeded.origin());
                notes.add("persistence entity=" + (copy == null ? "-" : copy.tag().getSerializedName() + "/" + copy.strength() + " old=" + copy.isOld()));
                discard(copy);
            }
            boolean codecOk = false;
            if (seededMemory != null) {
                var encoded = ChunkMemory.CODEC.codec().encodeStart(JsonOps.INSTANCE, seededMemory).getOrThrow();
                ChunkMemory decoded = ChunkMemory.CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
                encoded.getAsJsonObject().remove("residue_seeded");
                ChunkMemory legacy = ChunkMemory.CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
                codecOk = decoded.residueSeeded() && decoded.observatory() && !legacy.residueSeeded() && legacy.observatory();
                notes.add("persistence codec seeded=" + decoded.residueSeeded() + " legacy=" + legacy.residueSeeded());
            }
            ok[17] = entityOk && codecOk;
        } catch (RuntimeException e) {
            Mnemolith.LOGGER.error("Mnemolith residueqa failed", e);
            notes.add("exception " + e);
        } finally {
            for (Entity extra : extras) {
                discard(extra);
            }
            cleanup(level, owner, a);
            for (BlockPos p : List.of(a, b, c)) {
                QaSupport.discardResidues(level, p);
                QaSupport.discardReplicants(level, p);
                LoadedChunkMemory.clear(level.getChunkAt(p));
                releaseColumn(level, ChunkPos.containing(p));
            }
        }
        return report(source, notes, ok);
    }

    private static int report(CommandSourceStack source, List<String> notes, boolean[] checks) {
        int passed = count(checks);
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < NAMES.length; i++) {
            line.append(NAMES[i]).append('=').append(checks[i]).append(' ');
        }
        Mnemolith.LOGGER.info("Mnemolith residueqa {}", line.toString().trim());
        for (String note : notes) {
            Mnemolith.LOGGER.info("Mnemolith residueqa note {}", note);
            source.sendSuccess(() -> Component.literal(note), false);
        }
        String summary = line.toString().trim();
        source.sendSuccess(() -> Component.literal(summary), false);
        source.sendSuccess(() -> Component.translatable("mnemolith.command.residueqa", passed, NAMES.length), true);
        return passed;
    }

    // ---------- helpers ----------

    /** Instability until the chunk reaches {@code band} (one step at a time, so it does not overshoot OVERLOADED). */
    private static PressureBand loud(ServerLevel level, BlockPos pos, PressureBand target) {
        LevelChunk chunk = level.getChunkAt(pos);
        for (int i = 0; i < 60; i++) {
            ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
            PressureBand band = MemoryPressure.band(memory.cachedPressure());
            if (band.ordinal() >= target.ordinal()) {
                return band;
            }
            ImprintWriter.spike(level, pos, 3);
        }
        return MemoryPressure.band(LoadedChunkMemory.getOrCreate(chunk).cachedPressure());
    }

    private static int pressure(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? 0 : memory.cachedPressure();
    }

    private static void reset(ServerLevel level, BlockPos pos) {
        LoadedChunkMemory.clear(level.getChunkAt(pos));
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

    /** Holds a residue in place while the test ticks it (it would otherwise drift). */
    private static void hold(ResidueEntity residue, BlockPos at) {
        residue.snapTo(at.getX() + 0.5D, at.getY() + 0.25D, at.getZ() + 0.5D, residue.getYRot(), 0.0F);
        residue.setDeltaMovement(Vec3.ZERO);
    }

    private static ItemStack findShard(FakePlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (Residues.isShard(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<ResidueEntity> residues(ServerLevel level, BlockPos pos, ImprintTag tag) {
        return level.getEntitiesOfClass(ResidueEntity.class, new AABB(pos).inflate(12.0D), r -> r.isAlive() && r.tag() == tag);
    }

    private static int zombies(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(Zombie.class, new AABB(pos).inflate(16.0D), Entity::isAlive).size();
    }

    private static boolean shardDropped(ServerLevel level, BlockPos pos, ImprintTag tag) {
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6.0D))) {
            ImprintCast cast = drop.getItem().get(ModDataComponents.IMPRINT_CAST.get());
            if (Residues.isShard(drop.getItem()) && cast != null && cast.tag() == tag) {
                return true;
            }
        }
        return false;
    }

    private static void clearDrops(ServerLevel level, BlockPos pos) {
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(24.0D))) {
            drop.discard();
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

    private static @Nullable Archivist spawnArchivist(ServerLevel level, BlockPos at, List<Entity> extras) {
        Archivist mob = ModEntities.ARCHIVIST.get().create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            return null;
        }
        mob.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        mob.setPersistenceRequired();
        if (!level.addFreshEntity(mob)) {
            return null;
        }
        extras.add(mob);
        return mob;
    }

    private static void discard(@Nullable Entity entity) {
        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
    }

    private static @Nullable ResidueEntity reload(ServerLevel level, ResidueEntity residue) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        residue.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
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
        return new EchoRecording(OWNER, "ResidueQaOwner", level.dimension(), origin, buffer.array(), List.of());
    }

    private static @Nullable EchoEntity findEcho(ServerLevel level, BlockPos base) {
        List<EchoEntity> found = level.getEntitiesOfClass(EchoEntity.class, new AABB(base).inflate(24.0D), e -> e.isOwnedBy(OWNER) && e.isAlive());
        return found.isEmpty() ? null : found.get(0);
    }

    private static void cleanup(ServerLevel level, FakePlayer owner, BlockPos base) {
        if (EchoPossession.isPossessing(owner)) {
            EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
        }
        AABB area = new AABB(base).inflate(64.0D);
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, area, e -> e.isOwnedBy(OWNER))) {
            echo.discardSilently();
        }
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) {
            drop.discard();
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
