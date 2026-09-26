package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.count;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoRecorder;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.echo.job.JobStatus;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.MemoryAvatar;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.EchoJobPayload;
import com.mnemolith.network.EchoNetwork;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith jobqa}. Dedicated-server pass over the stage 2 echo jobs: lessons from real recorder sessions,
 * mining with tunnelling, drop-off at a linked chest and the safety rules, a missing tool, building a rotated
 * blueprint exactly, running out of materials and resuming from the chest, save/reload mid-job, the client summary
 * size of a recording, and that another player cannot command the echo. The echo is ticked through the level's own
 * entity tick, so movement uses real collisions.
 */
public final class JobQa {
    private static final UUID OWNER = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID STRANGER = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final int MAX_TICKS = 12000;
    private static int salt;

    private JobQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    /** Runs the suite near {@code spawn} and returns its report. Shared by the command and the game test. */
    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        BlockPos base = column(level, (spawn.getX() >> 4) + 40 + salt * 6, spawn.getZ() >> 4);
        tickColumn(level, base);
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(OWNER, "JobQaOwner"));
        FakePlayer stranger = FakePlayerFactory.get(level, new GameProfile(STRANGER, "JobQaStranger"));
        MemoryAvatar.STAND_INS.put(OWNER, owner);
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.getInventory().clearContent();
        List<String> notes = new ArrayList<>();
        boolean mineLesson = false, mining = false, deposit = false, safety = false, minePersist = false, noTool = false;
        boolean buildLesson = false, buildExact = false, buildMissing = false, buildResume = false, summary = false, stranger2 = false;
        EchoEntity echo = null;
        try {
            // ---------- lessons from real recorder sessions ----------
            owner.snapTo(Vec3.atBottomCenterOf(base.offset(0, 0, 26)), 0.0F, 30.0F);
            clearBox(level, base.offset(0, 0, 22), 12, 8);
            EchoRecorder.start(owner);
            EchoRecorder.onBreak(owner, base, Blocks.IRON_ORE.defaultBlockState());
            EchoRecorder.onBreak(owner, base, Blocks.IRON_ORE.defaultBlockState());
            EchoRecorder.onBreak(owner, base, Blocks.IRON_ORE.defaultBlockState());
            EchoRecorder.onBreak(owner, base, Blocks.COAL_ORE.defaultBlockState());
            for (int i = 0; i < 25; i++) {
                EchoRecorder.tick(owner);
            }
            ItemStack mineItem = EchoRecorder.finish(owner, "jobqa");
            EchoLesson mineL = mineItem == null ? EchoLesson.NONE : mineItem.getOrDefault(ModDataComponents.ECHO_LESSON.get(), EchoLesson.NONE);
            mineLesson = mineL.teachesMining() && mineL.mining().get(0).block() == Blocks.IRON_ORE && mineL.mining().get(0).count() == 3
                    && !mineL.teachesBuilding();
            notes.add("mineLesson targets=" + mineL.mining().stream().map(t -> t.block().getDescriptionId() + "x" + t.count()).toList());
            owner.getInventory().clearContent();

            BlockPos demo = base.offset(-3, 0, 26);
            List<EchoLesson.Entry> design = design();
            EchoRecorder.start(owner);
            for (EchoLesson.Entry entry : design) {
                BlockPos at = demo.offset(entry.offset());
                level.setBlock(at, entry.state(), 2);
                EchoRecorder.onPlace(owner, at, entry.state());
                EchoRecorder.tick(owner);
            }
            // A block placed and taken away again during the recording is not part of the blueprint.
            BlockPos removed = demo.offset(5, 0, 0);
            level.setBlock(removed, Blocks.DIRT.defaultBlockState(), 2);
            EchoRecorder.onPlace(owner, removed, Blocks.DIRT.defaultBlockState());
            level.setBlock(removed, Blocks.AIR.defaultBlockState(), 2);
            for (int i = 0; i < 10; i++) {
                EchoRecorder.tick(owner);
            }
            ItemStack buildItem = EchoRecorder.finish(owner, "jobqa");
            owner.getInventory().clearContent();
            for (EchoLesson.Entry entry : design) {
                level.setBlock(demo.offset(entry.offset()), Blocks.AIR.defaultBlockState(), 2);
            }
            EchoLesson buildL = buildItem == null ? EchoLesson.NONE : buildItem.getOrDefault(ModDataComponents.ECHO_LESSON.get(), EchoLesson.NONE);
            EchoLesson.Blueprint blueprint = buildL.blueprint().orElse(null);
            buildLesson = blueprint != null && blueprint.size() == design.size() && blueprint.facing() == Direction.SOUTH
                    && blueprint.entries().get(0).offset().equals(BlockPos.ZERO) && !buildL.teachesMining();
            notes.add("buildLesson size=" + (blueprint == null ? -1 : blueprint.size()) + " facing=" + (blueprint == null ? "-" : blueprint.facing())
                    + " materials=" + (blueprint == null ? "-" : names(blueprint.materials())));

            // ---------- client summary ----------
            if (buildItem != null) {
                int full = encodedSize(level, ItemStack.OPTIONAL_STREAM_CODEC, buildItem, true);
                int netBytes = encodedSize(level, ItemStack.OPTIONAL_STREAM_CODEC, buildItem, false);
                EchoRecording rec = buildItem.get(ModDataComponents.ECHO_RECORDING.get());
                // A creative client only has a frameless stub; when it sends the stack back, the server must get the full recording again.
                boolean roundTrip = false;
                if (rec != null) {
                    EchoRecording stub = new EchoRecording(rec.owner(), rec.ownerName(), rec.dimension(), rec.origin(), new byte[0], List.of(), rec.contentKey());
                    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess(), net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
                    try {
                        EchoRecording.NETWORK_CODEC.encode(buffer, rec);
                        EchoRecording.NETWORK_CODEC.encode(buffer, stub);
                        EchoRecording.NETWORK_CODEC.decode(buffer);
                        EchoRecording back = EchoRecording.NETWORK_CODEC.decode(buffer);
                        roundTrip = back.length() == rec.length() && back.equals(rec);
                    } finally {
                        buffer.release();
                    }
                }
                summary = rec != null && netBytes < 2048 && netBytes < full && roundTrip;
                notes.add("summary networkBytes=" + netBytes + " fullRecordingBytes=" + full + " frames=" + (rec == null ? -1 : rec.length()) + " creativeRoundTrip=" + roundTrip);
            }

            // ---------- mining ----------
            Mine mine = buildMine(level, base);
            EchoRecording plain = plainRecording(level, base);
            echo = EchoLife.spawn(level, owner, plain, mineL);
            if (echo == null) {
                notes.add("spawn failed");
                return report(notes, mineLesson);
            }
            echo.stopReplay();
            echo.snapTo(Vec3.atBottomCenterOf(base), 0.0F, 0.0F);
            echo.inventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            echo.inventory().setItem(1, new ItemStack(Items.BREAD, 3));
            for (int i = 2; i < 35; i++) {
                echo.inventory().setItem(i, new ItemStack(Items.STICK, 1));
            }
            owner.snapTo(Vec3.atBottomCenterOf(base.offset(2, 0, 2)));
            boolean linked = EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.LINK_CHEST, mine.chest(), 0));
            EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.RADIUS, BlockPos.ZERO, 8));
            boolean started = EchoNetwork.applyJob(owner, EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.MODE_MINE));
            int stoneBefore = countIn(level, mine.min(), mine.max(), Blocks.STONE.defaultBlockState());

            // 5) stranger: cannot command, link or assign; interaction refused.
            JobStatus beforeStranger = echo.job().status();
            stranger.snapTo(Vec3.atBottomCenterOf(base.offset(1, 0, 1)));
            boolean s1 = !EchoNetwork.applyJob(stranger, EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.STOP));
            boolean s2 = !EchoNetwork.applyJob(stranger, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.UNLINK_CHEST, BlockPos.ZERO, 0));
            InteractionResult touched = echo.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO);
            stranger2 = s1 && s2 && touched == InteractionResult.FAIL && echo.job().mode() == EchoJob.Mode.MINE
                    && echo.job().chest() != null && echo.job().status().equals(beforeStranger);
            notes.add("stranger stop=" + !s1 + " unlink=" + !s2 + " interact=" + touched + " mode=" + echo.job().mode());

            // Mine until two ores are gone, then save and reload the entity mid-job.
            int ticks = drive(level, echo, e -> e.job().mined() >= 2);
            int minedBeforeReload = echo.job().mined();
            int itemsBeforeReload = echo.inventory().totalCount();
            EchoEntity reloaded = reload(level, echo);
            minePersist = reloaded != null && reloaded.job().mode() == EchoJob.Mode.MINE && reloaded.inventory().totalCount() == itemsBeforeReload
                    && reloaded.job().chest() != null && reloaded.job().radius() == 8;
            notes.add("minePersist ticks=" + ticks + " mined=" + minedBeforeReload + " items=" + itemsBeforeReload + " reloadedMode="
                    + (reloaded == null ? "-" : reloaded.job().mode()));
            if (reloaded == null) {
                return report(notes, mineLesson, false, false, false, false, false, buildLesson, false, false, false, summary, stranger2);
            }
            echo = reloaded;
            ticks += drive(level, echo, e -> e.job().mode() != EchoJob.Mode.MINE);
            minePersist &= echo.job().mined() > minedBeforeReload;

            Container chest = com.mnemolith.echo.job.EchoWork.container(level, mine.chest());
            int oresLeft = 0;
            for (BlockPos ore : mine.ores()) {
                if (!level.getBlockState(ore).isAir()) {
                    oresLeft++;
                }
            }
            int rawIron = countAll(echo, chest, level, base, Items.RAW_IRON);
            int cobble = countAll(echo, chest, level, base, Items.COBBLESTONE);
            int sticks = countAll(echo, chest, level, base, Items.STICK);
            int coalItems = countAll(echo, chest, level, base, Items.COAL);
            boolean coalGone = level.getBlockState(mine.coal()).isAir();
            int stoneAfter = countIn(level, mine.min(), mine.max(), Blocks.STONE.defaultBlockState());
            int tunnelled = stoneBefore - stoneAfter;
            ItemStack pick = echo.inventory().getItem(echo.inventory().find(Items.IRON_PICKAXE) < 0 ? 0 : echo.inventory().find(Items.IRON_PICKAXE));
            int damage = pick.is(Items.IRON_PICKAXE) ? pick.getDamageValue() : -1;
            JobStatus end = echo.job().status();
            // The lesson taught iron ore (x3) and coal ore (x1): five iron ores (one deepslate variant) and the coal ore are mined.
            mining = linked && started && oresLeft == 0 && coalGone && rawIron == mine.ores().size() && coalItems == 1 && cobble == tunnelled
                    && damage == mine.ores().size() + 1 + tunnelled && end.kind() == JobStatus.Kind.NOTHING_LEFT && echo.job().mined() == mine.ores().size() + 1;
            notes.add("mining ticks=" + ticks + " status=" + end.kind().getSerializedName() + " mined=" + echo.job().mined() + " oresLeft=" + oresLeft
                    + " rawIron=" + rawIron + "(echo " + countItem(echo.inventory(), Items.RAW_IRON) + ", chest " + countItem(chest, Items.RAW_IRON) + ")"
                    + " coal=" + coalItems + " cobble=" + cobble + " tunnelled=" + tunnelled + " pickDamage=" + damage);
            int chestSticks = countItem(chest, Items.STICK);
            deposit = chest != null && chestSticks == 33 && sticks == 33 && countItem(chest, Items.IRON_PICKAXE) == 0 && countItem(chest, Items.BREAD) == 0
                    && countItem(echo.inventory(), Items.BREAD) == 3 && echo.inventory().find(Items.IRON_PICKAXE) >= 0;
            notes.add("deposit chestSticks=" + chestSticks + " totalSticks=" + sticks + " echoBread=" + countItem(echo.inventory(), Items.BREAD)
                    + " chestItems=" + totalCount(chest) + " echoItems=" + echo.inventory().totalCount());

            boolean waterOre = level.getBlockState(mine.waterOre()).is(Blocks.IRON_ORE);
            boolean lavaOre = level.getBlockState(mine.lavaOre()).is(Blocks.IRON_ORE);
            boolean fluidsIntact = level.getFluidState(mine.water()).is(Fluids.WATER) && level.getFluidState(mine.water()).isSource()
                    && level.getFluidState(mine.lava()).is(Fluids.LAVA) && level.getFluidState(mine.lava()).isSource();
            boolean rimIntact = rimIntact(level, mine.water(), mine.waterOre()) && rimIntact(level, mine.lava(), mine.lavaOre());
            boolean containers = level.getBlockState(mine.chest()).is(Blocks.CHEST) && level.getBlockState(mine.barrel()).is(Blocks.BARREL);
            boolean untouched = level.getBlockState(mine.copper()).is(Blocks.COPPER_ORE) && level.getBlockState(mine.far()).is(Blocks.IRON_ORE);
            safety = waterOre && lavaOre && fluidsIntact && rimIntact && containers && untouched;
            notes.add("safety waterOre=" + waterOre + " lavaOre=" + lavaOre + " fluids=" + fluidsIntact + " rim=" + rimIntact + " containers=" + containers
                    + " untaughtCopperAndOutOfRangeOre=" + untouched);

            // No tool: an exposed ore again, the pickaxe taken away (and given back afterwards).
            level.setBlock(mine.ores().get(0), Blocks.IRON_ORE.defaultBlockState(), 3);
            int slot = echo.inventory().find(Items.IRON_PICKAXE);
            ItemStack kept = slot < 0 ? ItemStack.EMPTY : echo.inventory().removeItemNoUpdate(slot);
            echo.job().startMining(echo);
            drive(level, echo, e -> e.job().mode() != EchoJob.Mode.MINE);
            JobStatus noToolStatus = echo.job().status();
            noTool = noToolStatus.kind() == JobStatus.Kind.NO_TOOL && noToolStatus.detail().equals("pickaxe")
                    && level.getBlockState(mine.ores().get(0)).is(Blocks.IRON_ORE);
            notes.add("noTool status=" + noToolStatus.kind().getSerializedName() + " detail=" + noToolStatus.detail() + " text=\""
                    + noToolStatus.component().getString() + "\"");
            if (!kept.isEmpty()) {
                echo.inventory().insert(kept);
            }

            // ---------- building ----------
            echo.teachLesson(buildL);
            if (blueprint != null) {
                Map<Item, Integer> needs = blueprint.materials();
                // A) Enough blocks, rotated 90 degrees clockwise (the player faced west while the recording faced south).
                BlockPos siteA = base.offset(-6, 0, 18);
                BlockPos siteB = base.offset(5, 0, 18);
                // The building sites are in the next chunk: make sure it is entity-ticking so the echo stays visible to lookups.
                tickColumn(level, siteA);
                tickColumn(level, siteB);
                clearEchoItems(echo);
                for (Map.Entry<Item, Integer> need : needs.entrySet()) {
                    echo.inventory().insert(new ItemStack(need.getKey(), need.getValue()));
                }
                echo.inventory().insert(new ItemStack(Items.OAK_PLANKS, 5));
                echo.snapTo(Vec3.atBottomCenterOf(siteA.offset(3, 0, -3)), 0.0F, 0.0F);
                owner.snapTo(Vec3.atBottomCenterOf(siteA.offset(4, 0, -4)));
                Rotation rotA = blueprint.rotationTo(Direction.WEST);
                boolean placedA = EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.PLACE_BLUEPRINT, siteA, rotA.ordinal()));
                notes.add("placeA accepted=" + placedA + " visible=" + (level.getEntity(echo.getId()) == echo));
                int tA = drive(level, echo, e -> e.job().mode() != EchoJob.Mode.BUILD);
                int wrong = mismatches(level, blueprint, siteA, rotA);
                BlockState stairs = level.getBlockState(siteA.offset(new BlockPos(2, 1, 0).rotate(rotA)));
                BlockState log = level.getBlockState(siteA.offset(new BlockPos(0, 1, 0).rotate(rotA)));
                boolean rotated = stairs.is(Blocks.OAK_STAIRS) && stairs.getValue(StairBlock.FACING) == Direction.SOUTH
                        && log.is(Blocks.OAK_LOG) && log.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z;
                boolean consumed = echo.inventory().totalCount() == 5 && countItem(echo.inventory(), Items.OAK_PLANKS) == 5;
                buildExact = placedA && rotA == Rotation.CLOCKWISE_90 && wrong == 0 && rotated && consumed && echo.job().status().kind() == JobStatus.Kind.DONE;
                notes.add("buildExact ticks=" + tA + " rotation=" + rotA + " status=\"" + echo.job().status().component().getString() + "\" wrong=" + wrong
                        + " stairsFacing=" + (stairs.is(Blocks.OAK_STAIRS) ? stairs.getValue(StairBlock.FACING) : "-") + " logAxis="
                        + (log.is(Blocks.OAK_LOG) ? log.getValue(RotatedPillarBlock.AXIS) : "-") + " echoItemsLeft=" + echo.inventory().totalCount());

                // B) Too few blocks: no glass, one stair short. The echo builds what it can and waits.
                BlockPos chestB = siteB.offset(-3, 0, -3);
                level.setBlock(chestB, Blocks.CHEST.defaultBlockState(), 3);
                clearEchoItems(echo);
                for (Map.Entry<Item, Integer> need : needs.entrySet()) {
                    int n = need.getValue();
                    if (need.getKey() == Items.GLASS) {
                        n = 0;
                    } else if (need.getKey() == Items.OAK_STAIRS) {
                        n -= 1;
                    }
                    if (n > 0) {
                        echo.inventory().insert(new ItemStack(need.getKey(), n));
                    }
                }
                echo.snapTo(Vec3.atBottomCenterOf(siteB.offset(-2, 0, -2)), 0.0F, 0.0F);
                owner.snapTo(Vec3.atBottomCenterOf(siteB.offset(-2, 0, -4)));
                EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.LINK_CHEST, chestB, 0));
                EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.PLACE_BLUEPRINT, siteB, Rotation.NONE.ordinal()));
                int tB = drive(level, echo, e -> e.job().status().kind() == JobStatus.Kind.WAIT_MISSING || e.job().mode() != EchoJob.Mode.BUILD);
                JobStatus waiting = echo.job().status();
                String missingText = waiting.component().getString();
                buildMissing = echo.job().mode() == EchoJob.Mode.BUILD && waiting.kind() == JobStatus.Kind.WAIT_MISSING
                        && waiting.detail().contains("minecraft:glass=2") && waiting.detail().contains("minecraft:oak_stairs=1")
                        && mismatches(level, blueprint, siteB, Rotation.NONE) == 3;
                notes.add("buildMissing ticks=" + tB + " detail=" + waiting.detail() + " text=\"" + missingText + "\" notBuilt="
                        + mismatches(level, blueprint, siteB, Rotation.NONE));

                // Save and reload while waiting, then put the missing blocks into the linked chest: it resumes and finishes.
                EchoEntity again = reload(level, echo);
                if (again != null) {
                    echo = again;
                    boolean stillBuilding = echo.job().mode() == EchoJob.Mode.BUILD && echo.job().buildAnchor() != null;
                    drive(level, echo, 60, e -> false);
                    Container supply = com.mnemolith.echo.job.EchoWork.container(level, chestB);
                    if (supply != null) {
                        supply.setItem(0, new ItemStack(Items.GLASS, 2));
                        supply.setItem(1, new ItemStack(Items.OAK_STAIRS, 1));
                        supply.setItem(2, new ItemStack(Items.COBBLESTONE, 7));
                    }
                    int tR = drive(level, echo, e -> e.job().mode() != EchoJob.Mode.BUILD);
                    int wrongB = mismatches(level, blueprint, siteB, Rotation.NONE);
                    buildResume = stillBuilding && wrongB == 0 && echo.job().status().kind() == JobStatus.Kind.DONE && supply != null
                            && countItem(supply, Items.GLASS) == 0 && countItem(supply, Items.OAK_STAIRS) == 0 && countItem(supply, Items.COBBLESTONE) == 7
                            && countItem(echo.inventory(), Items.GLASS) == 0;
                    notes.add("buildResume reloadedMode=" + (stillBuilding ? "build" : echo.job().mode()) + " ticks=" + tR + " status=\""
                            + echo.job().status().component().getString() + "\" wrong=" + wrongB + " chestLeft=" + totalCount(supply));
                }
            }
        } catch (RuntimeException e) {
            Mnemolith.LOGGER.error("Mnemolith jobqa failed", e);
            notes.add("exception " + e);
        } finally {
            cleanup(level, owner, base);
            releaseColumn(level, new ChunkPos(base.getX() >> 4, base.getZ() >> 4));
            releaseColumn(level, ChunkPos.containing(base.offset(-6, 0, 18)));
            releaseColumn(level, ChunkPos.containing(base.offset(5, 0, 18)));
        }
        return report(notes, mineLesson, mining, deposit, safety, minePersist, noTool, buildLesson, buildExact, buildMissing, buildResume, summary, stranger2);
    }

    private static QaReport report(List<String> notes, boolean... checks) {
        String[] names = {"mineLesson", "mining", "deposit", "safety", "minePersist", "noTool", "buildLesson", "buildExact", "buildMissing",
                "buildResume", "clientSummary", "strangerRefused"};
        return new QaReport("jobqa", names, checks, notes).log();
    }

    // ---------- scenario ----------

    private record Mine(BlockPos min, BlockPos max, List<BlockPos> ores, BlockPos waterOre, BlockPos water, BlockPos lavaOre, BlockPos lava,
            BlockPos coal, BlockPos copper, BlockPos far, BlockPos chest, BlockPos barrel) {}

    /** Stone from y-11 to y-1 over 19x19 (deeper than the radius), air up to y+10, 21x21 wide, so no natural ore is within the radius of 8; ores at different depths, fluids sealed in, a chest and a barrel. */
    private static Mine buildMine(ServerLevel level, BlockPos base) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = -11; dy <= 10; dy++) {
                    level.setBlock(base.offset(dx, dy, dz), dy < 0 ? stone : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        List<BlockPos> ores = List.of(base.offset(3, -1, 0), base.offset(0, -2, 3), base.offset(-3, -3, -2), base.offset(4, -2, -4), base.offset(-2, -1, -5));
        for (int i = 0; i < ores.size(); i++) {
            level.setBlock(ores.get(i), (i == 3 ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).defaultBlockState(), 2);
        }
        BlockPos barrel = base.offset(-2, -1, -6);
        level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 2);
        BlockPos waterOre = base.offset(-5, -2, 4);
        BlockPos water = waterOre.below();
        level.setBlock(waterOre, Blocks.IRON_ORE.defaultBlockState(), 2);
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
        BlockPos lavaOre = base.offset(6, -1, -1);
        BlockPos lava = lavaOre.below();
        level.setBlock(lavaOre, Blocks.IRON_ORE.defaultBlockState(), 2);
        level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 2);
        BlockPos coal = base.offset(1, -1, 1);
        level.setBlock(coal, Blocks.COAL_ORE.defaultBlockState(), 2);
        BlockPos copper = base.offset(1, -1, -1);
        level.setBlock(copper, Blocks.COPPER_ORE.defaultBlockState(), 2);
        BlockPos far = base.offset(9, -1, 0);
        level.setBlock(far, Blocks.IRON_ORE.defaultBlockState(), 2);
        BlockPos chest = base.offset(5, 0, 5);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        return new Mine(base.offset(-9, -11, -9), base.offset(9, -1, 9), ores, waterOre, water, lavaOre, lava, coal, copper, far, chest, barrel);
    }

    /** Floor of stone under a cleared box (used for the lesson demo and the two building sites). */
    private static void clearBox(ServerLevel level, BlockPos center, int rx, int rz) {
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                level.setBlock(center.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 0; dy <= 6; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /** 15 blocks in three layers, with oriented stairs and logs so the rotation is visible. Placing order = list order. */
    private static List<EchoLesson.Entry> design() {
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState logX = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        List<EchoLesson.Entry> out = new ArrayList<>();
        for (int z = 0; z <= 1; z++) {
            for (int x = 0; x <= 2; x++) {
                out.add(new EchoLesson.Entry(new BlockPos(x, 0, z), bricks));
            }
        }
        out.add(new EchoLesson.Entry(new BlockPos(0, 1, 0), logX));
        out.add(new EchoLesson.Entry(new BlockPos(1, 1, 0), logX));
        out.add(new EchoLesson.Entry(new BlockPos(2, 1, 0), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)));
        out.add(new EchoLesson.Entry(new BlockPos(0, 1, 1), planks));
        out.add(new EchoLesson.Entry(new BlockPos(1, 1, 1), planks));
        out.add(new EchoLesson.Entry(new BlockPos(2, 1, 1), planks));
        out.add(new EchoLesson.Entry(new BlockPos(0, 2, 1), glass));
        out.add(new EchoLesson.Entry(new BlockPos(1, 2, 1), glass));
        out.add(new EchoLesson.Entry(new BlockPos(2, 2, 1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)));
        return out;
    }

    private static EchoRecording plainRecording(ServerLevel level, BlockPos base) {
        Vec3 origin = Vec3.atBottomCenterOf(base);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        return new EchoRecording(OWNER, "JobQaOwner", level.dimension(), origin, buffer.array(), List.of());
    }

    // ---------- helpers ----------

    private interface Until {
        boolean done(EchoEntity echo);
    }

    private static int drive(ServerLevel level, EchoEntity echo, Until until) {
        return drive(level, echo, MAX_TICKS, until);
    }

    /** Ticks the echo through the level's own entity tick (movement, collisions, job) until {@code until} holds. */
    private static int drive(ServerLevel level, EchoEntity echo, int max, Until until) {
        int ticks = 0;
        while (ticks < max && echo.isAlive() && !until.done(echo)) {
            level.tickNonPassenger(echo);
            ticks++;
        }
        return ticks;
    }

    /** Saves the echo to NBT, removes it, and loads a fresh entity from that NBT (what a chunk unload/reload or restart does). */
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

    private static int encodedSize(ServerLevel level, net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ItemStack> codec, ItemStack stack, boolean fullRecording) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess(), net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            if (fullRecording) {
                EchoRecording rec = stack.get(ModDataComponents.ECHO_RECORDING.get());
                if (rec != null) {
                    EchoRecording.STREAM_CODEC.encode(buffer, rec);
                }
            } else {
                codec.encode(buffer, stack);
            }
            return buffer.writerIndex();
        } finally {
            buffer.release();
        }
    }

    private static int mismatches(ServerLevel level, EchoLesson.Blueprint blueprint, BlockPos anchor, Rotation rotation) {
        int wrong = 0;
        for (EchoLesson.Entry entry : blueprint.placed(anchor, rotation)) {
            if (level.getBlockState(entry.offset()) != entry.state()) {
                wrong++;
            }
        }
        return wrong;
    }

    private static boolean rimIntact(ServerLevel level, BlockPos fluid, BlockPos ore) {
        for (Direction direction : Direction.values()) {
            BlockPos next = fluid.relative(direction);
            if (next.equals(ore)) {
                continue;
            }
            if (!level.getBlockState(next).is(Blocks.STONE)) {
                return false;
            }
        }
        return true;
    }

    private static int countIn(ServerLevel level, BlockPos min, BlockPos max, BlockState state) {
        int total = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos) == state) {
                total++;
            }
        }
        return total;
    }

    private static int countAll(EchoEntity echo, @Nullable Container chest, ServerLevel level, BlockPos base, Item item) {
        int total = countItem(echo.inventory(), item) + countItem(chest, item);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(24.0D))) {
            if (drop.getItem().is(item)) {
                total += drop.getItem().getCount();
            }
        }
        return total;
    }

    private static int countItem(@Nullable Container container, Item item) {
        if (container == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int totalCount(@Nullable Container container) {
        if (container == null) {
            return -1;
        }
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            total += container.getItem(i).getCount();
        }
        return total;
    }

    private static void clearEchoItems(EchoEntity echo) {
        for (int i = 0; i < echo.inventory().getContainerSize(); i++) {
            echo.inventory().removeItemNoUpdate(i);
        }
    }

    private static String names(Map<Item, Integer> items) {
        Map<String, Integer> out = new LinkedHashMap<>();
        items.forEach((item, n) -> out.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath(), n));
        return out.toString();
    }

    private static void cleanup(ServerLevel level, FakePlayer owner, BlockPos base) {
        if (EchoPossession.isPossessing(owner)) {
            EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
        }
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, new AABB(base).inflate(48.0D), e -> e.isOwnedBy(OWNER))) {
            echo.discardSilently();
        }
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(48.0D))) {
            drop.discard();
        }
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.getInventory().clearContent();
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        MemoryAvatar.STAND_INS.remove(OWNER);
    }
}
