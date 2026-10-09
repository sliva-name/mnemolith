package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoRecorder;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.FarmLesson;
import com.mnemolith.echo.GuardLesson;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.StoredEcho;
import com.mnemolith.echo.graft.EchoGraft;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.echo.job.GuardController;
import com.mnemolith.echo.job.JobStatus;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.MemoryAvatar;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ModAttachments;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith guardqa}. Dedicated-server pass over the echo guard job: the lesson from a real recorder session,
 * the safety filter, weapon damage and graft multipliers, start refusals, a fight next to the post (durability spent,
 * bystanders untouched), the post leash, hitting back instead of fleeing, the grave taunt, volatile charges, and
 * persistence of the lesson in the job save and an echo-home snapshot.
 */
public final class GuardQa {
    private static final String[] NAMES = {"lesson", "filter", "damage", "refusals", "fight", "bystanders", "durability", "leash", "fightBack",
            "graveTaunt", "volatileSpends", "persistence"};
    private static final UUID OWNER = UUID.fromString("77777777-6666-6666-6666-666666666666");
    private static final int PAD = 12;
    private static int salt;

    private GuardQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        BlockPos a = column(level, (spawn.getX() >> 4) - 60 - salt * 3, (spawn.getZ() >> 4) + 52);
        tickColumn(level, a);
        flatten(level, a, PAD, 6);
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(OWNER, "GuardQaOwner"));
        owner.setGameMode(GameType.SURVIVAL);
        MemoryAvatar.STAND_INS.put(OWNER, owner);
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.getInventory().clearContent();
        owner.setHealth(owner.getMaxHealth());
        owner.snapTo(Vec3.atBottomCenterOf(a.offset(-6, 0, -6)), 0.0F, 0.0F);
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        List<Entity> extras = new ArrayList<>();
        try {
            // ---------- lesson: a real recorder session ----------
            Mob zombie0 = mob(level, EntityTypes.ZOMBIE, a.offset(8, 0, 8), extras);
            Mob cow0 = mob(level, EntityTypes.COW, a.offset(9, 0, 8), extras);
            EchoRecorder.start(owner);
            EchoRecorder.onGuardHit(owner, zombie0, false);
            EchoRecorder.onGuardHit(owner, cow0, false);
            EchoRecorder.onGuardHit(owner, zombie0, false);
            EchoRecorder.onGuardHit(owner, zombie0, true);
            for (int i = 0; i < 25; i++) {
                EchoRecorder.tick(owner);
            }
            ItemStack taught = EchoRecorder.finish(owner, "guardqa");
            GuardLesson lesson = taught == null ? GuardLesson.NONE : taught.getOrDefault(ModDataComponents.ECHO_GUARD.get(), GuardLesson.NONE);
            EchoRecorder.start(owner);
            EchoRecorder.onGuardHit(owner, zombie0, false);
            EchoRecorder.onGuardHit(owner, cow0, false);
            EchoRecorder.onGuardHit(owner, cow0, false);
            for (int i = 0; i < 25; i++) {
                EchoRecorder.tick(owner);
            }
            ItemStack once = EchoRecorder.finish(owner, "guardqa");
            boolean onceTeaches = once != null && once.has(ModDataComponents.ECHO_GUARD.get());
            ok[0] = lesson.teaches() && lesson.hits() == 2 && lesson.kills() == 1 && lesson.foes().equals(List.of(EntityTypes.ZOMBIE)) && !onceTeaches;
            notes.add("lesson hits=" + lesson.hits() + " kills=" + lesson.kills() + " foes=" + lesson.foes().size() + " oneHitTeaches=" + onceTeaches);
            owner.getInventory().clearContent();

            // ---------- the safety filter ----------
            EchoEntity echo = echo(level, owner, a, lesson);
            EchoEntity other = echo(level, owner, a.offset(-4, 0, 4), GuardLesson.NONE);
            Mob villager = mob(level, EntityTypes.VILLAGER, a.offset(1, 0, 2), extras);
            Mob cow = mob(level, EntityTypes.COW, a.offset(-1, 0, 2), extras);
            Wolf wolf = (Wolf) mob(level, EntityTypes.WOLF, a.offset(2, 0, -2), extras);
            wolf.tame(owner);
            Mob creeper = mob(level, EntityTypes.CREEPER, a.offset(-2, 0, -2), extras);
            Mob piglin = mob(level, EntityTypes.ZOMBIFIED_PIGLIN, a.offset(3, 0, 3), extras);
            Mob skeleton = mob(level, EntityTypes.SKELETON, a.offset(-3, 0, -3), extras);
            boolean calmPiglin = GuardController.canFight(echo, piglin);
            piglin.setTarget(echo);
            boolean angryPiglin = GuardController.canFight(echo, piglin);
            piglin.setTarget(null);
            ok[1] = GuardController.canFight(echo, zombie0) && GuardController.canFight(echo, skeleton) && !GuardController.canFight(echo, villager)
                    && !GuardController.canFight(echo, cow) && !GuardController.canFight(echo, wolf) && !GuardController.canFight(echo, creeper)
                    && !GuardController.canFight(echo, owner) && !GuardController.canFight(echo, other) && !calmPiglin && angryPiglin;
            notes.add("filter calmPiglin=" + calmPiglin + " angryPiglin=" + angryPiglin);
            skeleton.discard();
            creeper.discard();
            piglin.discard();
            zombie0.discard();
            cow0.discard();

            // ---------- damage and grafts ----------
            ItemStack iron = new ItemStack(Items.IRON_SWORD);
            ItemStack diamond = new ItemStack(Items.DIAMOND_SWORD);
            float plain = GuardController.damageFor(echo, iron, true);
            echo.setGraft(new EchoGraft(cast(ImprintTag.EXPLOSION, a), 10));
            float vol = GuardController.damageFor(echo, iron, false);
            echo.setGraft(new EchoGraft(cast(ImprintTag.SILENCE, a), 10));
            float hushUnaware = GuardController.damageFor(echo, iron, true);
            float hushAware = GuardController.damageFor(echo, iron, false);
            echo.setGraft(null);
            float dia = GuardController.damageFor(echo, diamond, true);
            boolean stick = GuardController.weaponDamage(new ItemStack(Items.STICK)) == 0.0D && GuardController.weaponDamage(new ItemStack(Items.DIRT)) == 0.0D;
            ok[2] = near(plain, 4.5F) && near(vol, 6.75F) && near(hushUnaware, 6.75F) && near(hushAware, 4.5F) && dia > plain && stick;
            notes.add("damage iron=" + plain + " volatile=" + vol + " hushedUnaware=" + hushUnaware + " hushedAware=" + hushAware + " diamond=" + dia);

            // ---------- refusals: no lesson, no weapon ----------
            boolean noLesson = !other.job().startGuarding(other) && other.job().status().kind() == JobStatus.Kind.NO_LESSON;
            echo.inventory().insert(new ItemStack(Items.STICK, 4));
            boolean noWeapon = !echo.job().startGuarding(echo) && echo.job().status().kind() == JobStatus.Kind.NO_TOOL
                    && echo.job().status().detail().equals("weapon");
            ok[3] = noLesson && noWeapon;
            notes.add("refusals noLesson=" + noLesson + " noWeapon=" + noWeapon);

            // ---------- a fight beside the post ----------
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            echo.inventory().insert(sword);
            boolean started = echo.job().startGuarding(echo);
            owner.snapTo(Vec3.atBottomCenterOf(a.offset(0, 0, -2)), 0.0F, 0.0F);
            Mob zombie = mob(level, EntityTypes.ZOMBIE, a.offset(2, 0, 0), extras);
            zombie.setNoAi(true);
            float villagerHp = villager.getHealth();
            float cowHp = cow.getHealth();
            float wolfHp = wolf.getHealth();
            float ownerHp = owner.getHealth();
            int ticks = 0;
            while (zombie.isAlive() && ticks < 400) {
                zombie.invulnerableTime = 0;
                echo.job().tick(level, echo);
                ticks++;
            }
            ItemStack held = weapon(echo);
            int used = held.isEmpty() ? -1 : held.getDamageValue();
            ok[4] = started && !zombie.isAlive() && echo.job().defeated() == 1 && echo.job().mode() == EchoJob.Mode.GUARD;
            ok[5] = villager.getHealth() == villagerHp && cow.getHealth() == cowHp && wolf.getHealth() == wolfHp && owner.getHealth() == ownerHp
                    && villager.isAlive() && cow.isAlive() && wolf.isAlive();
            ok[6] = used > 0 && used == echo.job().guardLanded();
            notes.add("fight started=" + started + " ticks=" + ticks + " dead=" + !zombie.isAlive() + " defeated=" + echo.job().defeated()
                    + " landed=" + echo.job().guardLanded() + " durabilityUsed=" + used + " status=" + echo.job().status().kind().getSerializedName());

            // ---------- the post leash ----------
            int radius = echo.job().guardRadius();
            Mob far = mob(level, EntityTypes.ZOMBIE, a.offset(radius + 5, 0, 0), extras);
            far.setNoAi(true);
            for (int i = 0; i < 40; i++) {
                echo.job().tick(level, echo);
            }
            boolean ignoresFar = echo.job().guardTarget() == null && far.getHealth() == far.getMaxHealth();
            echo.hurtServer(level, echo.damageSources().mobAttack(far), 1.0F);
            boolean refusesFar = echo.job().guardTarget() != far;
            far.discard();
            Mob inside = mob(level, EntityTypes.ZOMBIE, a.offset(radius - 2, 0, 0), extras);
            inside.setNoAi(true);
            for (int i = 0; i < 20 && echo.job().guardTarget() == null; i++) {
                echo.job().tick(level, echo);
            }
            boolean picksInside = echo.job().guardTarget() == inside;
            ok[7] = radius == 8 && ignoresFar && refusesFar && picksInside;
            notes.add("leash radius=" + radius + " ignoresFar=" + ignoresFar + " refusesFar=" + refusesFar + " picksInside=" + picksInside);
            inside.discard();

            // ---------- hits back instead of fleeing ----------
            for (int i = 0; i < 20; i++) {
                echo.job().tick(level, echo);
            }
            Mob biter = mob(level, EntityTypes.ZOMBIE, a.offset(1, 0, 1), extras);
            biter.setNoAi(true);
            echo.invulnerableTime = 0;
            echo.hurtServer(level, echo.damageSources().mobAttack(biter), 1.0F);
            ok[8] = !echo.job().alarmed() && echo.job().guardTarget() == biter && echo.job().mode() == EchoJob.Mode.GUARD;
            notes.add("fightBack alarmed=" + echo.job().alarmed() + " targetIsBiter=" + (echo.job().guardTarget() == biter));

            // ---------- grave pulls the mob off the player ----------
            echo.setGraft(new EchoGraft(cast(ImprintTag.DEATH, a), 10));
            biter.setTarget(villager);
            boolean chasing = biter.getTarget() == villager;
            for (int i = 0; i < 40 && biter.getTarget() != echo; i++) {
                biter.invulnerableTime = 0;
                echo.job().tick(level, echo);
            }
            ok[9] = chasing && biter.getTarget() == echo;
            notes.add("grave chasingVillager=" + chasing + " mobTarget=" + (biter.getTarget() == echo ? "echo" : biter.getTarget() == null ? "none" : "other") + " alive=" + biter.isAlive());
            biter.discard();

            // ---------- volatile spends a charge per landed hit ----------
            echo.setGraft(new EchoGraft(cast(ImprintTag.EXPLOSION, a), 10));
            Mob target = mob(level, EntityTypes.ZOMBIE, a.offset(1, 0, -1), extras);
            target.setNoAi(true);
            int before = echo.job().guardLanded();
            for (int i = 0; i < 60 && echo.job().guardLanded() < before + 2; i++) {
                target.invulnerableTime = 0;
                echo.job().tick(level, echo);
            }
            int hits = echo.job().guardLanded() - before;
            int charge = echo.graft() == null ? 0 : echo.graft().charge();
            ok[10] = hits >= 1 && charge == 10 - hits;
            notes.add("volatile hits=" + hits + " charge=" + charge);
            echo.setGraft(null);
            target.discard();

            // ---------- persistence ----------
            EchoJob.Saved saved = echo.job().save();
            var json = EchoJob.Saved.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
            EchoJob.Saved back = EchoJob.Saved.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            EchoJob copy = new EchoJob();
            copy.load(back);
            StoredEcho stored = StoredEcho.capture(echo);
            var storedJson = StoredEcho.CODEC.encodeStart(JsonOps.INSTANCE, stored).getOrThrow();
            StoredEcho storedBack = StoredEcho.CODEC.parse(JsonOps.INSTANCE, storedJson).getOrThrow();
            ok[11] = copy.mode() == EchoJob.Mode.GUARD && copy.guardLesson().equals(lesson) && copy.defeated() >= 1
                    && storedBack.guard().equals(lesson);
            notes.add("persistence mode=" + copy.mode().getSerializedName() + " defeated=" + copy.defeated() + " stored=" + storedBack.guard().hits());
        } catch (RuntimeException e) {
            notes.add("exception " + e);
            com.mnemolith.Mnemolith.LOGGER.error("guardqa failed", e);
        } finally {
            if (EchoPossession.isPossessing(owner)) {
                EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
            }
            for (Entity extra : extras) {
                if (!extra.isRemoved()) {
                    extra.discard();
                }
            }
            for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, new AABB(a).inflate(40.0D), e -> e.isOwnedBy(OWNER))) {
                echo.discardSilently();
            }
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(24.0D))) {
                drop.discard();
            }
            flatten(level, a, PAD, 6);
            releaseColumn(level, ChunkPos.containing(a));
            EchoRegistry.get(level.getServer()).forget(OWNER);
            owner.getInventory().clearContent();
            owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
            MemoryAvatar.STAND_INS.remove(OWNER);
        }
        return new QaReport("guardqa", NAMES, ok, notes).log();
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.01F;
    }

    private static ItemStack weapon(EchoEntity echo) {
        int slot = GuardController.bestWeapon(echo);
        return slot < 0 ? ItemStack.EMPTY : echo.inventory().getItem(slot);
    }

    private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, BlockPos at, List<Entity> extras) {
        Mob mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException("could not create " + type);
        }
        mob.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        extras.add(mob);
        return mob;
    }

    private static ImprintCast cast(ImprintTag tag, BlockPos pos) {
        return ImprintCast.from(com.mnemolith.data.ImprintSlips.of(tag, pos).get(ModDataComponents.IMPRINT_CAST.get()).toImprint());
    }

    private static @Nullable EchoEntity echo(ServerLevel level, FakePlayer owner, BlockPos at, GuardLesson guard) {
        Vec3 origin = Vec3.atBottomCenterOf(at);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        EchoRecording recording = new EchoRecording(OWNER, "GuardQaOwner", level.dimension(), origin, buffer.array(), List.of());
        EchoEntity echo = EchoLife.spawn(level, owner, recording, EchoLesson.NONE, FarmLesson.NONE, com.mnemolith.echo.LumberLesson.NONE,
                com.mnemolith.echo.CareLesson.NONE, guard);
        if (echo == null) {
            throw new IllegalStateException("no echo slot for the guard QA owner");
        }
        echo.stopReplay();
        echo.snapTo(origin, 0.0F, 0.0F);
        echo.setDeltaMovement(Vec3.ZERO);
        return echo;
    }

    private static void flatten(ServerLevel level, BlockPos center, int radius, int height) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.setBlock(center.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
                for (int dy = 0; dy <= height; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
    }
}
