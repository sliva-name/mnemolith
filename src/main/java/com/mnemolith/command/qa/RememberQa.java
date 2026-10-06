package com.mnemolith.command.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.PastSelf;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.recall.GestureLog;
import com.mnemolith.recall.LifeMoment;
import com.mnemolith.recall.LifeMomentKind;
import com.mnemolith.recall.LifeMoments;
import com.mnemolith.recall.RememberYou;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith rememberqa [death|home|build|battle]}. Gamemaster pass over "the world remembers you": recording,
 * caps, saving, the return conditions, and each scene. With a kind, only that scene is shown beside the player who ran
 * it, a few blocks ahead. Without one, the suite runs in a far column with a fake player and a death scene is shown.
 */
public final class RememberQa {
    private static final String[] NAMES = {
            "fakeSkipped", "recorded", "merged", "kindCap", "firstKept", "totalCap", "persisted",
            "freshSkipped", "awayChosen", "tooClose", "otherDimension", "muted", "maxReplays", "otherPlayer",
            "sceneSpawns", "sceneBlocksNext", "deathScene", "notSaved", "ownerOnly", "homeBedFree", "buildTally",
            "battleNotable", "whisper", "considerCounts", "staleFreed"
    };
    private static final UUID QA_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID OTHER_ID = UUID.fromString("55555555-5555-5555-5555-555555555556");
    private static int salt = 1;

    private RememberQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (source.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            demonstrate(player, LifeMomentKind.DEATH);
        }
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static int show(CommandContext<CommandSourceStack> context, LifeMomentKind kind) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            source.sendFailure(Component.literal("rememberqa " + kind.getSerializedName() + ": run it as a player"));
            return 0;
        }
        PastSelf body = demonstrate(player, kind);
        source.sendSuccess(() -> Component.literal("rememberqa " + kind.getSerializedName() + ": " + (body == null ? "no scene" : "scene at " + body.blockPosition().toShortString())), false);
        return body == null ? 0 : 1;
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        BlockPos site = QaSupport.column(level, (spawn.getX() >> 4) + 64 + salt * 3, (spawn.getZ() >> 4) - 40);
        QaSupport.tickColumn(level, site);
        LifeMoments moments = LifeMoments.get(level.getServer());
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(QA_ID, "RememberQa"));
        FakePlayer other = FakePlayerFactory.get(level, new GameProfile(OTHER_ID, "RememberQaOther"));
        boolean[] checks = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        String dimension = level.dimension().identifier().toString();
        List<BlockPos> placed = new ArrayList<>();
        PastSelf body = null;
        PastSelf home = null;
        try {
            reset(player, moments);
            reset(other, moments);
            long now = level.getGameTime();
            long away = CommonConfig.REMEMBER_AWAY_TICKS.get();
            int cap = CommonConfig.REMEMBER_MAX_MOMENTS.get();

            RememberYou.onDeath(player, level.damageSources().generic());
            checks[0] = moments.size(QA_ID) == 0;

            RememberYou.record(player, LifeMomentKind.DEATH, site, 0.0F, "minecraft:iron_sword", "minecraft:zombie");
            List<LifeMoment> one = moments.of(QA_ID);
            checks[1] = one.size() == 1 && one.get(0).kind() == LifeMomentKind.DEATH && one.get(0).first() && one.get(0).itemId().equals("minecraft:iron_sword");

            LifeMoments.Added again = RememberYou.record(player, LifeMomentKind.DEATH, site.offset(2, 0, 0), 0.0F, "", "lava");
            List<LifeMoment> merged = moments.of(QA_ID);
            checks[2] = again == LifeMoments.Added.MERGED && merged.size() == 1 && merged.get(0).count() == 2
                    && merged.get(0).gameTime() == one.get(0).gameTime() && merged.get(0).detail().equals("lava")
                    && merged.get(0).itemId().equals("minecraft:iron_sword");

            for (int i = 1; i <= 12; i++) {
                RememberYou.record(player, LifeMomentKind.DEATH, site.offset(i * 10, 0, 0), 0.0F, "", "");
            }
            checks[3] = moments.count(QA_ID, LifeMomentKind.DEATH) == LifeMomentKind.DEATH.cap();
            checks[4] = moments.of(QA_ID).stream().anyMatch(moment -> moment.first() && moment.pos().equals(site));

            for (LifeMomentKind kind : new LifeMomentKind[] {LifeMomentKind.HOME, LifeMomentKind.BUILD, LifeMomentKind.BATTLE}) {
                for (int i = 0; i < 10; i++) {
                    RememberYou.record(player, kind, site.offset(0, 0, 40 * (i + 1) + kind.ordinal() * 500), 0.0F, "", "");
                }
            }
            long firsts = moments.of(QA_ID).stream().filter(LifeMoment::first).count();
            checks[5] = moments.size(QA_ID) <= cap && firsts == LifeMomentKind.values().length;
            notes.add("moments=" + moments.size(QA_ID) + " cap=" + cap + " firsts=" + firsts);

            LifeMoments copy = new LifeMoments();
            copy.add(QA_ID, new LifeMoment(LifeMomentKind.BATTLE, dimension, site, 33.0F, "minecraft:iron_sword", "minecraft:wither", 100L, 150L, 3, 1, false), 24);
            copy.add(QA_ID, new LifeMoment(LifeMomentKind.HOME, dimension, site.offset(50, 0, 0), -90.0F, "", "", 200L, 250L, 1, 0, false), 24);
            copy.markScene(QA_ID, 777L);
            Tag tag = LifeMoments.CODEC.encodeStart(NbtOps.INSTANCE, copy).getOrThrow();
            LifeMoments loaded = LifeMoments.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            checks[6] = loaded.of(QA_ID).equals(copy.of(QA_ID)) && loaded.lastScene(QA_ID) == 777L && loaded.of(QA_ID).get(0).first()
                    && loaded.of(QA_ID).get(0).count() == 3;

            // Return conditions. One moment at a time, the player ten blocks east of it.
            BlockPos at = site;
            player.setPos(at.getX() + 10.5D, at.getY(), at.getZ() + 0.5D);
            plant(moments, LifeMoment.fresh(LifeMomentKind.DEATH, dimension, at, 0.0F, "minecraft:iron_sword", "", now));
            checks[7] = RememberYou.candidate(player, now).isEmpty();
            plant(moments, oldDeath(dimension, at, now, away, 0));
            checks[8] = RememberYou.candidate(player, now).isPresent();
            player.setPos(at.getX() + 2.5D, at.getY(), at.getZ() + 0.5D);
            boolean close = RememberYou.candidate(player, now).isEmpty();
            player.setPos(at.getX() + 10.5D, at.getY(), at.getZ() + 0.5D);
            checks[9] = close;
            plant(moments, new LifeMoment(LifeMomentKind.DEATH, "minecraft:the_nether", at, 0.0F, "", "", now - away - 40L, now - away - 40L, 1, 0, false));
            checks[10] = RememberYou.candidate(player, now).isEmpty();
            plant(moments, oldDeath(dimension, at, now, away, 0));
            QaSupport.muteStone(level, at.below());
            checks[11] = RememberYou.candidate(player, now).isEmpty();
            LoadedChunkMemory.removeMuteStone(level, at.below());
            level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 2 | 16);
            plant(moments, oldDeath(dimension, at, now, away, CommonConfig.REMEMBER_MAX_REPLAYS.get()));
            checks[12] = RememberYou.candidate(player, now).isEmpty();
            plant(moments, oldDeath(dimension, at, now, away, 0));
            other.setPos(at.getX() + 10.5D, at.getY(), at.getZ() + 0.5D);
            checks[13] = RememberYou.candidate(other, now).isEmpty() && RememberYou.candidate(player, now).isPresent();

            // The scene.
            Optional<LifeMoment> chosen = RememberYou.candidate(player, now);
            if (chosen.isPresent()) {
                float health = player.getHealth();
                body = RememberYou.stage(player, chosen.get());
                LifeMoment after = moments.of(QA_ID).isEmpty() ? null : moments.of(QA_ID).get(0);
                checks[14] = body != null && body.isAlive() && QA_ID.equals(body.ownerId()) && body.kind() == LifeMomentKind.DEATH
                        && body.getMainHandItem().is(Items.IRON_SWORD) && after != null && after.replays() == 1
                        && moments.lastScene(QA_ID) == now && RememberYou.running(QA_ID);
                // A second valid place right away: the scene is running and both cooldowns hold it back.
                plant(moments, oldDeath(dimension, at.offset(0, 0, 6), now, away, 0));
                checks[15] = !RememberYou.ready(player, now) && RememberYou.consider(player).isEmpty();
                if (body != null) {
                    checks[17] = !body.shouldBeSaved() && !ModEntities.PAST_SELF.get().canSerialize();
                    checks[18] = !body.broadcastToPlayer(other) && body.broadcastToPlayer(player);
                    boolean fell = false;
                    int guard = PastSelf.length(LifeMomentKind.DEATH) + 60;
                    while (!body.isRemoved() && guard-- > 0) {
                        body.tick();
                        fell |= body.isDeadOrDying();
                    }
                    checks[16] = fell && body.isRemoved() && player.getHealth() == health && !RememberYou.running(QA_ID);
                    notes.add("death scene fell=" + fell + " removed=" + body.isRemoved() + " age=" + body.age());
                }
            }

            // Home: a real bed, and the figure lies in it without taking it.
            BlockPos head = site.offset(-8, 0, 4);
            Direction facing = Direction.EAST;
            BlockPos foot = head.relative(facing.getOpposite());
            for (BlockPos pos : List.of(head, foot, foot.relative(facing.getClockWise()), foot.relative(facing.getCounterClockWise()))) {
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2 | 16);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, facing);
            level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 2 | 16);
            level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2 | 16);
            placed.add(head);
            placed.add(foot);
            home = RememberYou.stage(player, new LifeMoment(LifeMomentKind.HOME, dimension, head, 90.0F, "", "", now - away - 40L, now - away - 40L, 1, 0, true));
            if (home != null) {
                boolean lay = false;
                boolean free = true;
                int guard = PastSelf.length(LifeMomentKind.HOME) + 40;
                while (!home.isRemoved() && guard-- > 0) {
                    home.tick();
                    if (home.lying()) {
                        lay |= home.isSleeping() && home.getBedOrientation() == facing;
                    }
                    free &= !level.getBlockState(head).getValue(BedBlock.OCCUPIED);
                }
                checks[19] = lay && free && home.isRemoved() && level.getBlockState(head).getBlock() instanceof BedBlock
                        && !level.getBlockState(head).getValue(BedBlock.OCCUPIED);
                notes.add("home lay=" + lay + " free=" + free);
            }

            // Build: 64 blocks around one spot make one build of the most used block.
            moments.forget(QA_ID);
            int blocks = CommonConfig.REMEMBER_BUILD_BLOCKS.get();
            BlockPos base = site.offset(0, 0, 20);
            boolean early = false;
            boolean done = false;
            for (int i = 0; i < blocks; i++) {
                BlockState state = i % 4 == 0 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                boolean finished = RememberYou.tally(player, base.offset(i % 4, (i / 16) % 4, (i / 4) % 4), state);
                if (i < blocks - 1) {
                    early |= finished;
                } else {
                    done = finished;
                }
            }
            List<LifeMoment> builds = moments.of(QA_ID);
            checks[20] = !early && done && builds.size() == 1 && builds.get(0).kind() == LifeMomentKind.BUILD
                    && builds.get(0).itemId().equals("minecraft:cobblestone") && builds.get(0).pos().distSqr(base) < 16.0D
                    && RememberYou.tallied(player) == 0;

            Zombie plain = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            Zombie big = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            if (plain != null && big != null) {
                big.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CommonConfig.REMEMBER_BATTLE_MIN_HEALTH.get() + 10.0D);
                checks[21] = !RememberYou.notable(plain) && RememberYou.notable(big) && !RememberYou.notable(player);
            }

            LifeMoment twice = new LifeMoment(LifeMomentKind.DEATH, dimension, site, 0.0F, "", "", 0L, 0L, 2, 0, false);
            LifeMoment wither = new LifeMoment(LifeMomentKind.BATTLE, dimension, site, 0.0F, "", "minecraft:wither", 0L, 0L, 1, 0, false);
            LifeMoment house = new LifeMoment(LifeMomentKind.HOME, dimension, site, 0.0F, "", "", 0L, 0L, 1, 0, false);
            checks[22] = key(RememberYou.whisper(twice, false)).equals("mnemolith.remember.death_again")
                    && key(RememberYou.whisper(wither, false)).equals("mnemolith.remember.battle")
                    && key(RememberYou.whisper(house, true)).equals("mnemolith.remember.home_gone")
                    && RememberYou.whisper(house, false).getStyle().isItalic();

            // The real return path: the roll, the scene, the replay counted, the visit marked.
            moments.forget(QA_ID);
            RememberYou.forgetSession(QA_ID);
            com.mnemolith.recall.LivingMemory.log(player).markWow(-1_000_000L);
            player.setPos(at.getX() + 10.5D, at.getY(), at.getZ() + 0.5D);
            Optional<PastSelf> staged = Optional.empty();
            int tries = 0;
            while (staged.isEmpty() && tries++ < 40) {
                plant(moments, oldDeath(dimension, at, now, away, 0));
                staged = RememberYou.consider(player);
            }
            List<LifeMoment> counted = moments.of(QA_ID);
            checks[23] = staged.isPresent() && counted.size() == 1 && counted.get(0).replays() == 1 && counted.get(0).lastNear() == now;
            notes.add("consider tries=" + tries + " showing=" + com.mnemolith.recall.LivingMemory.showing(player) + " moments=" + counted);
            // A body whose chunk stopped ticking must not hold the owner's scene slot past its length.
            if (staged.isPresent()) {
                PastSelf stuck = staged.get();
                long late = now + stuck.sceneLength() + 41L;
                checks[24] = RememberYou.running(QA_ID, now) && !RememberYou.running(QA_ID, late) && stuck.isRemoved()
                        && !RememberYou.running(QA_ID);
            }
            staged.ifPresent(PastSelf::discard);
        } catch (RuntimeException ex) {
            Mnemolith.LOGGER.error("Mnemolith rememberqa failed", ex);
            notes.add("exception " + ex);
        } finally {
            if (body != null && !body.isRemoved()) {
                body.discard();
            }
            if (home != null && !home.isRemoved()) {
                home.discard();
            }
            for (BlockPos pos : placed) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            reset(player, moments);
            reset(other, moments);
            QaSupport.releaseColumn(level, ChunkPos.containing(site));
        }
        return new QaReport("rememberqa", NAMES, checks, notes).log();
    }

    private static void reset(FakePlayer player, LifeMoments moments) {
        moments.forget(player.getUUID());
        RememberYou.forgetSession(player.getUUID());
        player.setData(ModAttachments.GESTURE_LOG.get(), new GestureLog());
    }

    /** Replaces everything this player remembers with {@code moment}. */
    private static void plant(LifeMoments moments, LifeMoment moment) {
        moments.forget(QA_ID);
        moments.add(QA_ID, moment, 24);
    }

    private static LifeMoment oldDeath(String dimension, BlockPos at, long now, long away, int replays) {
        return new LifeMoment(LifeMomentKind.DEATH, dimension, at, 0.0F, "minecraft:iron_sword", "", now - away - 40L, now - away - 40L, 1, replays, false);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
    }

    /**
     * Shows one scene of {@code kind} a few blocks ahead of the player, right now (no rolls, no cooldowns). Nothing is
     * added to what the world remembers. A home scene uses the nearest bed within twelve blocks, or none.
     */
    public static PastSelf demonstrate(ServerPlayer player, LifeMomentKind kind) {
        if (!RememberYou.enabled() || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 look = player.getLookAngle();
        double flat = Math.max(1.0E-4D, Math.sqrt(look.x * look.x + look.z * look.z));
        BlockPos ahead = BlockPos.containing(player.getX() + look.x / flat * 6.0D, player.getY(), player.getZ() + look.z / flat * 6.0D);
        BlockPos pos = ahead;
        float yaw = player.getYRot() + 180.0F;
        String item = player.getMainHandItem().isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
        String detail = "";
        switch (kind) {
            case HOME -> {
                BlockPos bed = nearestBed(level, player.blockPosition(), 12);
                if (bed != null) {
                    pos = bed;
                }
                item = "";
            }
            case BUILD -> {
                if (item.isEmpty() || !(player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem)) {
                    item = "minecraft:oak_planks";
                }
            }
            case BATTLE -> {
                detail = "minecraft:wither";
                if (item.isEmpty()) {
                    item = "minecraft:iron_sword";
                }
            }
            default -> {
                if (item.isEmpty()) {
                    item = "minecraft:iron_sword";
                }
            }
        }
        long now = level.getGameTime();
        LifeMoment moment = new LifeMoment(kind, level.dimension().identifier().toString(), pos, yaw, item, detail, now - 24_000L, now - 24_000L, 1, 0, false);
        return RememberYou.stage(player, moment);
    }

    private static BlockPos nearestBed(ServerLevel level, BlockPos from, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-radius, -4, -radius), from.offset(radius, 4, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                double distance = pos.distSqr(from);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
