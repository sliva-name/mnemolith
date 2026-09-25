package com.mnemolith.echo;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Real world edits for a replaying echo. Every break, place, and use goes through a {@link FakePlayer} that carries the
 * owner's profile, so break/place/interact events and protection mods see the owner.
 * <p>
 * The fake player never keeps an item: the echo's own stack object is put in its hand for one call, then whatever is in
 * the hand is written back to the echo, and anything else the fake player picked up is moved to the echo or dropped.
 */
public final class EchoHands {
    /** Maximum distance from the echo's eyes to the block, so a desynced replay cannot edit far away. */
    private static final double REACH = 7.0D;
    private static @Nullable EchoEntity breaking;
    private static @Nullable FakePlayer breakingHand;
    /** Profile id of the last fake player seen by the break or place event. Read by {@code /mnemolith echoqa}. */
    public static volatile @Nullable UUID lastEventActor;

    private EchoHands() {}

    public enum Outcome {
        DONE,
        SKIPPED_CHANGED,
        SKIPPED_TOOL,
        SKIPPED_NO_ITEM,
        SKIPPED_FAR,
        REFUSED
    }

    public static FakePlayer hand(ServerLevel level, UUID owner, String name) {
        String profileName = name.isEmpty() ? "echo" : name;
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(owner, profileName));
        if (com.mnemolith.entity.echo.MemoryAvatar.STAND_INS.containsValue(player)) {
            // QA: the stand-in owner is itself a fake player with this profile; never borrow its inventory.
            player = FakePlayerFactory.get(level, new GameProfile(owner, profileName + "#hand"));
        }
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            player.setGameMode(GameType.SURVIVAL);
        }
        return player;
    }

    public static Outcome perform(ServerLevel level, EchoEntity echo, EchoAction action, EchoRecording.Frame frame) {
        UUID owner = echo.ownerId();
        if (owner == null) {
            return Outcome.REFUSED;
        }
        BlockPos pos = action.pos();
        if (echo.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > REACH || !level.isLoaded(pos)) {
            log(echo, action, Outcome.SKIPPED_FAR);
            return Outcome.SKIPPED_FAR;
        }
        FakePlayer hand = hand(level, owner, echo.ownerName());
        hand.snapTo(echo.getX(), echo.getY(), echo.getZ(), frame.yRot(), frame.xRot());
        hand.setYHeadRot(frame.headRot());
        hand.setShiftKeyDown(frame.has(EchoRecording.FLAG_SNEAK));
        sweep(level, echo, hand);
        Outcome outcome;
        try {
            outcome = switch (action.kind()) {
                case BREAK -> breakBlock(level, echo, hand, action);
                case PLACE -> place(level, echo, hand, action);
                case USE -> use(level, echo, hand, action);
            };
        } finally {
            sweep(level, echo, hand);
            breaking = null;
            breakingHand = null;
        }
        log(echo, action, outcome);
        return outcome;
    }

    private static Outcome breakBlock(ServerLevel level, EchoEntity echo, FakePlayer hand, EchoAction action) {
        BlockPos pos = action.pos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || action.block().isEmpty() || state.getBlock() != action.block().get()) {
            return Outcome.SKIPPED_CHANGED;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return Outcome.REFUSED;
        }
        int slot = echo.selectedSlot();
        ItemStack tool = echo.inventory().getItem(slot);
        if (!suits(tool, state)) {
            // The echo's own gear, not the recorder's: take the first fitting tool it carries into the hand.
            int better = findTool(echo.inventory(), state);
            if (better >= 0) {
                if (better < Inventory.getSelectionSize()) {
                    echo.setSelectedSlot(better);
                } else {
                    ItemStack moved = echo.inventory().removeItemNoUpdate(better);
                    echo.inventory().setItem(better, echo.inventory().removeItemNoUpdate(slot));
                    echo.inventory().setItem(slot, moved);
                }
                slot = echo.selectedSlot();
                tool = echo.inventory().getItem(slot);
            }
        }
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            return Outcome.SKIPPED_TOOL;
        }
        hand.getInventory().setSelectedSlot(slot);
        hand.getInventory().setItem(slot, tool);
        echo.inventory().setItem(slot, ItemStack.EMPTY);
        breaking = echo;
        breakingHand = hand;
        boolean broken;
        try {
            broken = hand.gameMode.destroyBlock(pos);
        } finally {
            ItemStack after = hand.getInventory().getItem(slot);
            hand.getInventory().setItem(slot, ItemStack.EMPTY);
            echo.inventory().setItem(slot, after);
        }
        echo.swing(InteractionHand.MAIN_HAND);
        return broken ? Outcome.DONE : Outcome.REFUSED;
    }

    private static boolean suits(ItemStack tool, BlockState state) {
        return state.requiresCorrectToolForDrops() ? tool.isCorrectToolForDrops(state) : tool.getDestroySpeed(state) > 1.0F;
    }

    private static int findTool(EchoInventory inventory, BlockState state) {
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && suits(stack, state)) {
                return i;
            }
        }
        return -1;
    }

    private static Outcome place(ServerLevel level, EchoEntity echo, FakePlayer hand, EchoAction action) {
        if (action.item().isEmpty()) {
            return Outcome.SKIPPED_NO_ITEM;
        }
        EchoInventory inventory = echo.inventory();
        int slot = inventory.getItem(echo.selectedSlot()).is(action.item().get()) ? echo.selectedSlot() : inventory.find(action.item().get());
        if (slot < 0) {
            return Outcome.SKIPPED_NO_ITEM;
        }
        if (slot < Inventory.getSelectionSize()) {
            echo.setSelectedSlot(slot);
        }
        int handSlot = 0;
        ItemStack stack = inventory.getItem(slot);
        hand.getInventory().setSelectedSlot(handSlot);
        hand.getInventory().setItem(handSlot, stack);
        inventory.setItem(slot, ItemStack.EMPTY);
        InteractionResult result;
        try {
            result = hand.gameMode.useItemOn(hand, level, stack, InteractionHand.MAIN_HAND, action.hit());
        } finally {
            ItemStack after = hand.getInventory().getItem(handSlot);
            hand.getInventory().setItem(handSlot, ItemStack.EMPTY);
            inventory.setItem(slot, after);
        }
        echo.swing(InteractionHand.MAIN_HAND);
        return result.consumesAction() ? Outcome.DONE : Outcome.REFUSED;
    }

    private static Outcome use(ServerLevel level, EchoEntity echo, FakePlayer hand, EchoAction action) {
        BlockState state = level.getBlockState(action.pos());
        if (action.block().isEmpty() || state.getBlock() != action.block().get()) {
            return Outcome.SKIPPED_CHANGED;
        }
        hand.setShiftKeyDown(false);
        InteractionResult result = hand.gameMode.useItemOn(hand, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, action.hit());
        echo.swing(InteractionHand.MAIN_HAND);
        return result.consumesAction() ? Outcome.DONE : Outcome.REFUSED;
    }

    /** Anything left in the fake player goes to the echo, or on the ground at the echo. Nothing stays behind. */
    private static void sweep(ServerLevel level, EchoEntity echo, FakePlayer hand) {
        Inventory inventory = hand.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                echo.inventory().insert(stack);
                if (!stack.isEmpty()) {
                    echo.spawnAtLocation(level, stack);
                }
            }
        }
        ItemStack carried = hand.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            hand.containerMenu.setCarried(ItemStack.EMPTY);
            echo.inventory().insert(carried);
            if (!carried.isEmpty()) {
                echo.spawnAtLocation(level, carried);
            }
        }
    }

    /** Blocks an echo breaks go into its own inventory first. Leftovers fall as usual. */
    public static void onBlockDrops(BlockDropsEvent event) {
        EchoEntity echo = breaking;
        if (echo == null || event.getBreaker() != breakingHand) {
            return;
        }
        var iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemEntity drop = iterator.next();
            ItemStack stack = drop.getItem();
            echo.inventory().insert(stack);
            if (stack.isEmpty()) {
                iterator.remove();
            } else {
                drop.setItem(stack);
            }
        }
    }

    private static void log(EchoEntity echo, EchoAction action, Outcome outcome) {
        Mnemolith.LOGGER.info("Mnemolith echo {} {} at {} -> {}", echo.ownerName(), action.kind().getSerializedName(), action.pos().toShortString(), outcome);
    }
}
