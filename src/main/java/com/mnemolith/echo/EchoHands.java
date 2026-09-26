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
import net.minecraft.world.phys.BlockHitResult;
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
    private static final java.util.List<ItemStack> CAPTURED = new java.util.ArrayList<>();

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
        boolean broken = destroyWith(level, echo, hand, pos, slot);
        echo.swing(InteractionHand.MAIN_HAND);
        return broken ? Outcome.DONE : Outcome.REFUSED;
    }

    /**
     * Lends the echo's stack in {@code slot} to the fake player for one {@code destroyBlock} call, then puts whatever is
     * in the fake hand back into that slot. Drops are held until the tool is back, then go into the echo's inventory;
     * what does not fit falls at the echo.
     */
    private static boolean destroyWith(ServerLevel level, EchoEntity echo, FakePlayer hand, BlockPos pos, int slot) {
        ItemStack tool = echo.inventory().getItem(slot);
        hand.getInventory().setSelectedSlot(slot);
        hand.getInventory().setItem(slot, tool);
        echo.inventory().setItem(slot, ItemStack.EMPTY);
        breaking = echo;
        breakingHand = hand;
        CAPTURED.clear();
        boolean broken;
        try {
            broken = hand.gameMode.destroyBlock(pos);
        } finally {
            ItemStack after = hand.getInventory().getItem(slot);
            hand.getInventory().setItem(slot, ItemStack.EMPTY);
            echo.inventory().setItem(slot, after);
            breaking = null;
            breakingHand = null;
            // Drops are held until the tool is back in its slot, so the lent slot is never mistaken for an empty one.
            for (ItemStack drop : CAPTURED) {
                echo.inventory().insert(drop);
                if (!drop.isEmpty()) {
                    echo.spawnAtLocation(level, drop);
                }
            }
            CAPTURED.clear();
        }
        return broken;
    }

    /** Result of a job break: what happened, and whether the tool used for it broke. */
    public record JobBreak(Outcome outcome, boolean toolBroke) {}

    /**
     * A job break (mining, tunnelling, clearing): the caller already chose the tool slot ({@code -1}: bare hand) and
     * checked that the block is safe to break. The tool is moved into the held hotbar slot first, so the echo visibly
     * holds it. The owner's fake player breaks the block, so protection events apply.
     */
    public static JobBreak breakForJob(ServerLevel level, EchoEntity echo, BlockPos pos, int toolSlot) {
        UUID owner = echo.ownerId();
        if (owner == null || !level.isLoaded(pos)) {
            return new JobBreak(Outcome.REFUSED, false);
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
            return new JobBreak(Outcome.SKIPPED_CHANGED, false);
        }
        EchoInventory inventory = echo.inventory();
        int slot = echo.selectedSlot();
        if (toolSlot >= 0 && toolSlot != slot) {
            if (toolSlot < Inventory.getSelectionSize()) {
                echo.setSelectedSlot(toolSlot);
            } else {
                ItemStack moved = inventory.removeItemNoUpdate(toolSlot);
                inventory.setItem(toolSlot, inventory.removeItemNoUpdate(slot));
                inventory.setItem(slot, moved);
            }
            slot = echo.selectedSlot();
        } else if (toolSlot < 0 && !inventory.getItem(slot).isEmpty()) {
            // Bare hand: hold an empty hotbar slot if there is one.
            int empty = firstEmptyHotbar(inventory);
            if (empty >= 0) {
                echo.setSelectedSlot(empty);
                slot = empty;
            }
        }
        ItemStack tool = inventory.getItem(slot);
        boolean damageable = !tool.isEmpty() && tool.isDamageableItem();
        net.minecraft.world.item.Item toolItem = tool.getItem();
        FakePlayer hand = hand(level, owner, echo.ownerName());
        aim(hand, echo, net.minecraft.world.phys.Vec3.atCenterOf(pos));
        hand.setShiftKeyDown(false);
        sweep(level, echo, hand);
        boolean broken;
        try {
            broken = destroyWith(level, echo, hand, pos, slot);
        } finally {
            sweep(level, echo, hand);
            breaking = null;
            breakingHand = null;
        }
        echo.swing(InteractionHand.MAIN_HAND);
        boolean toolBroke = damageable && !inventory.getItem(slot).is(toolItem);
        Mnemolith.LOGGER.debug("Mnemolith echo job break {} at {} -> {} toolBroke={}", echo.ownerName(), pos.toShortString(), broken, toolBroke);
        return new JobBreak(broken ? Outcome.DONE : Outcome.REFUSED, toolBroke);
    }

    /**
     * A job placement: the owner's fake player uses the echo's own block item on a neighbouring face (sneaking, so no
     * block is "used"), which fires the normal place events. If the block came out with other properties than the
     * blueprint asks for (facing, axis, half), the properties are set to the blueprint state afterwards; the block
     * itself is never swapped, and multi-block parts (doors, beds, double chests) are left as placed.
     */
    public static Outcome placeForJob(ServerLevel level, EchoEntity echo, BlockPos pos, BlockState target) {
        UUID owner = echo.ownerId();
        if (owner == null || !level.isLoaded(pos)) {
            return Outcome.REFUSED;
        }
        net.minecraft.world.item.Item item = com.mnemolith.echo.EchoLesson.itemFor(target);
        EchoInventory inventory = echo.inventory();
        int slot = inventory.getItem(echo.selectedSlot()).is(item) ? echo.selectedSlot() : inventory.find(item);
        if (slot < 0) {
            return Outcome.SKIPPED_NO_ITEM;
        }
        BlockHitResult hit = supportHit(level, pos);
        if (hit == null) {
            return Outcome.SKIPPED_CHANGED;
        }
        if (slot >= Inventory.getSelectionSize()) {
            int held = echo.selectedSlot();
            ItemStack moved = inventory.removeItemNoUpdate(slot);
            inventory.setItem(slot, inventory.removeItemNoUpdate(held));
            inventory.setItem(held, moved);
            slot = held;
        } else {
            echo.setSelectedSlot(slot);
        }
        FakePlayer hand = hand(level, owner, echo.ownerName());
        aim(hand, echo, hit.getLocation());
        hand.setShiftKeyDown(true);
        sweep(level, echo, hand);
        int handSlot = 0;
        ItemStack stack = inventory.getItem(slot);
        InteractionResult result;
        try {
            hand.getInventory().setSelectedSlot(handSlot);
            hand.getInventory().setItem(handSlot, stack);
            inventory.setItem(slot, ItemStack.EMPTY);
            try {
                result = hand.gameMode.useItemOn(hand, level, stack, InteractionHand.MAIN_HAND, hit);
            } finally {
                ItemStack after = hand.getInventory().getItem(handSlot);
                hand.getInventory().setItem(handSlot, ItemStack.EMPTY);
                inventory.setItem(slot, after);
            }
        } finally {
            hand.setShiftKeyDown(false);
            sweep(level, echo, hand);
        }
        echo.swing(InteractionHand.MAIN_HAND);
        BlockState now = level.getBlockState(pos);
        if (!result.consumesAction() || now.getBlock() != target.getBlock()) {
            Mnemolith.LOGGER.debug("Mnemolith echo job place {} at {} -> refused ({}, now {})", echo.ownerName(), pos.toShortString(), result, now);
            return Outcome.REFUSED;
        }
        if (now != target && correctable(target) && target.canSurvive(level, pos)) {
            level.setBlock(pos, target, net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        return Outcome.DONE;
    }

    /**
     * Pulls back a block the echo placed itself (stage 3: a mimicking replicant, or a misfired block the echo takes
     * back). The block turns to air without drops and exactly one block item goes back into the echo (or falls at it),
     * so nothing is duplicated or lost. The owner's fake player fires the normal break event first, so protection
     * mods can refuse it. Multi-part blocks and block entities are never touched.
     */
    public static boolean takeBack(ServerLevel level, EchoEntity echo, BlockPos pos, BlockState expected) {
        UUID owner = echo.ownerId();
        if (owner == null || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != expected.getBlock() || state.hasBlockEntity() || !correctable(state)) {
            return false;
        }
        net.minecraft.world.item.Item item = com.mnemolith.echo.EchoLesson.itemFor(state);
        if (item == net.minecraft.world.item.Items.AIR || !com.mnemolith.echo.job.EchoWork.safeToBreak(level, echo, pos)) {
            return false;
        }
        FakePlayer hand = hand(level, owner, echo.ownerName());
        aim(hand, echo, net.minecraft.world.phys.Vec3.atCenterOf(pos));
        sweep(level, echo, hand);
        var event = net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(level, GameType.SURVIVAL, hand, pos, state);
        if (event.isCanceled()) {
            return false;
        }
        if (!level.removeBlock(pos, false)) {
            return false;
        }
        ItemStack back = new ItemStack(item);
        echo.inventory().insert(back);
        if (!back.isEmpty()) {
            echo.spawnAtLocation(level, back);
        }
        return true;
    }

    private static boolean correctable(BlockState state) {
        return !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                && !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.CHEST_TYPE)
                && !state.hasBlockEntity();
    }

    /** A face of a solid neighbour that points at {@code pos}: below first, then the sides, then above. Null when nothing holds it. */
    public static @Nullable BlockHitResult supportHit(ServerLevel level, BlockPos pos) {
        net.minecraft.core.Direction[] order = {net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP};
        for (net.minecraft.core.Direction side : order) {
            BlockPos support = pos.relative(side);
            if (!level.isLoaded(support)) {
                continue;
            }
            BlockState state = level.getBlockState(support);
            if (state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, support).isEmpty()) {
                continue;
            }
            net.minecraft.core.Direction face = side.getOpposite();
            net.minecraft.world.phys.Vec3 location = net.minecraft.world.phys.Vec3.atCenterOf(support).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
            return new BlockHitResult(location, face, support, false);
        }
        return null;
    }

    private static void aim(FakePlayer hand, EchoEntity echo, net.minecraft.world.phys.Vec3 at) {
        net.minecraft.world.phys.Vec3 eye = echo.getEyePosition();
        net.minecraft.world.phys.Vec3 d = at.subtract(eye);
        float yaw = (float) (net.minecraft.util.Mth.atan2(d.z, d.x) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) -(net.minecraft.util.Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0D / Math.PI));
        hand.snapTo(echo.getX(), echo.getY(), echo.getZ(), yaw, pitch);
        hand.setYHeadRot(yaw);
        echo.setYRot(yaw);
        echo.setYHeadRot(yaw);
        echo.setXRot(pitch);
    }

    private static int firstEmptyHotbar(EchoInventory inventory) {
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
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

    /**
     * Blocks an echo breaks go into its own inventory first. The drops are taken out of the event here and handed to
     * the echo once the break call returns; whatever does not fit falls at the echo.
     */
    public static void onBlockDrops(BlockDropsEvent event) {
        if (breaking == null || event.getBreaker() != breakingHand || event.isCanceled()) {
            return;
        }
        var iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemEntity drop = iterator.next();
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty()) {
                CAPTURED.add(stack.copy());
            }
            iterator.remove();
        }
    }

    private static void log(EchoEntity echo, EchoAction action, Outcome outcome) {
        Mnemolith.LOGGER.debug("Mnemolith echo {} {} at {} -> {}", echo.ownerName(), action.kind().getSerializedName(), action.pos().toShortString(), outcome);
    }
}
