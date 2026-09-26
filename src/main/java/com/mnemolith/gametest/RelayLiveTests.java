package com.mnemolith.gametest;

import java.util.List;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.ArchiveVaultBlock;
import com.mnemolith.content.block.ArchiveVaultBlockEntity;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoPossession;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.PossessionState;
import com.mnemolith.echo.relay.EchoRelays;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The echo relay and the archive vault against real server players ({@link LivePlayers}). The thread is tied through
 * {@code Player.interactOn}, the hop through the return-key handler with the player sneaking, the mirrored break
 * through {@code ServerPlayerGameMode.destroyBlock} (the real break event, mirrored on the next server tick), and the
 * vault is placed, switched on, pricked with the needle and discharged through {@code ServerPlayerGameMode.useItemOn};
 * its draws run on the block entity's own tick. Echoes spawned here are scenery.
 */
final class RelayLiveTests {
    private RelayLiveTests() {}

    /** Relay sites: 6 chunks apart, south of everything else. */
    private static BlockPos site(GameTestHelper helper, int index) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos pos = LivePlayers.surface(level, (origin.getX() >> 4) + 6 * index, (origin.getZ() >> 4) + 50);
        LivePlayers.force(level, pos);
        ChunkPos center = ChunkPos.containing(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LoadedChunkMemory.clear(level.getChunk(center.x() + dx, center.z() + dz));
            }
        }
        // A flat stone pad with open air above.
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                level.setBlock(pos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
                for (int dy = 0; dy <= 6; dy++) {
                    level.setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
        return pos;
    }

    private static EchoEntity echo(GameTestHelper helper, ServerPlayer owner, BlockPos at) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = Vec3.atBottomCenterOf(at);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        EchoRecording recording = new EchoRecording(owner.getUUID(), owner.getGameProfile().name(), level.dimension(), origin, buffer.array(), List.of());
        EchoEntity echo = EchoLife.spawn(level, owner, recording, EchoLesson.NONE);
        if (echo == null) {
            throw helper.assertionException(Component.literal("could not spawn an echo at " + at.toShortString()));
        }
        echo.stopReplay();
        echo.snapTo(origin, 0.0F, 0.0F);
        return echo;
    }

    /**
     * The player ties two of their echoes with a relay thread (two right-clicks), possesses one, and sneaks and
     * presses the return key: they are in the other end, and the body they left stands back up as a linked echo.
     */
    static void threadLinksThenHop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, 0);
        ServerPlayer player = LivePlayers.join(helper, "LiveRelayHopper", Vec3.atBottomCenterOf(site));
        player.getAbilities().invulnerable = true;
        EchoEntity a = echo(helper, player, site.offset(-4, 0, 0));
        EchoEntity b = echo(helper, player, site.offset(6, 0, 0));
        ItemStack thread = new ItemStack(ModItems.RELAY_THREAD.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, thread);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    InteractionResult first = player.interactOn(a, InteractionHand.MAIN_HAND, a.position());
                    InteractionResult second = player.interactOn(b, InteractionHand.MAIN_HAND, b.position());
                    helper.assertTrue(a.relay() != null && a.relay().equals(b.relay()), "not linked: " + first + "/" + second + " relays " + a.relay() + " " + b.relay());
                    helper.assertTrue(player.getMainHandItem().getCount() == 2, "the thread was not used: " + player.getMainHandItem().getCount());
                    EchoPossession.Result possessed = EchoPossession.possess(player, a);
                    helper.assertTrue(possessed == EchoPossession.Result.POSSESSED, "could not possess: " + possessed);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    player.setShiftKeyDown(true);
                    EchoRelays.returnKey(player);
                    player.setShiftKeyDown(false);
                    PossessionState.Data data = EchoPossession.state(player).data();
                    helper.assertTrue(data != null && data.body().echo().equals(b.getUUID()), "not in the other end: " + (data == null ? "not possessing" : data.body().echo()));
                    helper.assertTrue(player.distanceToSqr(Vec3.atBottomCenterOf(site.offset(6, 0, 0))) < 4.0D, "the player is at " + player.blockPosition().toShortString());
                    EchoEntity left = EchoRelays.partnerOfPossessed(player);
                    helper.assertTrue(left != null && left.distanceToSqr(Vec3.atBottomCenterOf(site.offset(-4, 0, 0))) < 4.0D,
                            "the left body is " + (left == null ? "missing" : "at " + left.blockPosition().toShortString()));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    // Without sneaking the key returns the player to their own body.
                    EchoRelays.returnKey(player);
                    helper.assertTrue(!EchoPossession.isPossessing(player), "the return key did not return the player");
                })
                .thenSucceed();
    }

    /** Possessing one end, the player breaks a block beside them: on the next tick the other end breaks the same spot beside it. */
    static void mirrorBreak(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, 1);
        ServerPlayer player = LivePlayers.join(helper, "LiveRelayMirror", Vec3.atBottomCenterOf(site));
        player.getAbilities().invulnerable = true;
        EchoEntity a = echo(helper, player, site.offset(-5, 0, 0));
        EchoEntity b = echo(helper, player, site.offset(5, 0, 0));
        EchoRelays.link(level, a, b);
        BlockPos[] targets = new BlockPos[2];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    EchoPossession.Result possessed = EchoPossession.possess(player, a);
                    helper.assertTrue(possessed == EchoPossession.Result.POSSESSED, "could not possess: " + possessed);
                    b.snapTo(Vec3.atBottomCenterOf(site.offset(5, 0, 0)), 0.0F, 0.0F);
                    targets[0] = player.blockPosition().offset(0, 0, 2);
                    targets[1] = b.blockPosition().offset(0, 0, 2);
                    level.setBlock(targets[0], Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(targets[1], Blocks.STONE.defaultBlockState(), 3);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                    boolean broke = player.gameMode.destroyBlock(targets[0]);
                    helper.assertTrue(broke && level.getBlockState(targets[0]).isAir(), "the player's break did not happen");
                })
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(targets[1]).isAir(),
                        "the other end did not mirror the break: " + EchoRelays.lastMirror() + " at " + targets[1].toShortString()))
                .thenExecute(() -> EchoPossession.unpossess(player, EchoPossession.Reason.KEY))
                .thenSucceed();
    }

    /**
     * The player places a vault, switches it on with an empty hand, and it draws the area's imprints on its own tick;
     * the needle then takes the loudest out as a slip, and a sneaking empty-hand click lets the rest go into the chunk.
     */
    static void vaultDrawsThenSpends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, 2);
        ServerPlayer player = LivePlayers.join(helper, "LiveVaultKeeper", Vec3.atBottomCenterOf(site.offset(-2, 0, 0)));
        player.getAbilities().invulnerable = true;
        ImprintWriter.write(level, site.offset(5, 0, 5), List.of(ImprintTag.DEATH), null, false);
        ImprintWriter.write(level, site.offset(-6, 0, 4), List.of(ImprintTag.FIRE), null, false);
        BlockPos ground = site.below();
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false);
        BlockHitResult side = new BlockHitResult(Vec3.atCenterOf(site).add(-0.5D, 0.0D, 0.0D), Direction.WEST, site, false);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    ItemStack vault = new ItemStack(ModItems.ARCHIVE_VAULT.get());
                    player.setItemInHand(InteractionHand.MAIN_HAND, vault);
                    InteractionResult placed = player.gameMode.useItemOn(player, level, vault, InteractionHand.MAIN_HAND, top);
                    helper.assertTrue(placed.consumesAction() && level.getBlockState(site).is(ModBlocks.ARCHIVE_VAULT.get()), "the vault was not placed: " + placed);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    InteractionResult toggled = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, side);
                    helper.assertTrue(level.getBlockState(site).getValue(ArchiveVaultBlock.DRAWING), "the empty-hand click did not switch it on: " + toggled);
                })
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockEntity(site) instanceof ArchiveVaultBlockEntity vault && vault.count() >= 2,
                        "drew " + (level.getBlockEntity(site) instanceof ArchiveVaultBlockEntity vault ? vault.count() : -1) + " so far"))
                .thenExecute(() -> {
                    ArchiveVaultBlockEntity vault = (ArchiveVaultBlockEntity) level.getBlockEntity(site);
                    helper.assertTrue(!com.mnemolith.command.qa.QaSupport.hasTag(level, site.offset(5, 0, 5), ImprintTag.DEATH), "the death imprint is still in its chunk");
                    // Stop drawing before spending, so a draw between the steps cannot take back what is let go.
                    player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, side);
                    helper.assertTrue(!level.getBlockState(site).getValue(ArchiveVaultBlock.DRAWING), "the second click did not switch it off");
                    ItemStack needle = new ItemStack(ModItems.EXTRACTION_NEEDLE.get());
                    player.setItemInHand(InteractionHand.MAIN_HAND, needle);
                    InteractionResult pricked = player.gameMode.useItemOn(player, level, needle, InteractionHand.MAIN_HAND, side);
                    boolean slip = player.getInventory().getNonEquipmentItems().stream().anyMatch(s -> {
                        com.mnemolith.data.ImprintCast cast = s.get(com.mnemolith.data.ModDataComponents.IMPRINT_CAST.get());
                        return cast != null && cast.tag() == ImprintTag.DEATH;
                    });
                    helper.assertTrue(pricked.consumesAction() && slip && vault.count() == 1, "the needle took " + pricked + " slip=" + slip + " left " + vault.count());
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setShiftKeyDown(true);
                    player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, side);
                    player.setShiftKeyDown(false);
                    helper.assertTrue(vault.isEmpty() && com.mnemolith.command.qa.QaSupport.hasTag(level, site.above(), ImprintTag.FIRE),
                            "the discharge left " + vault.count());
                })
                .thenSucceed();
    }
}
