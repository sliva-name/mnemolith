package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.count;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.content.item.EchoUpgradeItem;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoProgress;
import com.mnemolith.echo.EchoRecorder;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.FarmLesson;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.echo.job.JobStatus;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.MemoryAvatar;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.entity.mob.EchoStrider;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.network.EchoNetwork;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith echo3qa}. Dedicated-server pass over echo stage 3: upgrades (limit, recording length, health,
 * recipes, guide), the farming lesson from a real recorder session, a moment replicant mimicking a build (and the work
 * imprint it leaves), overload misfires on a build (exact result, no item gained or lost), the fracture stop, a farming
 * job (harvest, replant, sow, chest), an echo strider trailing the echo, an archivist theft (and the loot dropped on
 * death), a husk hunting the echo (flee and resume), the three lens orders, navigation (water, ladders, a door and a
 * gate) and save/reload of the new state. Everything is ticked through the level's own entity tick.
 */
public final class Echo3Qa {
    private static final int CHECKS = 13;
    private static final UUID OWNER = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID STRANGER = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final int MAX_TICKS = 12000;
    private static int salt;

    private Echo3Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        salt++;
        BlockPos spawn = BlockPos.containing(source.getPosition());
        BlockPos a = column(level, (spawn.getX() >> 4) - 40 - salt * 6, (spawn.getZ() >> 4) + 20);
        // The farm stands on its own chunk's surface. With chunk A's height it could be carved into a hillside as a sealed
        // dark room: sky light 0, so the wheat popped and nothing could be planted.
        int ax = a.getX() >> 4;
        int az = a.getZ() >> 4;
        BlockPos b = a.offset(16, 0, 0);
        BlockPos c = column(level, ax, az + 1);
        BlockPos d = a.offset(16, 0, 16);
        for (BlockPos p : List.of(a, b, c, d)) {
            tickColumn(level, p);
            flatten(level, p, 7, 8);
        }
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(OWNER, "Echo3QaOwner"));
        FakePlayer stranger = FakePlayerFactory.get(level, new GameProfile(STRANGER, "Echo3QaStranger"));
        MemoryAvatar.STAND_INS.put(OWNER, owner);
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.setData(ModAttachments.ECHO_PROGRESS.get(), EchoProgress.NONE);
        owner.getInventory().clearContent();
        List<String> notes = new ArrayList<>();
        boolean upgrades = false, farmLesson = false, mimic = false, workPressure = false, misfire = false, fracture = false, farming = false;
        boolean strider = false, archivist = false, mobAttack = false, orders = false, navigation = false, persistence = false;
        List<Entity> extras = new ArrayList<>();
        try {
            // ---------- upgrades ----------
            int base = CommonConfig.ECHO_MAX_PER_PLAYER.get();
            int limit0 = EchoProgress.echoLimit(owner);
            int frames0 = EchoProgress.recordFrames(owner);
            boolean c1 = EchoUpgradeItem.absorb(owner, EchoProgress.Kind.CHORUS);
            boolean c2 = EchoUpgradeItem.absorb(owner, EchoProgress.Kind.CHORUS);
            boolean c3 = EchoUpgradeItem.absorb(owner, EchoProgress.Kind.CHORUS);
            int limit2 = EchoProgress.echoLimit(owner);
            boolean l1 = EchoUpgradeItem.absorb(owner, EchoProgress.Kind.LONG_TAKE);
            boolean l2 = EchoUpgradeItem.absorb(owner, EchoProgress.Kind.LONG_TAKE);
            boolean l3 = EchoUpgradeItem.absorb(owner, EchoProgress.Kind.LONG_TAKE);
            int frames2 = EchoProgress.recordFrames(owner);
            boolean s1 = EchoUpgradeItem.absorb(owner, EchoProgress.Kind.STURDY);
            boolean s2 = EchoUpgradeItem.absorb(owner, EchoProgress.Kind.STURDY);
            boolean s3 = EchoUpgradeItem.absorb(owner, EchoProgress.Kind.STURDY);
            double bonus = EchoProgress.bonusHealth(owner);
            boolean recipes = recipe(level, "echo_chorus_slip") && recipe(level, "echo_long_slip") && recipe(level, "echo_sturdy_slip");
            boolean guide = GuideBook.pageCount() == GuideBook.PAGE_COUNT && Component.translatable("mnemolith.guide.echoes.title").getString().length() > 0;
            boolean upgradeCore = limit0 == base && c1 && c2 && !c3 && limit2 == Math.min(CommonConfig.ECHO_MAX_PER_PLAYER_CAP.get(), base + 2)
                    && l1 && l2 && !l3 && frames2 == Math.min(EchoRecording.MAX_FRAMES, frames0 + 2 * CommonConfig.ECHO_RECORD_BONUS_SECONDS.get() * 20)
                    && s1 && s2 && !s3 && Math.abs(bonus - 2 * CommonConfig.ECHO_STURDY_HEALTH_BONUS.get()) < 0.001D && recipes && guide;
            notes.add("upgrades limit " + limit0 + "->" + limit2 + " chorus=" + c1 + "," + c2 + ",third=" + c3 + " frames " + frames0 + "->" + frames2
                    + " longTake third=" + l3 + " sturdyBonus=" + bonus + " third=" + s3 + " recipes=" + recipes + " guidePages=" + GuideBook.pageCount());

            // ---------- farming lesson from a recorder session ----------
            owner.snapTo(Vec3.atBottomCenterOf(c.offset(-5, 0, -6)), 0.0F, 30.0F);
            BlockPos demo = c.offset(-5, 0, -5);
            EchoRecorder.start(owner);
            for (int i = 0; i < 2; i++) {
                BlockPos soil = demo.offset(i, -1, 0);
                level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 2);
                EchoRecorder.onRightClickBlock(owner, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
                level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 2);
                EchoRecorder.tick(owner);
                BlockState young = Blocks.WHEAT.defaultBlockState();
                level.setBlock(soil.above(), young, 2);
                EchoRecorder.onPlace(owner, soil.above(), young);
                EchoRecorder.tick(owner);
                EchoRecorder.onBreak(owner, soil.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
                level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), 2);
                EchoRecorder.tick(owner);
            }
            for (int i = 0; i < 20; i++) {
                EchoRecorder.tick(owner);
            }
            ItemStack farmItem = EchoRecorder.finish(owner, "echo3qa");
            FarmLesson farmL = farmItem == null ? FarmLesson.NONE : farmItem.getOrDefault(ModDataComponents.ECHO_FARM.get(), FarmLesson.NONE);
            EchoLesson farmPlain = farmItem == null ? EchoLesson.NONE : farmItem.getOrDefault(ModDataComponents.ECHO_LESSON.get(), EchoLesson.NONE);
            farmLesson = farmL.teaches() && farmL.crops().equals(List.of(Blocks.WHEAT)) && farmL.tilled() == 2 && farmL.planted() == 2 && farmL.harvested() == 2
                    && !farmPlain.teachesMining() && farmPlain.blueprint().isEmpty();
            notes.add("farmLesson crops=" + farmL.crops().size() + " tilled=" + farmL.tilled() + " planted=" + farmL.planted() + " harvested=" + farmL.harvested()
                    + " mining=" + farmPlain.teachesMining() + " blueprint=" + farmPlain.blueprint().isPresent() + " names=\"" + farmL.cropNames().getString() + "\"");
            owner.getInventory().clearContent();

            // ---------- build lesson: 12 stone bricks under 12 planks ----------
            BlockPos buildDemo = a.offset(-2, 0, 3);
            List<EchoLesson.Entry> design = design();
            EchoRecorder.start(owner);
            for (EchoLesson.Entry entry : design) {
                BlockPos at = buildDemo.offset(entry.offset());
                level.setBlock(at, entry.state(), 2);
                EchoRecorder.onPlace(owner, at, entry.state());
                EchoRecorder.tick(owner);
            }
            for (int i = 0; i < 10; i++) {
                EchoRecorder.tick(owner);
            }
            ItemStack buildItem = EchoRecorder.finish(owner, "echo3qa");
            owner.getInventory().clearContent();
            for (EchoLesson.Entry entry : design) {
                level.setBlock(buildDemo.offset(entry.offset()), Blocks.AIR.defaultBlockState(), 2);
            }
            EchoLesson buildL = buildItem == null ? EchoLesson.NONE : buildItem.getOrDefault(ModDataComponents.ECHO_LESSON.get(), EchoLesson.NONE);
            EchoLesson.Blueprint blueprint = buildL.blueprint().orElse(null);
            if (blueprint == null) {
                notes.add("no blueprint from the build recording");
                return report(source, notes, upgradeCore, farmLesson);
            }

            // ---------- spawn with the sturdy body ----------
            EchoEntity echo = EchoLife.spawn(level, owner, plainRecording(level, a), buildL, farmL);
            if (echo == null) {
                notes.add("spawn failed");
                return report(source, notes, upgradeCore, farmLesson);
            }
            echo.stopReplay();
            double expectedMax = CommonConfig.ECHO_MAX_HEALTH.get() + bonus;
            upgrades = upgradeCore && Math.abs(echo.getMaxHealth() - expectedMax) < 0.01D && Math.abs(echo.bonusHealth() - bonus) < 0.01D;
            notes.add("upgrades echoMaxHealth=" + echo.getMaxHealth() + " expected=" + expectedMax);

            // ---------- replicant mimic + work imprint (chunk A, calm) ----------
            LoadedChunkMemory.clear(level.getChunkAt(a));
            BlockPos siteA = a.offset(-2, 0, -4);
            fillMaterials(echo, blueprint);
            echo.snapTo(Vec3.atBottomCenterOf(a.offset(2, 0, 2)), 0.0F, 0.0F);
            int imprintsBefore = imprints(level, a);
            int pressureBefore = pressure(level, a);
            echo.job().setBlueprintAnchor(siteA, Rotation.NONE);
            boolean startedA = echo.job().startBuilding(echo);
            MomentReplicant replicant = spawnMob(level, ModEntities.MOMENT_REPLICANT.get(), a.offset(4, 0, 0), extras);
            drive(level, echo, 8, e -> false);
            boolean mimicking = replicant != null && replicant.startMimic(level, echo) && echo.job().mimicked();
            int maxUndone = 0;
            int ticksA = 0;
            while (ticksA < MAX_TICKS && echo.job().mode() == EchoJob.Mode.BUILD) {
                level.tickNonPassenger(echo);
                maxUndone = Math.max(maxUndone, echo.job().mimicUndone());
                ticksA++;
            }
            int wrongA = mismatches(level, blueprint, siteA);
            int leftA = echo.inventory().totalCount();
            int drops = drops(level, a);
            mimic = startedA && mimicking && maxUndone >= 1 && maxUndone <= CommonConfig.ECHO_REPLICANT_UNDO_MAX.get() && wrongA == 0 && leftA == 0 && drops == 0
                    && echo.job().status().kind() == JobStatus.Kind.DONE;
            notes.add("replicantMimic started=" + mimicking + " undone=" + maxUndone + " ticks=" + ticksA + " wrong=" + wrongA + " echoItemsLeft=" + leftA
                    + " groundDrops=" + drops + " status=\"" + echo.job().status().component().getString() + "\"");
            int imprintsAfter = imprints(level, a);
            int pressureAfter = pressure(level, a);
            workPressure = imprintsAfter > imprintsBefore && pressureAfter > pressureBefore && MemoryPressure.band(pressureAfter) == PressureBand.CALM;
            notes.add("workPressure imprints " + imprintsBefore + "->" + imprintsAfter + " pressure " + pressureBefore + "->" + pressureAfter
                    + " band=" + MemoryPressure.band(pressureAfter).name() + " every=" + CommonConfig.ECHO_WORK_IMPRINT_EVERY.get());
            discard(replicant);

            // ---------- overload misfires (chunk B) ----------
            LoadedChunkMemory.clear(level.getChunkAt(b));
            int overloaded = ImprintWriter.spike(level, b, 52);
            BlockPos siteB = b.offset(-2, 0, -4);
            fillMaterials(echo, blueprint);
            echo.snapTo(Vec3.atBottomCenterOf(b.offset(2, 0, 2)), 0.0F, 0.0F);
            EchoJob.qaMisfireChance = 0.4D;
            int misfiresBefore = echo.job().misfireCount();
            echo.job().setBlueprintAnchor(siteB, Rotation.NONE);
            echo.job().startBuilding(echo);
            boolean sawStrain = false, sawNotice = false, sawWrong = false;
            int ticksB = 0;
            while (ticksB < MAX_TICKS && echo.job().mode() == EchoJob.Mode.BUILD) {
                level.tickNonPassenger(echo);
                sawStrain |= echo.job().strain() == PressureBand.OVERLOADED;
                sawNotice |= echo.job().shownStatus().kind() == JobStatus.Kind.MISFIRE;
                sawWrong |= echo.job().misfiredCount() > 0;
                ticksB++;
            }
            EchoJob.qaMisfireChance = null;
            int wrongB = mismatches(level, blueprint, siteB);
            int leftB = echo.inventory().totalCount();
            int misfires = echo.job().misfireCount() - misfiresBefore;
            misfire = MemoryPressure.band(overloaded) == PressureBand.OVERLOADED && sawStrain && sawNotice && sawWrong && misfires >= 2 && wrongB == 0
                    && leftB == 0 && drops(level, b) == 0 && echo.job().misfiredCount() == 0 && echo.job().status().kind() == JobStatus.Kind.DONE;
            notes.add("misfire pressure=" + overloaded + " strainSeen=" + sawStrain + " misfires=" + misfires + " wrongPlaced=" + sawWrong + " noticeSeen=" + sawNotice
                    + " ticks=" + ticksB + " wrong=" + wrongB + " echoItemsLeft=" + leftB + " groundDrops=" + drops(level, b)
                    + " label=\"" + Component.translatable("mnemolith.job.strain", Component.translatable(PressureBand.OVERLOADED.translationKey())).getString() + "\"");

            // ---------- fracture stop (chunk B) ----------
            for (EchoLesson.Entry entry : blueprint.placed(siteB, Rotation.NONE)) {
                level.setBlock(entry.offset(), Blocks.AIR.defaultBlockState(), 2);
            }
            fillMaterials(echo, blueprint);
            int fractured = ImprintWriter.spike(level, b, 40);
            QaSupport.discardReplicants(level, b);
            echo.job().startBuilding(echo);
            int ticksF = drive(level, echo, 200, e -> e.job().mode() != EchoJob.Mode.BUILD);
            int placedF = blueprint.size() - mismatches(level, blueprint, siteB);
            // The strain band is re-read every few ticks, so a last overload misfire can still put a wrong block down before
            // the fracture stop; it stays in the world (a documented limit), so count every filled spot, not only exact ones.
            int filledF = 0;
            for (EchoLesson.Entry entry : blueprint.placed(siteB, Rotation.NONE)) {
                if (!level.getBlockState(entry.offset()).isAir()) {
                    filledF++;
                }
            }
            boolean conserved = filledF + echo.inventory().totalCount() == blueprint.size();
            fracture = MemoryPressure.band(fractured) == PressureBand.FRACTURE && echo.job().status().kind() == JobStatus.Kind.FRACTURED
                    && echo.job().mode() == EchoJob.Mode.IDLE && ticksF <= 40 && conserved && echo.jobStopped();
            notes.add("fractureStop pressure=" + fractured + " ticks=" + ticksF + " status=\"" + echo.job().status().component().getString() + "\" placed=" + placedF
                    + " filled=" + filledF + " conserved=" + conserved);
            QaSupport.discardReplicants(level, b);
            LoadedChunkMemory.clear(level.getChunkAt(b));
            clearEchoItems(echo);

            // ---------- farming (chunk C) ----------
            LoadedChunkMemory.clear(level.getChunkAt(c));
            // Crops need light: open the sky over the field (the area may sit under an overhang) and let the light
            // engine catch up, or the wheat pops and nothing can be planted.
            openSky(level, c, 5);
            QaSupport.settleLight(level, c);
            List<BlockPos> field = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = 1; dz <= 4; dz++) {
                    BlockPos soil = c.offset(dx, -1, dz);
                    level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 2);
                    level.setBlock(soil.above(), dz <= 2 ? ((CropBlock) Blocks.WHEAT).getStateForAge(7) : Blocks.AIR.defaultBlockState(), 2);
                    field.add(soil.above());
                }
            }
            BlockPos farmChest = c.offset(3, 0, -2);
            level.setBlock(farmChest, Blocks.CHEST.defaultBlockState(), 3);
            echo.snapTo(Vec3.atBottomCenterOf(c), 0.0F, 0.0F);
            echo.inventory().insert(new ItemStack(Items.WHEAT_SEEDS, 12));
            echo.job().setRadius(4);
            echo.job().setChest(farmChest);
            boolean farmStarted = echo.job().startFarming(echo);
            boolean sawFarmLabel = false;
            int ticksFarm = 0;
            while (ticksFarm < MAX_TICKS && echo.job().mode() == EchoJob.Mode.FARM
                    && !(echo.job().status().kind() == JobStatus.Kind.FARM_WAIT && echo.job().harvested() > 0)) {
                level.tickNonPassenger(echo);
                sawFarmLabel |= echo.job().status().kind() == JobStatus.Kind.FARMING && echo.job().status().component().getString().length() > 0;
                ticksFarm++;
            }
            int planted = 0;
            for (BlockPos spot : field) {
                if (level.getBlockState(spot).is(Blocks.WHEAT)) {
                    planted++;
                }
            }
            Container chestC = com.mnemolith.echo.job.EchoWork.container(level, farmChest);
            int wheat = countItem(chestC, Items.WHEAT) + countItem(echo.inventory(), Items.WHEAT);
            int chestWheat = countItem(chestC, Items.WHEAT);
            int groundC = drops(level, c);
            farming = farmStarted && sawFarmLabel && echo.job().harvested() == 10 && planted == field.size() && wheat == 10 && chestWheat == 10 && groundC == 0
                    && echo.job().status().kind() == JobStatus.Kind.FARM_WAIT;
            notes.add("farming ticks=" + ticksFarm + " harvested=" + echo.job().harvested() + " planted=" + planted + "/" + field.size() + " chestWheat=" + chestWheat
                    + " echoSeeds=" + countItem(echo.inventory(), Items.WHEAT_SEEDS) + " chestSeeds=" + countItem(chestC, Items.WHEAT_SEEDS) + " groundDrops=" + groundC
                    + " label=\"" + echo.job().status().component().getString() + "\"");

            // ---------- echo strider trails the echo (calm: no charge) ----------
            EchoStrider striderMob = spawnMob(level, ModEntities.ECHO_STRIDER.get(), c.offset(-6, 0, -5), extras);
            float healthBefore = echo.getHealth();
            boolean sawStrider = false;
            double startDistance = striderMob == null ? 0.0D : striderMob.distanceTo(echo);
            for (int i = 0; i < 300 && striderMob != null; i++) {
                level.tickNonPassenger(striderMob);
                level.tickNonPassenger(echo);
                sawStrider |= echo.job().shownStatus().kind() == JobStatus.Kind.STRIDER;
            }
            double endDistance = striderMob == null ? 0.0D : striderMob.distanceTo(echo);
            strider = striderMob != null && striderMob.shadowedEcho(level) == echo && sawStrider && endDistance < startDistance && endDistance < 6.0D
                    && echo.getHealth() >= healthBefore;
            notes.add("striderShadow noticeSeen=" + sawStrider + " distance " + fmt(startDistance) + "->" + fmt(endDistance) + " echoHealth=" + echo.getHealth());
            discard(striderMob);

            // ---------- archivist theft ----------
            // The farm left seeds in the echo; set them aside so the largest stealable stack is the cobblestone.
            int farmSeeds = countItem(echo.inventory(), Items.WHEAT_SEEDS);
            removeAll(echo, Items.WHEAT_SEEDS);
            echo.inventory().insert(new ItemStack(Items.COBBLESTONE, 20));
            echo.inventory().insert(new ItemStack(Items.IRON_HOE));
            int cobbleBefore = countItem(echo.inventory(), Items.COBBLESTONE);
            Archivist thief = spawnMob(level, ModEntities.ARCHIVIST.get(), echo.blockPosition().offset(1, 0, 0), extras);
            boolean stole = thief != null && thief.stealFromEcho(level, echo);
            int loot = thief == null ? 0 : thief.echoLoot().getCount();
            boolean lootIsCobble = thief != null && thief.echoLoot().is(Items.COBBLESTONE);
            int cobbleAfter = countItem(echo.inventory(), Items.COBBLESTONE);
            boolean hoeKept = echo.inventory().find(Items.IRON_HOE) >= 0;
            boolean stolenLabel = echo.job().shownStatus().kind() == JobStatus.Kind.STOLEN;
            String stolenText = echo.job().shownStatus().component().getString();
            int dropped = 0;
            if (thief != null) {
                thief.kill(level);
                for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, thief.getBoundingBox().inflate(6.0D))) {
                    if (drop.getItem().is(Items.COBBLESTONE)) {
                        dropped += drop.getItem().getCount();
                    }
                }
            }
            archivist = stole && lootIsCobble && loot == Math.min(20, CommonConfig.ECHO_ARCHIVIST_STEAL_MAX.get()) && cobbleAfter == cobbleBefore - loot && hoeKept
                    && stolenLabel && dropped == loot;
            notes.add("archivistSteal spawned=" + (thief != null) + " stole=" + stole + " loot=" + loot + " echoCobble " + cobbleBefore + "->" + cobbleAfter + " hoeKept=" + hoeKept + " dropOnDeath="
                    + dropped + " label=\"" + stolenText + "\"");
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(24.0D))) {
                drop.discard();
            }
            removeAll(echo, Items.COBBLESTONE);
            if (farmSeeds > 0) {
                echo.inventory().insert(new ItemStack(Items.WHEAT_SEEDS, farmSeeds));
            }

            // ---------- a husk hunts the working echo ----------
            owner.snapTo(Vec3.atBottomCenterOf(c.offset(0, 0, 40)));
            Mob husk = spawnMob(level, net.minecraft.world.entity.EntityTypes.HUSK, echo.blockPosition().offset(6, 0, -3), extras);
            boolean targeted = false, attacked = false;
            int ticksHunt = 0;
            BlockPos fledFrom = null;
            for (; ticksHunt < 600 && husk != null; ticksHunt++) {
                level.tickNonPassenger(husk);
                level.tickNonPassenger(echo);
                targeted |= husk.getTarget() == echo;
                if (targeted && husk.distanceTo(echo) > 2.0D && ticksHunt > 200) {
                    // The QA loop runs inside one server tick, so melee goals that wait on the game clock never re-check:
                    // bring the husk to the echo and let it hit through its own attack.
                    husk.snapTo(echo.position().add(1.2D, 0.0D, 0.0D), 90.0F, 0.0F);
                }
                if (targeted && husk.distanceTo(echo) <= 2.0D && !echo.job().alarmed()) {
                    husk.doHurtTarget(level, echo);
                }
                if (echo.job().alarmed()) {
                    attacked = true;
                    fledFrom = husk.blockPosition();
                    break;
                }
            }
            String attackedText = echo.job().status().component().getString();
            boolean attackedStatus = echo.job().status().kind() == JobStatus.Kind.ATTACKED && echo.jobStopped();
            // Let it run away (the husk stands still now), then take the husk away and wait for calm.
            drive(level, echo, 80, e -> false);
            double fled = fledFrom == null ? 0.0D : Math.sqrt(echo.blockPosition().distSqr(fledFrom));
            discard(husk);
            int ticksCalm = drive(level, echo, 1200, e -> !e.job().alarmed());
            JobStatus.Kind resumed = echo.job().status().kind();
            mobAttack = targeted && attacked && attackedStatus && fled >= CommonConfig.ECHO_FLEE_DISTANCE.get() - 2 && !echo.job().alarmed()
                    && echo.job().mode() == EchoJob.Mode.FARM && (resumed == JobStatus.Kind.FARMING || resumed == JobStatus.Kind.FARM_WAIT) && echo.isAlive();
            notes.add("mobAttack targeted=" + targeted + " hitAfter=" + ticksHunt + " label=\"" + attackedText + "\" fled=" + fmt(fled) + " calmAfter=" + ticksCalm
                    + " resumed=" + resumed.getSerializedName() + " health=" + echo.getHealth() + "/" + echo.getMaxHealth());

            // ---------- lens orders ----------
            drive(level, echo, MAX_TICKS, e -> e.job().status().kind() == JobStatus.Kind.FARM_WAIT);
            BlockPos anchor = echo.job().workAnchor();
            owner.snapTo(Vec3.atBottomCenterOf(c.offset(1, 0, -1)));
            boolean needsLens = !EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.STAY, true);
            boolean stay = EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.STAY, false);
            Vec3 stayAt = echo.position();
            drive(level, echo, 60, e -> false);
            boolean stayed = stay && echo.job().order() == EchoJob.Order.STAY && echo.job().status().kind() == JobStatus.Kind.STAY && !echo.job().isWorking()
                    && echo.position().distanceTo(stayAt) < 0.3D && !echo.attractsMobs();
            stranger.snapTo(Vec3.atBottomCenterOf(c.offset(1, 0, -2)));
            boolean strangerRefused = !EchoNetwork.applyCommand(stranger, echo.getId(), EchoJob.Order.FOLLOW, false) && echo.job().order() == EchoJob.Order.STAY;
            owner.snapTo(Vec3.atBottomCenterOf(c.offset(-4, 0, -6)));
            boolean follow = EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.FOLLOW, false);
            int ticksFollow = drive(level, echo, 600, e -> e.distanceTo(owner) <= 3.2D);
            boolean followed = follow && echo.distanceTo(owner) <= 3.2D && echo.job().status().kind() == JobStatus.Kind.FOLLOW;
            owner.snapTo(Vec3.atBottomCenterOf(c.offset(0, 0, 70)));
            drive(level, echo, 5, e -> false);
            boolean lost = echo.job().status().kind() == JobStatus.Kind.LOST_OWNER && echo.job().order() == EchoJob.Order.STAY;
            owner.snapTo(Vec3.atBottomCenterOf(c.offset(1, 0, -1)));
            boolean back = EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.RETURN, false);
            int ticksReturn = drive(level, echo, 600, e -> e.job().order() == EchoJob.Order.NONE);
            JobStatus.Kind afterReturn = echo.job().status().kind();
            boolean returned = back && anchor != null && echo.blockPosition().distSqr(anchor) <= 9.0D && echo.job().mode() == EchoJob.Mode.FARM
                    && (afterReturn == JobStatus.Kind.FARMING || afterReturn == JobStatus.Kind.FARM_WAIT) && echo.job().isWorking();
            orders = needsLens && stayed && strangerRefused && followed && lost && returned;
            notes.add("lensCommands lensRequired=" + needsLens + " stay=" + stayed + " strangerRefused=" + strangerRefused + " follow=" + followed + "(" + ticksFollow
                    + "t) lost=" + lost + " return=" + returned + "(" + ticksReturn + "t, " + afterReturn.getSerializedName() + ")");

            // ---------- navigation (chunk D) ----------
            echo.job().stop(echo);
            clearEchoItems(echo);
            int doorsBefore = echo.job().doorsOpened();
            Nav water = course(level, echo, owner, d.offset(-6, 0, -6), Obstacle.WATER);
            Nav ladder = course(level, echo, owner, d.offset(-6, 0, -3), Obstacle.LADDER);
            Nav door = course(level, echo, owner, d.offset(-6, 0, 0), Obstacle.DOOR);
            Nav gate = course(level, echo, owner, d.offset(-6, 0, 3), Obstacle.GATE);
            int doorsOpened = echo.job().doorsOpened() - doorsBefore;
            navigation = water.ok() && ladder.ok() && door.ok() && gate.ok() && doorsOpened >= 2;
            notes.add("navigation water=" + water + " ladder=" + ladder + " door=" + door + " gate=" + gate + " doorsOpened=" + doorsOpened);

            // ---------- save / reload ----------
            owner.snapTo(Vec3.atBottomCenterOf(d.offset(2, 0, 6)));
            echo.job().setFarmLesson(farmL);
            EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.STAY, false);
            EchoEntity reloaded = reload(level, echo);
            CompoundTag progressTag = (CompoundTag) EchoProgress.CODEC.codec().encodeStart(NbtOps.INSTANCE, EchoProgress.of(owner)).getOrThrow();
            EchoProgress progressBack = EchoProgress.CODEC.codec().parse(NbtOps.INSTANCE, progressTag).getOrThrow();
            if (reloaded != null) {
                echo = reloaded;
            }
            persistence = reloaded != null && reloaded.job().order() == EchoJob.Order.STAY && reloaded.job().status().kind() == JobStatus.Kind.STAY
                    && reloaded.job().farmLesson().equals(farmL) && Math.abs(reloaded.bonusHealth() - bonus) < 0.01D
                    && Math.abs(reloaded.getMaxHealth() - expectedMax) < 0.01D && progressBack.equals(EchoProgress.of(owner)) && progressBack.chorus() == 2;
            notes.add("persistence order=" + (reloaded == null ? "-" : reloaded.job().order().getSerializedName()) + " farm="
                    + (reloaded != null && reloaded.job().farmLesson().equals(farmL)) + " maxHealth=" + (reloaded == null ? -1 : reloaded.getMaxHealth())
                    + " progress=" + progressBack);
        } catch (RuntimeException e) {
            Mnemolith.LOGGER.error("Mnemolith echo3qa failed", e);
            notes.add("exception " + e);
        } finally {
            EchoJob.qaMisfireChance = null;
            for (Entity extra : extras) {
                discard(extra);
            }
            cleanup(level, owner, a);
            for (BlockPos p : List.of(a, b, c, d)) {
                QaSupport.discardReplicants(level, p);
                LoadedChunkMemory.clear(level.getChunkAt(p));
                releaseColumn(level, ChunkPos.containing(p));
            }
        }
        return report(source, notes, upgrades, farmLesson, mimic, workPressure, misfire, fracture, farming, strider, archivist, mobAttack, orders, navigation,
                persistence);
    }

    private static int report(CommandSourceStack source, List<String> notes, boolean... checks) {
        int passed = count(checks);
        String[] names = {"upgrades", "farmLesson", "replicantMimic", "workPressure", "misfire", "fractureStop", "farming", "striderShadow", "archivistSteal",
                "mobAttack", "lensCommands", "navigation", "persistence"};
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            line.append(names[i]).append('=').append(i < checks.length && checks[i]).append(' ');
        }
        Mnemolith.LOGGER.info("Mnemolith echo3qa {}", line.toString().trim());
        for (String note : notes) {
            Mnemolith.LOGGER.info("Mnemolith echo3qa note {}", note);
            source.sendSuccess(() -> Component.literal(note), false);
        }
        String summary = line.toString().trim();
        source.sendSuccess(() -> Component.literal(summary), false);
        source.sendSuccess(() -> Component.translatable("mnemolith.command.echo3qa", passed, CHECKS), true);
        return passed;
    }

    // ---------- navigation courses ----------

    private enum Obstacle {
        WATER,
        LADDER,
        DOOR,
        GATE
    }

    private record Nav(boolean ok, int ticks, String detail) {
        @Override
        public String toString() {
            return this.ok + "(" + this.ticks + "t " + this.detail + ")";
        }
    }

    /**
     * A closed corridor, one block wide and 12 long, with the obstacle across it at x+4: the only way from the start
     * (x+1) to the work point (x+10) is through it. The echo gets a RETURN order and must arrive past the obstacle.
     */
    private static Nav course(ServerLevel level, EchoEntity echo, FakePlayer owner, BlockPos origin, Obstacle obstacle) {
        int height = obstacle == Obstacle.LADDER ? 6 : 4;
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int dx = -1; dx <= 12; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= height + 1; dy++) {
                    boolean wall = dy == -1 || (dy <= height && (dz != 0 || dx == -1 || dx == 12));
                    level.setBlock(origin.offset(dx, dy, dz), wall ? stone : Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
        BlockPos at = origin.offset(4, 0, 0);
        switch (obstacle) {
            case WATER -> {
                for (int dx = 3; dx <= 5; dx++) {
                    level.setBlock(origin.offset(dx, 0, 0), Blocks.WATER.defaultBlockState(), 2 | 16);
                }
            }
            case LADDER -> {
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlock(at.above(dy), stone, 2 | 16);
                    level.setBlock(at.offset(-1, dy, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), 2 | 16);
                    level.setBlock(at.offset(1, dy, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST), 2 | 16);
                }
            }
            case DOOR -> {
                for (int dy = 2; dy <= height; dy++) {
                    level.setBlock(at.above(dy), stone, 2 | 16);
                }
                BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
                level.setBlock(at, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2 | 16);
                level.setBlock(at.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2 | 16);
            }
            case GATE -> {
                for (int dy = 2; dy <= height; dy++) {
                    level.setBlock(at.above(dy), stone, 2 | 16);
                }
                level.setBlock(at, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST), 2 | 16);
            }
        }
        BlockPos start = origin.offset(1, 0, 0);
        BlockPos goal = origin.offset(10, 0, 0);
        echo.snapTo(Vec3.atBottomCenterOf(start), -90.0F, 0.0F);
        echo.setDeltaMovement(Vec3.ZERO);
        echo.job().setWorkAnchor(goal);
        owner.snapTo(Vec3.atBottomCenterOf(origin.offset(5, height + 2, 3)));
        boolean ordered = EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.RETURN, false);
        int ticks = 0;
        double maxY = echo.getY();
        boolean wet = false;
        while (ticks < 1200 && echo.job().order() != EchoJob.Order.NONE && echo.isAlive()) {
            level.tickNonPassenger(echo);
            maxY = Math.max(maxY, echo.getY());
            wet |= echo.isInWater();
            ticks++;
        }
        boolean passed = ordered && echo.getX() >= origin.getX() + 6.0D && echo.job().status().kind() == JobStatus.Kind.AT_POINT;
        String detail = "x=" + fmt(echo.getX() - origin.getX()) + " status=" + echo.job().status().kind().getSerializedName();
        boolean extra = switch (obstacle) {
            case WATER -> wet;
            case LADDER -> maxY >= origin.getY() + 3.5D;
            case DOOR -> !level.getBlockState(at).getValue(DoorBlock.OPEN);
            case GATE -> !level.getBlockState(at).getValue(FenceGateBlock.OPEN);
        };
        detail += switch (obstacle) {
            case WATER -> " waded=" + wet;
            case LADDER -> " climbedTo=+" + fmt(maxY - origin.getY());
            case DOOR, GATE -> " closedBehind=" + extra;
        };
        return new Nav(passed && extra, ticks, detail);
    }

    // ---------- scenario helpers ----------

    /** 12 stone bricks under 12 oak planks, 4 x 3. Placing order = list order. */
    private static List<EchoLesson.Entry> design() {
        List<EchoLesson.Entry> out = new ArrayList<>();
        for (int layer = 0; layer <= 1; layer++) {
            BlockState state = layer == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
            for (int z = 0; z <= 2; z++) {
                for (int x = 0; x <= 3; x++) {
                    out.add(new EchoLesson.Entry(new BlockPos(x, layer, z), state));
                }
            }
        }
        return out;
    }

    private static void fillMaterials(EchoEntity echo, EchoLesson.Blueprint blueprint) {
        clearEchoItems(echo);
        blueprint.materials().forEach((item, n) -> echo.inventory().insert(new ItemStack(item, n)));
    }

    /** Stone floor under a cleared box around {@code center}. */
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

    /** Clears everything above the flattened box around {@code center} up to the surface, so it sees the sky. */
    private static void openSky(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, center.getX() + dx, center.getZ() + dz);
                for (int y = center.getY() + 9; y < top; y++) {
                    level.setBlock(new BlockPos(center.getX() + dx, y, center.getZ() + dz), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
    }

    private static <T extends Entity> @Nullable T spawnMob(ServerLevel level, EntityType<T> type, BlockPos at, List<Entity> extras) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            return null;
        }
        mob.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        if (mob instanceof Mob m) {
            m.setPersistenceRequired();
        }
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

    private static boolean recipe(ServerLevel level, String path) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path));
        return level.getServer().getRecipeManager().byKey(key).isPresent();
    }

    private static int imprints(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? 0 : memory.imprintCount();
    }

    private static int pressure(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? 0 : memory.cachedPressure();
    }

    private static int drops(ServerLevel level, BlockPos pos) {
        int total = 0;
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(12.0D))) {
            total += drop.getItem().getCount();
        }
        return total;
    }

    private static int mismatches(ServerLevel level, EchoLesson.Blueprint blueprint, BlockPos anchor) {
        int wrong = 0;
        for (EchoLesson.Entry entry : blueprint.placed(anchor, Rotation.NONE)) {
            if (level.getBlockState(entry.offset()) != entry.state()) {
                wrong++;
            }
        }
        return wrong;
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

    private static void removeAll(EchoEntity echo, Item item) {
        for (int i = 0; i < echo.inventory().getContainerSize(); i++) {
            if (echo.inventory().getItem(i).is(item)) {
                echo.inventory().removeItemNoUpdate(i);
            }
        }
    }

    private static void clearEchoItems(EchoEntity echo) {
        for (int i = 0; i < echo.inventory().getContainerSize(); i++) {
            echo.inventory().removeItemNoUpdate(i);
        }
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static EchoRecording plainRecording(ServerLevel level, BlockPos base) {
        Vec3 origin = Vec3.atBottomCenterOf(base);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        return new EchoRecording(OWNER, "Echo3QaOwner", level.dimension(), origin, buffer.array(), List.of());
    }

    private interface Until {
        boolean done(EchoEntity echo);
    }

    private static int drive(ServerLevel level, EchoEntity echo, int max, Until until) {
        int ticks = 0;
        while (ticks < max && echo.isAlive() && !until.done(echo)) {
            level.tickNonPassenger(echo);
            ticks++;
        }
        return ticks;
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
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.setData(ModAttachments.ECHO_PROGRESS.get(), EchoProgress.NONE);
        MemoryAvatar.STAND_INS.remove(OWNER);
    }
}
