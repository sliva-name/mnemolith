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
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.graft.EchoGraft;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.MemoryAvatar;
import com.mnemolith.entity.mob.Archivist;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith graftqa}. Dedicated-server pass over memory grafts: grafting rules (take, feed, full, faint), the
 * hush over a neighbour's work, replacing a graft (the old one goes back into the chunk), kindled smelting (own and a
 * neighbour's ore), unpicking with the needle and a worn graft crumbling, the volatile burst on body death, a plunging
 * drop, a grave decoy drawing a husk, a fracture rejecting the graft, an archivist stealing it, possession carrying it
 * (effect, charge, return) and save/reload. Echoes and mobs are ticked through the level's own entity tick.
 */
public final class GraftQa {
    private static final int CHECKS = 12;
    private static final UUID OWNER = UUID.fromString("99999999-7777-7777-7777-777777777777");
    private static int salt;

    private GraftQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        salt++;
        BlockPos spawn = BlockPos.containing(source.getPosition());
        BlockPos a = column(level, (spawn.getX() >> 4) + 40 + salt * 4, (spawn.getZ() >> 4) - 30);
        BlockPos b = a.offset(16, 0, 0);
        for (BlockPos p : List.of(a, b)) {
            tickColumn(level, p);
            flatten(level, p, 7, 10);
            LoadedChunkMemory.clear(level.getChunkAt(p));
        }
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(OWNER, "GraftQaOwner"));
        MemoryAvatar.STAND_INS.put(OWNER, owner);
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.setData(ModAttachments.ECHO_PROGRESS.get(), EchoProgress.NONE);
        owner.getInventory().clearContent();
        owner.snapTo(Vec3.atBottomCenterOf(a.offset(-3, 0, -3)));
        List<String> notes = new ArrayList<>();
        boolean rules = false, hush = false, replace = false, kindled = false, unpick = false, burst = false;
        boolean plunge = false, decoy = false, fracture = false, archivist = false, possession = false, persistence = false;
        List<Entity> extras = new ArrayList<>();
        try {
            EchoEntity echo = EchoLife.spawn(level, owner, plainRecording(level, a), EchoLesson.NONE);
            EchoEntity near = EchoLife.spawn(level, owner, plainRecording(level, a.offset(3, 0, 0)), EchoLesson.NONE);
            if (echo == null || near == null) {
                notes.add("spawn failed echo=" + (echo != null) + " near=" + (near != null));
                return report(source, notes);
            }
            echo.stopReplay();
            near.stopReplay();
            place(echo, a);
            place(near, a.offset(3, 0, 0));

            // ---------- rules: take, feed, full, faint ----------
            int hushed = EchoGrafts.slipCharge(Temper.HUSHED);
            ItemStack silence = slip(ImprintTag.SILENCE, a, 3);
            boolean took = EchoGrafts.graft(owner, echo, silence);
            int afterTake = echo.graftCharge();
            boolean fed = EchoGrafts.graft(owner, echo, silence);
            int afterFeed = echo.graftCharge();
            boolean full = !EchoGrafts.graft(owner, echo, silence) && silence.getCount() == 1;
            ItemStack path = slip(ImprintTag.PATH, a, 1);
            boolean faint = !EchoGrafts.graft(owner, echo, path) && path.getCount() == 1 && echo.graftTemper() == Temper.HUSHED;
            rules = took && afterTake == hushed && fed && afterFeed == hushed * 2 && echo.graftCapacity() == hushed * 2 && full && faint
                    && echo.graftTemper() == Temper.HUSHED;
            notes.add("rules took=" + took + " charge " + afterTake + "->" + afterFeed + "/" + echo.graftCapacity() + " full=" + full + " faint=" + faint);

            // ---------- hush over a neighbour ----------
            ImprintTag own = EchoGrafts.workImprint(level, echo, echo.blockPosition());
            ImprintTag covered = EchoGrafts.workImprint(level, near, near.blockPosition());
            int hushLeft = echo.graftCharge();
            place(near, a.offset(0, 0, -7));
            place(echo, b);
            ImprintTag farTag = EchoGrafts.workImprint(level, near, near.blockPosition());
            place(echo, a);
            place(near, a.offset(3, 0, 0));
            hush = own == null && covered == null && hushLeft == hushed * 2 - 2 && farTag == ImprintTag.BUILD && EchoGrafts.unnoticed(echo)
                    && !EchoGrafts.unnoticed(near);
            notes.add("hush self=" + own + " neighbour=" + covered + " far=" + farTag + " charge=" + hushLeft);

            // ---------- replace: the old graft goes back into the chunk ----------
            LoadedChunkMemory.clear(level.getChunkAt(a));
            ItemStack fire = slip(ImprintTag.FIRE, a, 2);
            boolean replaced = EchoGrafts.graft(owner, echo, fire);
            replace = replaced && echo.graftTemper() == Temper.KINDLED && echo.graftCharge() == EchoGrafts.slipCharge(Temper.KINDLED)
                    && hasTag(level, a, ImprintTag.SILENCE) && fire.getCount() == 1;
            notes.add("replace ok=" + replaced + " temper=" + echo.graftTemper() + " charge=" + echo.graftCharge() + " silenceInChunk="
                    + hasTag(level, a, ImprintTag.SILENCE));

            // ---------- kindled: fireproof, smelts its own and a neighbour's ore ----------
            int kindledStart = echo.graftCharge();
            BlockPos oreA = a.offset(1, 0, 1);
            BlockPos oreB = a.offset(4, 0, 1);
            level.setBlock(oreA, Blocks.IRON_ORE.defaultBlockState(), 3);
            level.setBlock(oreB, Blocks.IRON_ORE.defaultBlockState(), 3);
            clearItems(echo);
            clearItems(near);
            echo.inventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            near.inventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            EchoHands.JobBreak ownBreak = EchoHands.breakForJob(level, echo, oreA, 0);
            EchoHands.JobBreak nearBreak = EchoHands.breakForJob(level, near, oreB, 0);
            int ingots = countItem(echo, Items.IRON_INGOT) + countItem(near, Items.IRON_INGOT);
            int raw = countItem(echo, Items.RAW_IRON) + countItem(near, Items.RAW_IRON);
            kindled = ownBreak.outcome() == EchoHands.Outcome.DONE && nearBreak.outcome() == EchoHands.Outcome.DONE && ingots == 2 && raw == 0
                    && echo.graftCharge() == kindledStart - 2 && echo.fireImmune() && !near.fireImmune() && EchoGrafts.lavaProof(echo)
                    && EchoGrafts.workImprint(level, echo, a) == ImprintTag.FIRE;
            notes.add("kindled ingots=" + ingots + " raw=" + raw + " charge " + kindledStart + "->" + echo.graftCharge() + " fireImmune=" + echo.fireImmune());

            // ---------- unpick with the needle; a worn graft crumbles ----------
            clearItems(echo);
            clearItems(near);
            owner.getInventory().clearContent();
            ItemStack needle = new ItemStack(ModItems.EXTRACTION_NEEDLE.get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, needle);
            // A fake player never ticks, so a needle cooldown from an earlier run would still be there.
            owner.getCooldowns().removeCooldown(owner.getCooldowns().getCooldownGroup(needle));
            boolean picked = EchoGrafts.unpick(owner, echo, needle);
            int fireSlips = slipsOf(owner, ImprintTag.FIRE);
            echo.setGraft(new EchoGraft(cast(ImprintTag.FIRE, a), 3));
            LoadedChunkMemory.clear(level.getChunkAt(a));
            String worn = EchoGrafts.release(level, echo, echo.blockPosition(), "qa");
            unpick = picked && fireSlips == 1 && echo.graft() == null && "spent".equals(worn) && !hasTag(level, a, ImprintTag.FIRE)
                    && dropsNear(level, echo.blockPosition()) == 0;
            notes.add("unpick returned=" + fireSlips + " worn=" + worn);
            owner.getInventory().clearContent();

            // ---------- volatile: fast, pays per block, bursts when the body dies ----------
            ItemStack blast = slip(ImprintTag.EXPLOSION, a, 1);
            EchoGrafts.graft(owner, near, blast);
            double factor = EchoGrafts.digFactor(near);
            int volatileStart = near.graftCharge();
            EchoGrafts.afterDig(near);
            int afterDig = near.graftCharge();
            Mob pig = spawnMob(level, net.minecraft.world.entity.EntityTypes.PIG, near.blockPosition().offset(2, 0, 0), extras);
            float pigBefore = pig == null ? 0.0F : pig.getHealth();
            BlockPos floor = near.blockPosition().below();
            near.kill(level);
            float pigAfter = pig == null ? 0.0F : pig.getHealth();
            burst = near.isRemoved() || !near.isAlive();
            burst = burst && factor < 1.0D && afterDig == volatileStart - 1 && pig != null && pigAfter < pigBefore
                    && level.getBlockState(floor).is(Blocks.STONE);
            notes.add("volatile factor=" + fmt(factor) + " charge " + volatileStart + "->" + afterDig + " pig " + fmt(pigBefore) + "->" + fmt(pigAfter)
                    + " floorKept=" + level.getBlockState(floor).is(Blocks.STONE));
            discard(pig);

            // The burst, the deaths and the released grafts all wrote into chunk A; a fracture there would reject the
            // next grafts on its own (that rule has its own check below), so start the next checks from a quiet chunk.
            LoadedChunkMemory.clear(level.getChunkAt(a));

            // ---------- plunging: a long drop without damage, one charge ----------
            EchoGrafts.graft(owner, echo, slip(ImprintTag.FALL, a, 1));
            int plungeStart = echo.graftCharge();
            float healthBefore = echo.getHealth();
            echo.snapTo(Vec3.atBottomCenterOf(a.offset(-3, 8, 3)), 0.0F, 0.0F);
            echo.setDeltaMovement(Vec3.ZERO);
            int fallTicks = 0;
            drive(level, echo, 5);
            while (fallTicks < 200 && !echo.onGround()) {
                level.tickNonPassenger(echo);
                fallTicks++;
            }
            plunge = echo.onGround() && EchoGrafts.maxDrop(echo) == EchoGrafts.PLUNGE_DROP && echo.getHealth() >= healthBefore - 0.01F
                    && echo.graftCharge() == plungeStart - 1;
            notes.add("plunge ticks=" + fallTicks + " health " + fmt(healthBefore) + "->" + fmt(echo.getHealth()) + " charge " + plungeStart + "->" + echo.graftCharge()
                    + " maxDrop=" + EchoGrafts.maxDrop(echo));

            // ---------- grave: a decoy that draws a husk and stands its ground ----------
            LoadedChunkMemory.clear(level.getChunkAt(a));
            EchoGrafts.graft(owner, echo, slip(ImprintTag.DEATH, a, 1));
            place(echo, a);
            owner.snapTo(Vec3.atBottomCenterOf(a.offset(0, 0, 40)));
            Mob husk = spawnMob(level, net.minecraft.world.entity.EntityTypes.HUSK, a.offset(5, 0, -3), extras);
            boolean targeted = false;
            int huntTicks = 0;
            for (; huntTicks < 400 && husk != null && !targeted; huntTicks++) {
                level.tickNonPassenger(husk);
                level.tickNonPassenger(echo);
                targeted = husk.getTarget() == echo;
            }
            int graveStart = echo.graftCharge();
            if (husk != null) {
                husk.snapTo(echo.position().add(1.2D, 0.0D, 0.0D), 90.0F, 0.0F);
                husk.doHurtTarget(level, echo);
            }
            decoy = targeted && echo.attractsMobs() && echo.graftCharge() == graveStart - 1 && echo.isAlive() && !echo.job().alarmed();
            notes.add("decoy targeted=" + targeted + "(" + huntTicks + "t) charge " + graveStart + "->" + echo.graftCharge() + " alarmed=" + echo.job().alarmed());
            discard(husk);
            owner.snapTo(Vec3.atBottomCenterOf(a.offset(-3, 0, -3)));

            // ---------- fracture: the chunk rejects the graft ----------
            place(echo, b);
            LoadedChunkMemory.clear(level.getChunkAt(b));
            int fractured = ImprintWriter.spike(level, b, 85);
            EchoGrafts.checkFracture(level, echo);
            boolean backInWorld = hasTag(level, b, ImprintTag.DEATH) || slipDropped(level, b, ImprintTag.DEATH);
            fracture = MemoryPressure.band(fractured) == PressureBand.FRACTURE && echo.graft() == null && backInWorld;
            notes.add("fracture pressure=" + fractured + " graftGone=" + (echo.graft() == null) + " backInWorld=" + backInWorld);
            QaSupport.discardReplicants(level, b);
            LoadedChunkMemory.clear(level.getChunkAt(b));
            clearDrops(level, b);
            place(echo, a);

            // ---------- archivist: the graft is stolen first and dropped on death ----------
            EchoGrafts.graft(owner, echo, slip(ImprintTag.DEATH, a, 1));
            echo.inventory().insert(new ItemStack(Items.COBBLESTONE, 20));
            Archivist thief = spawnMob(level, ModEntities.ARCHIVIST.get(), echo.blockPosition().offset(1, 0, 0), extras);
            boolean stole = thief != null && thief.stealFromEcho(level, echo);
            ImprintCast loot = thief == null ? null : thief.echoLoot().get(ModDataComponents.IMPRINT_CAST.get());
            boolean dropped = false;
            if (thief != null) {
                thief.kill(level);
                dropped = slipDropped(level, thief.blockPosition(), ImprintTag.DEATH);
            }
            archivist = stole && echo.graft() == null && loot != null && loot.tag() == ImprintTag.DEATH && countItem(echo, Items.COBBLESTONE) == 20 && dropped;
            notes.add("archivist stole=" + stole + " loot=" + (loot == null ? "-" : loot.tag().getSerializedName()) + " cobbleKept=" + countItem(echo, Items.COBBLESTONE)
                    + " droppedOnDeath=" + dropped);
            clearDrops(level, a);
            clearItems(echo);

            // ---------- possession: the body's temper on the player, then back ----------
            EchoGrafts.graft(owner, echo, slip(ImprintTag.EXPLOSION, a, 1));
            int carried = echo.graftCharge();
            owner.snapTo(Vec3.atBottomCenterOf(a.offset(-2, 0, -2)));
            EchoPossession.Result result = EchoPossession.possess(owner, echo);
            EchoGraft inBody = EchoGrafts.possessedGraft(owner);
            owner.tickCount = EchoGrafts.POSSESSED_SPEND_TICKS * 3;
            EchoGrafts.possessedTick(owner);
            MobEffectInstance haste = owner.getEffect(MobEffects.HASTE);
            EchoGraft paid = EchoGrafts.possessedGraft(owner);
            boolean back = EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
            EchoEntity returned = findEcho(level, a);
            possession = result == EchoPossession.Result.POSSESSED && inBody != null && inBody.temper() == Temper.VOLATILE && inBody.charge() == carried
                    && haste != null && haste.getAmplifier() == 1 && paid != null && paid.charge() == carried - 1 && back && returned != null
                    && returned.graftTemper() == Temper.VOLATILE && returned.graftCharge() == carried - 1 && owner.getEffect(MobEffects.HASTE) == null;
            notes.add("possession result=" + result + " inBody=" + (inBody == null ? "-" : inBody.temper() + "/" + inBody.charge()) + " haste="
                    + (haste == null ? "-" : haste.getAmplifier()) + " paid=" + (paid == null ? "-" : paid.charge()) + " returned="
                    + (returned == null ? "-" : returned.graftTemper() + "/" + returned.graftCharge()));

            // ---------- save / reload ----------
            if (returned != null) {
                int before = returned.graftCharge();
                EchoEntity reloaded = reload(level, returned);
                persistence = reloaded != null && reloaded.graftTemper() == Temper.VOLATILE && reloaded.graftCharge() == before
                        && reloaded.graft() != null && reloaded.graft().cast().tag() == ImprintTag.EXPLOSION;
                notes.add("persistence temper=" + (reloaded == null ? "-" : reloaded.graftTemper()) + " charge=" + (reloaded == null ? -1 : reloaded.graftCharge()));
            }
        } catch (RuntimeException e) {
            Mnemolith.LOGGER.error("Mnemolith graftqa failed", e);
            notes.add("exception " + e);
        } finally {
            for (Entity extra : extras) {
                discard(extra);
            }
            cleanup(level, owner, a);
            for (BlockPos p : List.of(a, b)) {
                QaSupport.discardReplicants(level, p);
                LoadedChunkMemory.clear(level.getChunkAt(p));
                releaseColumn(level, ChunkPos.containing(p));
            }
        }
        return report(source, notes, rules, hush, replace, kindled, unpick, burst, plunge, decoy, fracture, archivist, possession, persistence);
    }

    private static int report(CommandSourceStack source, List<String> notes, boolean... checks) {
        int passed = count(checks);
        String[] names = {"rules", "hush", "replace", "kindled", "unpick", "volatileBurst", "plunge", "graveDecoy", "fractureReject", "archivistSteal",
                "possession", "persistence"};
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            line.append(names[i]).append('=').append(i < checks.length && checks[i]).append(' ');
        }
        Mnemolith.LOGGER.info("Mnemolith graftqa {}", line.toString().trim());
        for (String note : notes) {
            Mnemolith.LOGGER.info("Mnemolith graftqa note {}", note);
            source.sendSuccess(() -> Component.literal(note), false);
        }
        String summary = line.toString().trim();
        source.sendSuccess(() -> Component.literal(summary), false);
        source.sendSuccess(() -> Component.translatable("mnemolith.command.graftqa", passed, CHECKS), true);
        return passed;
    }

    // ---------- helpers ----------

    private static ImprintCast cast(ImprintTag tag, BlockPos pos) {
        return ImprintCast.from(ImprintSlips.of(tag, pos).get(ModDataComponents.IMPRINT_CAST.get()).toImprint());
    }

    private static ItemStack slip(ImprintTag tag, BlockPos pos, int count) {
        return ImprintSlips.of(tag, pos).copyWithCount(count);
    }

    private static void place(EchoEntity echo, BlockPos at) {
        echo.snapTo(Vec3.atBottomCenterOf(at), 0.0F, 0.0F);
        echo.setDeltaMovement(Vec3.ZERO);
    }

    private static int slipsOf(FakePlayer player, ImprintTag tag) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
            if (cast != null && cast.tag() == tag) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean slipDropped(ServerLevel level, BlockPos pos, ImprintTag tag) {
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(10.0D))) {
            ImprintCast cast = drop.getItem().get(ModDataComponents.IMPRINT_CAST.get());
            if (cast != null && cast.tag() == tag) {
                return true;
            }
        }
        return false;
    }

    private static int dropsNear(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4.0D)).size();
    }

    private static void clearDrops(ServerLevel level, BlockPos pos) {
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(24.0D))) {
            drop.discard();
        }
    }

    private static int countItem(EchoEntity echo, Item item) {
        int total = 0;
        for (int i = 0; i < echo.inventory().getContainerSize(); i++) {
            ItemStack stack = echo.inventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void clearItems(EchoEntity echo) {
        for (int i = 0; i < echo.inventory().getContainerSize(); i++) {
            echo.inventory().removeItemNoUpdate(i);
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

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static EchoRecording plainRecording(ServerLevel level, BlockPos base) {
        Vec3 origin = Vec3.atBottomCenterOf(base);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        return new EchoRecording(OWNER, "GraftQaOwner", level.dimension(), origin, buffer.array(), List.of());
    }

    private static void drive(ServerLevel level, LivingEntity entity, int ticks) {
        for (int i = 0; i < ticks && entity.isAlive(); i++) {
            level.tickNonPassenger(entity);
        }
    }

    private static @Nullable EchoEntity findEcho(ServerLevel level, BlockPos base) {
        List<EchoEntity> found = level.getEntitiesOfClass(EchoEntity.class, new AABB(base).inflate(24.0D), e -> e.isOwnedBy(OWNER) && e.isAlive());
        return found.isEmpty() ? null : found.get(0);
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
        owner.removeAllEffects();
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.setData(ModAttachments.ECHO_PROGRESS.get(), EchoProgress.NONE);
        MemoryAvatar.STAND_INS.remove(OWNER);
    }
}
