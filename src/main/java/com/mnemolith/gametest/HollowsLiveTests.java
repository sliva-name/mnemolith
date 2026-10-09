package com.mnemolith.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.Hollows;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Memory flickers against real server players ({@link LivePlayers}), read off their in-memory connections: a player
 * standing in Memory Hollows is sent a flicker of the chunk's imprint, a friend nearby gets the same payload and a
 * player far off gets nothing; then the first player catches it with needle and lens through the real item use, gets
 * the slip, and both nearby players are told the flicker was caught.
 */
final class HollowsLiveTests {
    private HollowsLiveTests() {}

    static void flickerDeliveredAndCaught(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos site = LivePlayers.surface(level, (origin.getX() >> 4) - 40, (origin.getZ() >> 4) + 120);
        LivePlayers.force(level, site);
        BlockPos far = LivePlayers.surface(level, (site.getX() >> 4) + 9, site.getZ() >> 4);
        LivePlayers.force(level, far);
        CommandSourceStack console = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
        String fill = String.format("fillbiome %d %d %d %d %d %d mnemolith:memory_hollows", site.getX() - 24, site.getY() - 2, site.getZ() - 24,
                site.getX() + 24, site.getY() + 5, site.getZ() + 24);
        try {
            level.getServer().getCommands().getDispatcher().execute(fill, console);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            helper.fail("fillbiome failed: " + e.getMessage());
            return;
        }
        helper.assertTrue(Hollows.is(level.getBiome(site)), "fillbiome did not make the site Memory Hollows");
        // Every chunk a flicker spot can fall in remembers a path.
        ChunkPos center = ChunkPos.containing(site);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                var chunk = level.getChunk(center.x() + dx, center.z() + dz);
                LoadedChunkMemory.clear(chunk);
                ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
                memory.addImprint(new Imprint(ImprintTag.PATH, 3, site, Optional.empty(), 900 + dx * 7 + dz, level.getGameTime()), 8);
            }
        }
        ServerPlayer seer = LivePlayers.join(helper, "LiveFlickerSeer", Vec3.atBottomCenterOf(site));
        ServerPlayer friend = LivePlayers.join(helper, "LiveFlickerFriend", Vec3.atBottomCenterOf(site.offset(4, 0, 4)));
        ServerPlayer stranger = LivePlayers.join(helper, "LiveFlickerStranger", Vec3.atBottomCenterOf(far));
        HollowFlickers.clear();
        HollowFlickerPayload[] shown = new HollowFlickerPayload[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    drain(seer);
                    drain(friend);
                    drain(stranger);
                    boolean sent = false;
                    long now = level.getGameTime();
                    // Density 4 makes the roll certain; a spot can still miss (a pond, the biome edge), so try a few times.
                    for (int i = 0; i < 40 && !sent; i++) {
                        sent = HollowFlickers.offer(level, seer, 4.0D, now + i * 100L);
                    }
                    helper.assertTrue(sent, "no flicker was offered to a player standing in Memory Hollows");
                    List<HollowFlickerPayload> seen = flickers(seer);
                    helper.assertTrue(seen.size() == 1, "the player in the hollow got " + seen.size() + " flicker payloads, not 1; read " + LAST
                            + " open=" + channel(seer).isOpen() + " connected=" + seer.connection.getConnection().isConnected());
                    HollowFlickerPayload payload = seen.get(0);
                    double distance = Math.sqrt(payload.pos().distSqr(seer.blockPosition().atY(payload.pos().getY())));
                    helper.assertTrue(payload.tag() == ImprintTag.PATH.ordinal() && payload.scene() == HollowFlickers.WALK,
                            "the flicker is not the chunk's path imprint: tag=" + payload.tag() + " scene=" + payload.scene());
                    helper.assertTrue(distance >= HollowFlickers.MIN_DISTANCE - 1 && distance <= HollowFlickers.MAX_DISTANCE + 1,
                            "the flicker is " + distance + " blocks from the player");
                    List<HollowFlickerPayload> friendSaw = flickers(friend);
                    helper.assertTrue(friendSaw.size() == 1 && friendSaw.get(0).equals(payload), "the friend nearby did not get the same flicker: " + friendSaw);
                    helper.assertTrue(flickers(stranger).isEmpty(), "a player " + (int) Math.sqrt(far.distSqr(site)) + " blocks away was sent the flicker");
                    shown[0] = payload;
                    // Needle in the main hand, lens in the off hand, two blocks from where it stands.
                    BlockPos spot = payload.pos();
                    seer.teleportTo(level, spot.getX() + 2.5D, spot.getY(), spot.getZ() + 0.5D, java.util.Set.of(), 0.0F, 0.0F, true);
                    seer.getInventory().clearContent();
                    seer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EXTRACTION_NEEDLE.get()));
                    seer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.CHRONICLE_LENS.get()));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    drain(seer);
                    drain(friend);
                    ItemStack needle = seer.getMainHandItem();
                    seer.gameMode.useItem(seer, level, needle, InteractionHand.MAIN_HAND);
                    int slips = 0;
                    for (int i = 0; i < seer.getInventory().getContainerSize(); i++) {
                        ImprintCast cast = seer.getInventory().getItem(i).get(ModDataComponents.IMPRINT_CAST.get());
                        if (cast != null && cast.tag() == ImprintTag.PATH) {
                            slips += seer.getInventory().getItem(i).getCount();
                        }
                    }
                    helper.assertTrue(slips == 1, "catching the flicker gave " + slips + " path slips");
                    for (ServerPlayer watcher : List.of(seer, friend)) {
                        List<HollowFlickerPayload> told = flickers(watcher);
                        helper.assertTrue(told.size() == 1 && told.get(0).scene() == HollowFlickers.CAUGHT && told.get(0).pos().equals(shown[0].pos()),
                                watcher.getName().getString() + " was not told the flicker was caught: " + told);
                    }
                    helper.assertTrue(HollowFlickers.active().isEmpty(), "the caught flicker is still catchable");
                    var advancement = level.getServer().getAdvancements().get(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.mnemolith.Mnemolith.MOD_ID, "flicker_caught"));
                    helper.assertTrue(advancement != null && seer.getAdvancements().getOrStartProgress(advancement).isDone(),
                            "catching a flicker did not award flicker_caught");
                    HollowFlickers.clear();
                })
                .thenSucceed();
    }

    private static EmbeddedChannel channel(ServerPlayer player) {
        return (EmbeddedChannel) player.connection.getConnection().channel();
    }

    private static void drain(ServerPlayer player) {
        EmbeddedChannel channel = channel(player);
        channel.runPendingTasks();
        // The game writes packets during the tick and flushes at its end: flush to see this tick's.
        channel.flushOutbound();
        while (channel.readOutbound() != null) {
            // discard
        }
    }

    /** Flicker payloads sent to {@code player} since the last read, in order. */
    private static List<HollowFlickerPayload> flickers(ServerPlayer player) {
        EmbeddedChannel channel = channel(player);
        channel.runPendingTasks();
        // The game writes packets during the tick and flushes at its end: flush to see this tick's.
        channel.flushOutbound();
        List<HollowFlickerPayload> out = new ArrayList<>();
        LAST.clear();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            collect(message, out);
        }
        return out;
    }

    /** Classes of the last messages read, for a failure message. */
    private static final List<String> LAST = new ArrayList<>();

    private static void collect(Object message, List<HollowFlickerPayload> out) {
        if (LAST.size() < 12) {
            LAST.add(message.getClass().getSimpleName() + (message instanceof ClientboundCustomPayloadPacket c ? "(" + c.payload().type().id() + ")" : ""));
        }
        if (message instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof HollowFlickerPayload flicker) {
            out.add(flicker);
        } else if (message instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> inner : bundle.subPackets()) {
                collect(inner, out);
            }
        }
    }
}
