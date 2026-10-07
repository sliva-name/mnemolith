package com.mnemolith.gametest;

import java.util.List;
import java.util.Optional;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModItems;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.job.EchoJob;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoShell;
import com.mnemolith.network.EchoCommandPayload;
import com.mnemolith.network.EchoJobPayload;
import com.mnemolith.network.EchoNetwork;
import com.mnemolith.network.EchoPossessPayload;
import com.mnemolith.network.PressureSync;
import com.mnemolith.network.RequestPressurePayload;

import io.netty.buffer.Unpooled;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hostile client packets. The client only asks; these send what a modified client could send (someone else's echo, a
 * radius past the server limit, an action id this build does not know, a chest or anchor far away or in unloaded land,
 * no lens raised, the wrong aim) and check that the server refuses and nothing changes.
 */
final class NetworkNegativeTests {
    private NetworkNegativeTests() {}

    private static final int LANE = 61;

    /** Echo job packets: strangers, out-of-range values, unknown actions, far and unloaded targets, a dead echo. */
    static void jobPayloads(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(0);
            ServerPlayer owner = support.player("NegJobOwner", at.offset(2, 0, 2));
            ServerPlayer stranger = support.player("NegJobStranger", at.offset(-2, 0, 2));
            EchoLesson build = new EchoLesson(20, 0, 1, 0, List.of(), Optional.of(new EchoLesson.Blueprint(Direction.SOUTH,
                    List.of(new EchoLesson.Entry(BlockPos.ZERO, Blocks.COBBLESTONE.defaultBlockState())))));
            EchoEntity echo = support.echo(owner, at, build);
            EchoJob job = echo.job();
            BlockPos chest = at.offset(3, 0, 0);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);

            // The owner may: link the chest, set a radius (the positive control for everything below).
            helper.assertTrue(EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.LINK_CHEST, chest, 0)),
                    "the owner could not link a chest beside them");
            helper.assertTrue(EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.RADIUS, BlockPos.ZERO, 8)) && job.radius() == 8,
                    "the owner could not set radius 8 (got " + job.radius() + ")");
            EchoJob.Mode mode = job.mode();

            // A stranger: every action refused, nothing changes.
            BlockPos otherChest = at.offset(-3, 0, 0);
            level.setBlock(otherChest, Blocks.CHEST.defaultBlockState(), 3);
            EchoJobPayload[] hostile = {
                    new EchoJobPayload(echo.getId(), EchoJobPayload.Action.RADIUS, BlockPos.ZERO, 2),
                    new EchoJobPayload(echo.getId(), EchoJobPayload.Action.LINK_CHEST, otherChest, 0),
                    EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.UNLINK_CHEST),
                    EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.MODE_MINE),
                    EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.MODE_BUILD),
                    EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.MODE_REPLAY),
                    EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.STOP),
                    new EchoJobPayload(echo.getId(), EchoJobPayload.Action.PLACE_BLUEPRINT, at.offset(-2, 0, -2), 1),
                    EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.CLEAR_BLUEPRINT),
            };
            for (EchoJobPayload payload : hostile) {
                helper.assertTrue(!EchoNetwork.applyJob(stranger, payload), "a stranger's " + payload.action() + " was accepted");
            }
            helper.assertTrue(job.radius() == 8 && chest.equals(job.chest()) && job.mode() == mode && job.buildAnchor() == null,
                    "a stranger's packets changed the job: radius=" + job.radius() + " chest=" + job.chest() + " mode=" + job.mode() + " anchor=" + job.buildAnchor());

            // Radius past the server limit, negative, and the extremes: clamped to [2, echoMineMaxRadius].
            int max = CommonConfig.ECHO_MINE_MAX_RADIUS.get();
            int[][] radii = {{1_000_000, max}, {max + 1, max}, {Integer.MAX_VALUE, max}, {-5, 2}, {0, 2}, {Integer.MIN_VALUE, 2}};
            for (int[] r : radii) {
                EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.RADIUS, BlockPos.ZERO, r[0]));
                helper.assertTrue(job.radius() == r[1], "radius " + r[0] + " became " + job.radius() + ", expected " + r[1]);
            }
            // A lower server limit is honoured too.
            int saved = max;
            try {
                CommonConfig.ECHO_MINE_MAX_RADIUS.set(6);
                EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.RADIUS, BlockPos.ZERO, 32));
                helper.assertTrue(job.radius() == 6, "radius 32 under a server limit of 6 became " + job.radius());
            } finally {
                CommonConfig.ECHO_MINE_MAX_RADIUS.set(saved);
            }
            EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.RADIUS, BlockPos.ZERO, 8));

            // Action ids this build does not know (a newer client, or garbage) decode as INVALID and are refused.
            for (int id : new int[] {EchoJobPayload.Action.values().length, 999, -1, Integer.MAX_VALUE}) {
                EchoJobPayload decoded = decodeJob(level, echo.getId(), id, 0);
                helper.assertTrue(decoded.action() == EchoJobPayload.Action.INVALID, "action id " + id + " decoded as " + decoded.action());
                helper.assertTrue(!EchoNetwork.applyJob(owner, decoded), "action id " + id + " was accepted");
            }
            helper.assertTrue(job.radius() == 8 && chest.equals(job.chest()) && job.mode() == mode, "an unknown action changed the job");

            // The id of something that is not an echo, and of nothing at all.
            Vec3 standAt = Vec3.atBottomCenterOf(at.offset(1, 0, -3));
            ArmorStand stand = new ArmorStand(level, standAt.x, standAt.y, standAt.z);
            level.addFreshEntity(stand);
            try {
                helper.assertTrue(!EchoNetwork.applyJob(owner, EchoJobPayload.simple(stand.getId(), EchoJobPayload.Action.STOP)), "a job packet for an armor stand was accepted");
                helper.assertTrue(!EchoNetwork.applyJob(owner, EchoJobPayload.simple(Integer.MAX_VALUE, EchoJobPayload.Action.STOP)), "a job packet for no entity was accepted");
                helper.assertTrue(!EchoNetwork.applyJob(owner, EchoJobPayload.simple(-1, EchoJobPayload.Action.STOP)), "a job packet for entity -1 was accepted");
            } finally {
                stand.discard();
            }

            // Linking: not a container, out of reach, far away in land that is not even loaded, too far from the echo.
            BlockPos stone = at.offset(2, -1, 0);
            helper.assertTrue(!EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.LINK_CHEST, stone, 0)), "stone was linked as a chest");
            BlockPos outOfReach = at.offset(-6, 0, -6);
            level.setBlock(outOfReach, Blocks.CHEST.defaultBlockState(), 3);
            helper.assertTrue(!EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.LINK_CHEST, outOfReach, 0)),
                    "a chest " + (int) Math.sqrt(owner.distanceToSqr(Vec3.atCenterOf(outOfReach))) + " blocks from the owner was linked");
            BlockPos faraway = new BlockPos(at.getX() + 1_000_000, at.getY(), at.getZ() + 1_000_000);
            helper.assertTrue(!EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.LINK_CHEST, faraway, 0)), "a chest a million blocks away was linked");
            helper.assertTrue(!level.hasChunk(faraway.getX() >> 4, faraway.getZ() >> 4), "a far link packet loaded the far chunk");
            helper.assertTrue(chest.equals(job.chest()), "a refused link changed the linked chest to " + job.chest());
            // The chest beside the owner, but 66 blocks from the echo (the owner stands 60 out, inside command range).
            BlockPos remote = at.offset(66, 0, 0);
            BlockState was = level.getBlockState(remote);
            level.setBlock(remote, Blocks.CHEST.defaultBlockState(), 2 | 16);
            try {
                owner.snapTo(Vec3.atBottomCenterOf(at.offset(60, 0, 0)));
                helper.assertTrue(!EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.LINK_CHEST, remote, 0)),
                        "a chest 66 blocks from the echo was linked");
            } finally {
                level.setBlock(remote, was, 2 | 16);
            }

            // The owner out of command range (70 blocks): even a radius change is refused.
            owner.snapTo(Vec3.atBottomCenterOf(at.offset(70, 0, 0)));
            helper.assertTrue(!EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.RADIUS, BlockPos.ZERO, 3)) && job.radius() == 8,
                    "the owner 70 blocks away changed the radius to " + job.radius());
            owner.snapTo(Vec3.atBottomCenterOf(at.offset(2, 0, 2)));

            // Blueprint anchors: 40 blocks from the owner, and a million blocks away.
            BlockPos farAnchor = at.offset(-40, 0, 0);
            helper.assertTrue(!EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.PLACE_BLUEPRINT, farAnchor, 0)), "an anchor 40 blocks away was placed");
            helper.assertTrue(!EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.PLACE_BLUEPRINT, faraway, 0)), "an anchor a million blocks away was placed");
            helper.assertTrue(job.buildAnchor() == null && job.mode() == mode, "a refused anchor was kept: " + job.buildAnchor() + " mode=" + job.mode());
            helper.assertTrue(!level.hasChunk(faraway.getX() >> 4, faraway.getZ() >> 4), "a far anchor packet loaded the far chunk");
            // Positive control: an anchor beside the owner is taken, so the refusals above were the distance check.
            BlockPos near = at.offset(-2, 0, -2);
            EchoNetwork.applyJob(owner, new EchoJobPayload(echo.getId(), EchoJobPayload.Action.PLACE_BLUEPRINT, near, 5));
            helper.assertTrue(near.equals(job.buildAnchor()), "an anchor beside the owner was not taken: " + job.buildAnchor());
            EchoNetwork.applyJob(owner, EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.CLEAR_BLUEPRINT));

            // A dead echo takes nothing.
            echo.discard();
            helper.assertTrue(!EchoNetwork.applyJob(owner, EchoJobPayload.simple(echo.getId(), EchoJobPayload.Action.STOP)), "a removed echo took a job packet");
            for (BlockPos pos : List.of(chest, otherChest, outOfReach)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            helper.succeed();
        }
    }

    /** Lens orders and possession: no lens, a stranger, the wrong aim, too far, a spectator, an unknown order. */
    static void lensOrdersAndPossession(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos at = support.pad(1);
            ServerPlayer owner = support.player("NegLensOwner", at.offset(3, 0, 0));
            ServerPlayer stranger = support.player("NegLensStranger", at.offset(-3, 0, 0));
            EchoEntity echo = support.echo(owner, at, EchoLesson.NONE);
            echo.inventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            stranger.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            EchoJob.Order before = echo.job().order();

            // Orders without the lens check (the QA path), then the network path that requires it.
            helper.assertTrue(!EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.NONE, false), "order NONE was accepted");
            helper.assertTrue(!EchoNetwork.applyCommand(stranger, echo.getId(), EchoJob.Order.FOLLOW, false), "a stranger's order was accepted");
            helper.assertTrue(!EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.FOLLOW, true), "an order without a raised lens was accepted");
            owner.snapTo(Vec3.atBottomCenterOf(at.offset(40, 0, 0)));
            helper.assertTrue(!EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.FOLLOW, false), "an order from 40 blocks away was accepted");
            helper.assertTrue(echo.job().order() == before, "refused orders changed the echo's order to " + echo.job().order());
            // Unknown order ids decode as NONE and change nothing through the real handler.
            for (int id : new int[] {EchoJob.Order.values().length, 77, -3}) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                buf.writeVarInt(echo.getId());
                buf.writeVarInt(id);
                EchoCommandPayload decoded = EchoCommandPayload.STREAM_CODEC.decode(buf);
                helper.assertTrue(decoded.order() == EchoJob.Order.NONE, "order id " + id + " decoded as " + decoded.order());
                EchoNetwork.handleCommand(decoded, NegativeSupport.context(owner));
            }
            helper.assertTrue(echo.job().order() == before, "an unknown order id changed the echo's order");
            // Positive control: the owner, close, may order.
            owner.snapTo(Vec3.atBottomCenterOf(at.offset(3, 0, 0)));
            helper.assertTrue(EchoNetwork.applyCommand(owner, echo.getId(), EchoJob.Order.STAY, false) && echo.job().order() == EchoJob.Order.STAY,
                    "the owner beside the echo could not order it to stay");

            // Possession by packet. No lens raised: nothing.
            aim(owner, echo);
            EchoNetwork.handlePossess(new EchoPossessPayload(echo.getId()), NegativeSupport.context(owner));
            helper.assertTrue(!EchoPossession.isPossessing(owner), "possessed without a raised lens");
            // A stranger with the lens raised, aiming at it: refused, their own items untouched.
            raiseLens(stranger);
            aim(stranger, echo);
            EchoNetwork.handlePossess(new EchoPossessPayload(echo.getId()), NegativeSupport.context(stranger));
            helper.assertTrue(!EchoPossession.isPossessing(stranger), "a stranger possessed someone else's echo");
            helper.assertTrue(stranger.getInventory().getItem(0).is(Items.DIAMOND) && stranger.getInventory().getItem(0).getCount() == 7,
                    "a refused possession touched the stranger's items");
            helper.assertTrue(EchoPossession.possess(stranger, echo) == EchoPossession.Result.NOT_YOURS, "possess(stranger) did not say NOT_YOURS");
            // The owner with the lens raised but looking the other way.
            raiseLens(owner);
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, owner.getEyePosition().add(owner.getEyePosition().subtract(echo.getBoundingBox().getCenter())));
            EchoNetwork.handlePossess(new EchoPossessPayload(echo.getId()), NegativeSupport.context(owner));
            helper.assertTrue(!EchoPossession.isPossessing(owner), "possessed while looking away from the echo");
            // Out of range (40 blocks; the limit is echoPossessRange + 2).
            owner.snapTo(Vec3.atBottomCenterOf(at.offset(CommonConfig.ECHO_POSSESS_RANGE.get() + 8, 0, 0)));
            raiseLens(owner);
            aim(owner, echo);
            EchoNetwork.handlePossess(new EchoPossessPayload(echo.getId()), NegativeSupport.context(owner));
            helper.assertTrue(!EchoPossession.isPossessing(owner), "possessed from beyond the possession range");
            helper.assertTrue(EchoPossession.possess(owner, echo) == EchoPossession.Result.FAR, "possess from far did not say FAR");
            // A spectator owner, close.
            owner.snapTo(Vec3.atBottomCenterOf(at.offset(3, 0, 0)));
            owner.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(EchoPossession.possess(owner, echo) == EchoPossession.Result.BUSY, "a spectator could possess");
            owner.setGameMode(GameType.SURVIVAL);
            int shells = level.getEntitiesOfClass(EchoShell.class, new AABB(at).inflate(48.0D)).size();
            helper.assertTrue(shells == 0, "refused possessions left " + shells + " shells");
            helper.assertTrue(echo.isAlive() && echo.inventory().getItem(0).is(Items.IRON_PICKAXE), "refused possessions touched the echo");

            // Positive control: the owner, close, lens raised, aiming: the same packet possesses.
            raiseLens(owner);
            aim(owner, echo);
            EchoNetwork.handlePossess(new EchoPossessPayload(echo.getId()), NegativeSupport.context(owner));
            boolean possessed = EchoPossession.isPossessing(owner);
            if (possessed) {
                EchoPossession.unpossess(owner, EchoPossession.Reason.KEY);
            }
            helper.assertTrue(possessed, "the owner could not possess with the lens raised and aimed (the test set-up is wrong)");
            for (EchoEntity woken : level.getEntitiesOfClass(EchoEntity.class, new AABB(at).inflate(16.0D), e -> e.isOwnedBy(owner))) {
                support.track(woken);
            }
            helper.succeed();
        }
    }

    /** A pressure request that asks for "ambient" without a lens gets the band-only answer unless the server allows more. */
    static void pressureRequest(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            BlockPos at = support.pad(2);
            ServerPlayer player = support.player("NegPressure", at);
            boolean allowed = CommonConfig.ALLOW_AMBIENT_PRESSURE.get();
            try {
                CommonConfig.ALLOW_AMBIENT_PRESSURE.set(false);
                PressureSync.forget(player.getUUID());
                PressureSync.handleRequest(new RequestPressurePayload(true), NegativeSupport.context(player));
                helper.assertTrue(PressureSync.lastAnswer(player.getUUID()).equals("bands"),
                        "an ambient request without a lens got " + PressureSync.lastAnswer(player.getUUID()));
                // Positive control: the lens in hand gets the full snapshot.
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CHRONICLE_LENS.get()));
                PressureSync.handleRequest(new RequestPressurePayload(false), NegativeSupport.context(player));
                helper.assertTrue(PressureSync.lastAnswer(player.getUUID()).equals("lens"), "a lens request got " + PressureSync.lastAnswer(player.getUUID()));
                // Back to no lens: the next ambient request is band-only again, whatever it asks for.
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                PressureSync.handleRequest(new RequestPressurePayload(true), NegativeSupport.context(player));
                helper.assertTrue(PressureSync.lastAnswer(player.getUUID()).equals("bands"),
                        "an ambient request after putting the lens away got " + PressureSync.lastAnswer(player.getUUID()));
            } finally {
                CommonConfig.ALLOW_AMBIENT_PRESSURE.set(allowed);
                PressureSync.forget(player.getUUID());
                player.getInventory().clearContent();
            }
            helper.succeed();
        }
    }

    private static EchoJobPayload decodeJob(ServerLevel level, int entityId, int actionId, int value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        buf.writeVarInt(entityId);
        buf.writeVarInt(actionId);
        BlockPos.STREAM_CODEC.encode(buf, BlockPos.ZERO);
        buf.writeVarInt(value);
        return EchoJobPayload.STREAM_CODEC.decode(buf);
    }

    private static void raiseLens(ServerPlayer player) {
        player.stopUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CHRONICLE_LENS.get()));
        player.startUsingItem(InteractionHand.MAIN_HAND);
    }

    private static void aim(ServerPlayer player, EchoEntity echo) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, echo.getBoundingBox().getCenter());
    }
}
