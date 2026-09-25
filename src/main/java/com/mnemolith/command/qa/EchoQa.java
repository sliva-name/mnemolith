package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.count;
import static com.mnemolith.command.qa.QaSupport.memory;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.menu.EchoMenu;
import com.mnemolith.echo.EchoAction;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.SlotStack;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;
import com.mnemolith.entity.echo.EchoShell;
import com.mnemolith.entity.echo.MemoryAvatar;
import com.mnemolith.event.EchoEvents;
import com.mnemolith.imprint.ModAttachments;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * {@code /mnemolith echoqa}. Dedicated-server pass over echoes: a synthetic recording is replayed through the
 * owner's fake player, items are given through the echo menu, and every possession exit path is checked for
 * item conservation (no losses, no duplicates). The owner is a fake player registered as a stand-in "online" owner
 * for the duration of the command.
 */
public final class EchoQa {
    private static final int CHECKS = 11;
    private static final UUID OWNER = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static int salt;

    private EchoQa() {}

    private record Site(BlockPos origin, BlockPos dirt, BlockPos stone, BlockPos floor, BlockPos placed, BlockPos lever) {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        salt++;
        BlockPos spawn = BlockPos.containing(source.getPosition());
        BlockPos base = column(level, (spawn.getX() >> 4) - 30 - salt * 4, spawn.getZ() >> 4);
        tickColumn(level, base);
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(OWNER, "EchoQaOwner"));
        MemoryAvatar.STAND_INS.put(OWNER, owner);
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        owner.getInventory().clearContent();
        owner.setHealth(owner.getMaxHealth());
        Site site = build(level, base);
        EchoRecording recording = recording(level, site);
        List<String> notes = new ArrayList<>();
        boolean spawned = false, empty = false, replay = false, give = false, gear = false, swap = false;
        boolean bodyDied = false, logout = false, recover = false, dimension = false, shellKilled = false;
        try {
            EchoEntity echo = EchoLife.spawn(level, owner, recording);
            spawned = echo != null && echo.isAlive() && echo.isReplaying() && EchoRegistry.get(level.getServer()).count(OWNER) == 1;
            notes.add("spawn=" + (echo == null ? "null" : echo.blockPosition().toShortString()));
            if (echo == null) {
                return report(source, notes, spawned);
            }
            empty = echo.inventory().isEmpty() && echo.inventory().totalCount() == 0;

            // 1) Replay with an empty inventory: dirt breaks by hand, stone needs a pickaxe, planks are missing, the lever flips.
            EchoHands.lastEventActor = null;
            boolean leverBefore = level.getBlockState(site.lever()).getValue(LeverBlock.POWERED);
            drive(level, echo);
            boolean dirtGone = level.getBlockState(site.dirt()).isAir();
            boolean dirtHeld = countItem(echo.inventory(), Items.DIRT.asItem()) == 1;
            boolean stoneKept = level.getBlockState(site.stone()).is(Blocks.STONE);
            boolean notPlaced = level.getBlockState(site.placed()).isAir();
            boolean leverFlipped = level.getBlockState(site.lever()).getValue(LeverBlock.POWERED) != leverBefore;
            boolean actor = OWNER.equals(EchoHands.lastEventActor);
            replay = dirtGone && dirtHeld && stoneKept && notPlaced && leverFlipped && actor && !echo.isReplaying();
            notes.add("replay dirtGone=" + dirtGone + " dirtHeld=" + dirtHeld + " stoneKept=" + stoneKept + " notPlaced=" + notPlaced
                    + " lever=" + leverFlipped + " fakeOwner=" + actor);

            // 2) Give items through the echo menu (shift-click from the owner's hotbar).
            owner.snapTo(Vec3.atBottomCenterOf(site.origin().offset(-2, 0, -2)));
            owner.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 16));
            owner.getInventory().setItem(1, new ItemStack(Items.IRON_PICKAXE));
            int before = playerCount(owner) + echo.inventory().totalCount();
            EchoMenu menu = new EchoMenu(0, owner.getInventory(), echo.inventory(), echo);
            int hotbarStart = EchoMenu.ECHO_SLOTS + 27;
            menu.quickMoveStack(owner, hotbarStart);
            menu.quickMoveStack(owner, hotbarStart + 1);
            boolean valid = menu.stillValid(owner);
            menu.removed(owner);
            int after = playerCount(owner) + echo.inventory().totalCount();
            give = valid && before == after && playerCount(owner) == 0 && countItem(echo.inventory(), Items.OAK_PLANKS) == 16
                    && countItem(echo.inventory(), Items.IRON_PICKAXE) == 1;
            notes.add("give before=" + before + " after=" + after + " echo=" + echo.inventory().totalCount() + " valid=" + valid);

            // 3) Replay again with gear: the pickaxe breaks the stone, one plank is consumed.
            level.setBlockAndUpdate(site.dirt(), Blocks.DIRT.defaultBlockState());
            echo.startReplay(recording);
            drive(level, echo);
            boolean stoneGone = level.getBlockState(site.stone()).isAir();
            boolean cobble = countItem(echo.inventory(), Items.COBBLESTONE) == 1;
            boolean placed = level.getBlockState(site.placed()).is(Blocks.OAK_PLANKS);
            boolean planksLeft = countItem(echo.inventory(), Items.OAK_PLANKS) == 15;
            gear = stoneGone && cobble && placed && planksLeft && countItem(echo.inventory(), Items.DIRT) == 2;
            notes.add("gear stoneGone=" + stoneGone + " cobble=" + cobble + " placed=" + placed + " planks=" + countItem(echo.inventory(), Items.OAK_PLANKS));

            // 4) Possess and return with an item count check on both sides.
            owner.snapTo(Vec3.atBottomCenterOf(site.origin().offset(-2, 0, -2)));
            owner.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 5));
            owner.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.TORCH, 7));
            owner.getInventory().setItem(39, new ItemStack(Items.IRON_HELMET));
            owner.setHealth(15.0F);
            owner.getFoodData().setFoodLevel(12);
            owner.setExperienceLevels(3);
            int real = playerCount(owner);
            int body = echo.inventory().totalCount();
            Vec3 home = owner.position();
            UUID oldEcho = echo.getUUID();
            EchoPossession.Result result = EchoPossession.possess(owner, echo);
            PossessionState.Data data = EchoPossession.state(owner).data();
            Entity shell = data == null ? null : level.getEntity(data.anchor().shell());
            boolean inBody = result == EchoPossession.Result.POSSESSED && echo.isRemoved() && shell instanceof EchoShell
                    && playerCount(owner) == body && data != null && SlotStack.count(data.real().items()) == real
                    && owner.position().distanceTo(echo.position()) < 0.5D;
            owner.getInventory().add(new ItemStack(Items.EMERALD, 3));
            boolean back = EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
            EchoEntity returned = findEcho(level, site);
            boolean realBack = playerCount(owner) == real && countItem(owner.getInventory(), Items.DIAMOND) == 5
                    && owner.getInventory().getItem(Inventory.SLOT_OFFHAND).is(Items.TORCH) && owner.getInventory().getItem(39).is(Items.IRON_HELMET)
                    && Math.abs(owner.getHealth() - 15.0F) < 0.01F && owner.getFoodData().getFoodLevel() == 12 && owner.experienceLevel == 3;
            boolean bodyBack = returned != null && !returned.getUUID().equals(oldEcho) && returned.inventory().totalCount() == body + 3
                    && EchoRegistry.get(level.getServer()).count(OWNER) == 1;
            boolean home2 = owner.position().distanceTo(home) < 0.5D && shell != null && shell.isRemoved();
            swap = inBody && back && realBack && bodyBack && home2 && !EchoPossession.isPossessing(owner);
            notes.add("swap result=" + result + " inBody=" + inBody + " realBack=" + realBack + " bodyBack=" + bodyBack + " home=" + home2
                    + " real=" + real + " body=" + body + " total=" + (playerCount(owner) + (returned == null ? -1 : returned.inventory().totalCount())));
            if (returned == null) {
                return report(source, notes, spawned, empty, replay, give, gear, swap);
            }

            // 5) The body dies while possessed: player back at the shell, body items on the ground, chunk pressure up.
            int instabilityBefore = memory(level, returned.blockPosition()).instability();
            int bodyItems = returned.inventory().totalCount();
            BlockPos deathAt = returned.blockPosition();
            clearGround(level, deathAt);
            EchoPossession.possess(owner, returned);
            CommonHooks.onLivingDeath(owner, level.damageSources().generic());
            int dropped = groundCount(level, deathAt);
            int instabilityAfter = memory(level, deathAt).instability();
            bodyDied = !EchoPossession.isPossessing(owner) && playerCount(owner) == real && dropped == bodyItems
                    && instabilityAfter > instabilityBefore && EchoRegistry.get(level.getServer()).count(OWNER) == 0
                    && owner.position().distanceTo(home) < 0.5D;
            notes.add("bodyDied dropped=" + dropped + "/" + bodyItems + " instability " + instabilityBefore + "->" + instabilityAfter);
            clearGround(level, deathAt);

            // 6) Disconnect while possessed: the logout hook swaps back before the player file would be saved.
            EchoEntity second = EchoLife.spawn(level, owner, recording);
            if (second != null) {
                second.stopReplay();
                second.inventory().setItem(0, new ItemStack(Items.BREAD, 4));
                second.inventory().setItem(EchoInventory.OFFHAND, new ItemStack(Items.SHIELD));
                EchoPossession.possess(owner, second);
                EchoEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(owner));
                EchoEntity third = findEcho(level, site);
                logout = !EchoPossession.isPossessing(owner) && playerCount(owner) == real && third != null && third.inventory().totalCount() == 5;
                notes.add("logout echoItems=" + (third == null ? -1 : third.inventory().totalCount()) + " real=" + playerCount(owner));

                // 7) Crash while possessed: the attachment round-trips through its codec, and the join hook swaps back.
                if (third != null) {
                    EchoPossession.possess(owner, third);
                    PossessionState saved = EchoPossession.state(owner);
                    RegistryOps<com.google.gson.JsonElement> ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                    var encoded = PossessionState.MAP_CODEC.codec().encodeStart(ops, saved).getOrThrow();
                    PossessionState loaded = PossessionState.MAP_CODEC.codec().parse(ops, encoded).getOrThrow();
                    owner.setData(ModAttachments.ECHO_POSSESSION.get(), loaded);
                    EchoEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(owner));
                    EchoEntity fourth = findEcho(level, site);
                    recover = loaded.isActive() && !EchoPossession.isPossessing(owner) && playerCount(owner) == real
                            && fourth != null && fourth.inventory().totalCount() == 5;
                    notes.add("recover codec=" + loaded.isActive() + " echoItems=" + (fourth == null ? -1 : fourth.inventory().totalCount()));

                    // 8) Dimension change while possessed: the travel is cancelled and the player swaps back.
                    if (fourth != null) {
                        EchoPossession.possess(owner, fourth);
                        EntityTravelToDimensionEvent travel = new EntityTravelToDimensionEvent(owner, Level.NETHER);
                        EchoEvents.onTravel(travel);
                        EchoEntity fifth = findEcho(level, site);
                        dimension = travel.isCanceled() && !EchoPossession.isPossessing(owner) && playerCount(owner) == real
                                && fifth != null && fifth.inventory().totalCount() == 5;
                        notes.add("dimension cancelled=" + travel.isCanceled() + " echoItems=" + (fifth == null ? -1 : fifth.inventory().totalCount()));

                        // 9) The shell is killed: the player is pulled back first (and would die at the shell).
                        if (fifth != null) {
                            EchoPossession.possess(owner, fifth);
                            PossessionState.Data shellData = EchoPossession.state(owner).data();
                            Entity target = shellData == null ? null : level.getEntity(shellData.anchor().shell());
                            if (target instanceof EchoShell victim) {
                                victim.hurtServer(level, level.damageSources().generic(), 4.0F);
                                float stored = EchoPossession.state(owner).data() == null ? -1.0F : EchoPossession.state(owner).data().real().health();
                                victim.hurtServer(level, level.damageSources().generic(), 1000.0F);
                                EchoEntity sixth = findEcho(level, site);
                                shellKilled = Math.abs(stored - 11.0F) < 0.01F && !EchoPossession.isPossessing(owner) && victim.isRemoved()
                                        && sixth != null && sixth.inventory().totalCount() == 5 && countItem(owner.getInventory(), Items.DIAMOND) == 5;
                                notes.add("shellKilled storedHealth=" + stored + " echoItems=" + (sixth == null ? -1 : sixth.inventory().totalCount()));
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            Mnemolith.LOGGER.error("Mnemolith echoqa failed", e);
            notes.add("exception " + e);
        } finally {
            cleanup(level, owner, site);
            releaseColumn(level, new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4));
        }
        return report(source, notes, spawned, empty, replay, give, gear, swap, bodyDied, logout, recover, dimension, shellKilled);
    }

    private static int report(CommandSourceStack source, List<String> notes, boolean... checks) {
        int passed = count(checks);
        String[] names = {"spawn", "emptyInventory", "replayFakePlayer", "giveItems", "replayWithGear", "possessSwap", "bodyDied", "logout", "crashRecover", "dimension", "shellKilled"};
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            line.append(names[i]).append('=').append(i < checks.length && checks[i]).append(' ');
        }
        Mnemolith.LOGGER.info("Mnemolith echoqa {}", line.toString().trim());
        for (String note : notes) {
            Mnemolith.LOGGER.info("Mnemolith echoqa note {}", note);
        }
        String summary = line.toString().trim();
        source.sendSuccess(() -> Component.literal(summary), false);
        source.sendSuccess(() -> Component.translatable("mnemolith.command.echoqa", passed, CHECKS), true);
        return passed;
    }

    private static Site build(ServerLevel level, BlockPos base) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 5; dz++) {
                level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos dirt = base.offset(1, 0, 1);
        BlockPos stone = base.offset(-1, 0, 1);
        BlockPos floor = base.offset(0, -1, 2);
        BlockPos lever = base.offset(2, 0, -1);
        level.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        return new Site(base, dirt, stone, floor, floor.above(), lever);
    }

    /** Two seconds walking 1.5 blocks along +x, looking down; break dirt, break stone, place planks, flip the lever. */
    private static EchoRecording recording(ServerLevel level, Site site) {
        Vec3 origin = Vec3.atBottomCenterOf(site.origin());
        int frames = 40;
        ByteBuffer buffer = ByteBuffer.allocate(frames * EchoRecording.FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            int flags = EchoRecording.FLAG_GROUND | (i == 5 || i == 7 || i == 10 || i == 15 ? EchoRecording.FLAG_SWING : 0);
            EchoRecording.writeFrame(buffer, origin, origin.x + i * 1.5D / frames, origin.y, origin.z, -90.0F, 45.0F, -90.0F, flags);
        }
        List<EchoAction> actions = List.of(
                EchoAction.of(5, EchoAction.Kind.BREAK, hit(site.dirt(), Direction.UP), false, Blocks.DIRT, null),
                EchoAction.of(7, EchoAction.Kind.BREAK, hit(site.stone(), Direction.UP), false, Blocks.STONE, null),
                EchoAction.of(10, EchoAction.Kind.PLACE, hit(site.floor(), Direction.UP), false, Blocks.STONE, Items.OAK_PLANKS),
                EchoAction.of(15, EchoAction.Kind.USE, hit(site.lever(), Direction.UP), false, Blocks.LEVER, null));
        return new EchoRecording(OWNER, "EchoQaOwner", level.dimension(), origin, buffer.array(), actions);
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 location = face == Direction.UP ? new Vec3(center.x, pos.getY() + 1.0D, center.z) : center;
        return new BlockHitResult(location, face, pos, false);
    }

    private static void drive(ServerLevel level, EchoEntity echo) {
        int guard = 0;
        while (echo.isReplaying() && guard++ < EchoRecording.MAX_FRAMES + 5) {
            echo.stepReplay(level);
        }
    }

    private static @Nullable EchoEntity findEcho(ServerLevel level, Site site) {
        for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, new AABB(site.origin()).inflate(24.0D), e -> e.isAlive() && e.isOwnedBy(OWNER))) {
            return echo;
        }
        return null;
    }

    private static int countItem(net.minecraft.world.Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int playerCount(FakePlayer player) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            total += inventory.getItem(i).getCount();
        }
        return total;
    }

    private static int groundCount(ServerLevel level, BlockPos pos) {
        int total = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0D))) {
            total += item.getItem().getCount();
        }
        return total;
    }

    private static void clearGround(ServerLevel level, BlockPos pos) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0D))) {
            item.discard();
        }
    }

    private static void cleanup(ServerLevel level, FakePlayer owner, @Nullable Site site) {
        if (EchoPossession.isPossessing(owner)) {
            EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
        }
        if (site != null) {
            for (EchoEntity echo : level.getEntitiesOfClass(EchoEntity.class, new AABB(site.origin()).inflate(24.0D), e -> e.isOwnedBy(OWNER))) {
                echo.discardSilently();
            }
            for (EchoShell shell : level.getEntitiesOfClass(EchoShell.class, new AABB(site.origin()).inflate(24.0D))) {
                shell.discard();
            }
            clearGround(level, site.origin());
        }
        EchoRegistry.get(level.getServer()).forget(OWNER);
        owner.getInventory().clearContent();
        owner.setHealth(owner.getMaxHealth());
        owner.setData(ModAttachments.ECHO_POSSESSION.get(), new PossessionState());
        MemoryAvatar.STAND_INS.remove(OWNER);
    }
}
