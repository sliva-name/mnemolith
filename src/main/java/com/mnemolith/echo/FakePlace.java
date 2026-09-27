package com.mnemolith.echo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Block changes that have no item of their own, posted through the same place hooks a {@link FakePlayer} item use
 * uses. {@link EventHooks#onBlockPlace} (and the multi-block variant) fire, so a claim mod can cancel them. While
 * snapshots are being captured, chunk placement skips {@code onPlace}; a commit that is not canceled calls it, which
 * is what schedules a replicated moment's fade.
 * <p>
 * A replicant ghost uses a stable profile, never a new UUID per place. When {@code placeCopy} has a player, that
 * player's UUID is used with the name {@code name + "#replicant"}, so claims see the player and the profile does not
 * share the echo's {@code #hand} inventory. With no player, the profile is {@link #GHOST_ID} / {@link #GHOST_NAME}
 * ({@code UUID.nameUUIDFromBytes("mnemolith:moment_replicant")}). Ghost commits set {@link #skippingOwnerImprint()}
 * for the place event only, so imprint and echo-actor listeners do not file it as the owner building.
 */
public final class FakePlace {
    /** Stable id for an unlinked replicant. Same bytes every place: {@code mnemolith:moment_replicant}. */
    public static final UUID GHOST_ID = UUID.nameUUIDFromBytes("mnemolith:moment_replicant".getBytes(StandardCharsets.UTF_8));
    /** 15 characters, under the usual profile-name length. */
    public static final String GHOST_NAME = "MomentReplicant";

    private static final ThreadLocal<Integer> SKIP_OWNER_IMPRINT = ThreadLocal.withInitial(() -> 0);

    private FakePlace() {}

    /** True only while a ghost commit is posting its place event. */
    public static boolean skippingOwnerImprint() {
        return SKIP_OWNER_IMPRINT.get() > 0;
    }

    /**
     * Places {@code state} at {@code pos} as {@code player}, with the place event a block item would post.
     * False when the world rejects the change or a listener cancels the place. A cancel restores the old block.
     * {@code skipOwnerImprint} is set only around that event, so a ghost is not written as the owner's build.
     */
    public static boolean commit(ServerLevel level, Player player, BlockPos pos, BlockState state, Direction clickedFace, int flags, boolean skipOwnerImprint) {
        pos = pos.immutable();
        BlockState before = level.getBlockState(pos);
        int from = level.capturedBlockSnapshots.size();
        boolean previousCapture = level.captureBlockSnapshots;
        level.captureBlockSnapshots = true;
        boolean changed;
        try {
            changed = level.setBlock(pos, state, flags);
        } finally {
            level.captureBlockSnapshots = previousCapture;
        }
        if (!changed) {
            return false;
        }
        int end = level.capturedBlockSnapshots.size();
        if (end < from) {
            from = end;
        }
        List<BlockSnapshot> snaps = new ArrayList<>(level.capturedBlockSnapshots.subList(from, end));
        if (from < end) {
            level.capturedBlockSnapshots.subList(from, end).clear();
        }
        boolean canceled = post(player, snaps, clickedFace, skipOwnerImprint);
        if (canceled) {
            level.restoringBlockSnapshots = true;
            try {
                for (int i = snaps.size() - 1; i >= 0; i--) {
                    BlockSnapshot snap = snaps.get(i);
                    snap.restore(snap.getFlags() | Block.UPDATE_CLIENTS);
                }
                if (snaps.isEmpty()) {
                    level.setBlock(pos, before, flags | Block.UPDATE_CLIENTS);
                }
            } finally {
                level.restoringBlockSnapshots = false;
            }
            return false;
        }
        if (snaps.isEmpty()) {
            state.onPlace(level, pos, before, false);
            level.markAndNotifyBlock(pos, level.getChunkAt(pos), before, state, flags, 512);
            return true;
        }
        for (BlockSnapshot snap : snaps) {
            BlockState oldBlock = snap.getState();
            BlockState newBlock = level.getBlockState(snap.getPos());
            int updateFlag = snap.getFlags();
            newBlock.onPlace(level, snap.getPos(), oldBlock, false);
            level.markAndNotifyBlock(snap.getPos(), level.getChunkAt(snap.getPos()), oldBlock, newBlock, updateFlag, 512);
        }
        return true;
    }

    /**
     * Right-click then commit of a replicated moment. Grief rules stay with the caller ({@code canEntityGrief});
     * this only refuses when the fake player's right-click is canceled or the block/item use is denied.
     */
    public static boolean placeGhost(ServerLevel level, @Nullable ServerPlayer linked, LivingEntity actor, BlockPos pos, BlockState state) {
        UUID id = linked != null ? linked.getUUID() : GHOST_ID;
        String name = linked != null ? linkedName(linked) : GHOST_NAME;
        FakePlayer hand = EchoHands.hand(level, id, name);
        hand.snapTo(actor.getX(), actor.getY(), actor.getZ(), actor.getYRot(), actor.getXRot());
        hand.setYHeadRot(actor.getYHeadRot());
        boolean shift = hand.isShiftKeyDown();
        hand.setShiftKeyDown(false);
        Inventory inventory = hand.getInventory();
        int selected = inventory.getSelectedSlot();
        ItemStack main = inventory.getItem(selected);
        ItemStack off = inventory.getItem(Inventory.SLOT_OFFHAND);
        inventory.setItem(selected, ItemStack.EMPTY);
        inventory.setItem(Inventory.SLOT_OFFHAND, ItemStack.EMPTY);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            PlayerInteractEvent.RightClickBlock event = CommonHooks.onRightClickBlock(hand, InteractionHand.MAIN_HAND, pos, hit);
            if (event.isCanceled() || event.getUseBlock() == TriState.FALSE || event.getUseItem() == TriState.FALSE) {
                return false;
            }
            return commit(level, hand, pos, state, Direction.UP, Block.UPDATE_ALL, true);
        } finally {
            inventory.setItem(selected, main);
            inventory.setItem(Inventory.SLOT_OFFHAND, off);
            hand.setShiftKeyDown(shift);
        }
    }

    private static String linkedName(ServerPlayer linked) {
        String name = linked.getGameProfile().name();
        return name.isEmpty() ? "replicant" : name + "#replicant";
    }

    private static boolean post(Player player, List<BlockSnapshot> snaps, Direction clickedFace, boolean skipOwnerImprint) {
        if (skipOwnerImprint) {
            SKIP_OWNER_IMPRINT.set(SKIP_OWNER_IMPRINT.get() + 1);
        }
        try {
            if (snaps.size() > 1) {
                return EventHooks.onMultiBlockPlace(player, snaps, clickedFace);
            }
            if (snaps.size() == 1) {
                return EventHooks.onBlockPlace(player, snaps.get(0), clickedFace);
            }
            return false;
        } finally {
            if (skipOwnerImprint) {
                int depth = SKIP_OWNER_IMPRINT.get() - 1;
                if (depth <= 0) {
                    SKIP_OWNER_IMPRINT.remove();
                } else {
                    SKIP_OWNER_IMPRINT.set(depth);
                }
            }
        }
    }
}
