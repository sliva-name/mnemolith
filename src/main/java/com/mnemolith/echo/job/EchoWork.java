package com.mnemolith.echo.job;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mnemolith.echo.EchoHands;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.TriState;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Tool choice, break safety, and chest transfers for echo jobs. Everything here reads or changes only the echo's own items or a linked chest. */
public final class EchoWork {
    /** Reach from the echo's eyes to the centre of a block it mines or places. */
    public static final double REACH = 4.5D;

    private EchoWork() {}

    // ---- tools ----

    /** Main slot of the best tool for {@code state}: a correct tool first, then the fastest. -1: bare hand (or none, see {@link #needsTool}). */
    public static int bestTool(EchoInventory inventory, BlockState state) {
        int best = -1;
        float bestSpeed = 1.0F;
        boolean needsCorrect = state.requiresCorrectToolForDrops();
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (needsCorrect && !stack.isCorrectToolForDrops(state)) {
                continue;
            }
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed || (needsCorrect && best < 0)) {
                best = i;
                bestSpeed = speed;
            }
        }
        return best;
    }

    public static boolean needsTool(BlockState state) {
        return state.requiresCorrectToolForDrops();
    }

    /** Short tool name for the "no tool" status: pickaxe, shovel, axe, hoe, or tool. */
    public static String toolKind(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return "pickaxe";
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return "shovel";
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return "axe";
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return "hoe";
        }
        return "tool";
    }

    /** Ticks a break takes with {@code tool}, like a player standing on the ground (without enchantments or effects). */
    public static int breakTicks(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0.0F) {
            return 2;
        }
        float speed = Math.max(1.0F, tool.isEmpty() ? 1.0F : tool.getDestroySpeed(state));
        boolean correct = !state.requiresCorrectToolForDrops() || (!tool.isEmpty() && tool.isCorrectToolForDrops(state));
        float perTick = speed / hardness / (correct ? 30.0F : 100.0F);
        return Math.max(2, Math.min(200, (int) Math.ceil(1.0F / perTick)));
    }

    // ---- safety ----

    /** Natural terrain an echo may dig through on its way to a target. */
    public static boolean tunnelMaterial(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(Tags.Blocks.STONES);
    }

    /** Terrain a building echo may clear when {@code echoBuildClearsTerrain} is on. */
    public static boolean clearableTerrain(BlockState state) {
        return tunnelMaterial(state) || state.is(BlockTags.SAND) || state.is(Tags.Blocks.GRAVELS) || state.canBeReplaced()
                || state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES);
    }

    /**
     * The shared break rules: nothing with a block entity or unbreakable, nothing next to a fluid (or holding one), and
     * never the block under the echo when the drop below would be more than 3 blocks. Protection events are checked
     * by the actual break through the fake player.
     */
    public static boolean safeToBreak(ServerLevel level, EchoEntity echo, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || level.getBlockEntity(pos) != null || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        // A kindled graft makes the echo lava-proof, so lava beside the block is allowed (never water, never a fluid block).
        boolean lavaProof = com.mnemolith.echo.graft.EchoGrafts.lavaProof(echo);
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (!level.isLoaded(next)) {
                return false;
            }
            var fluid = level.getFluidState(next);
            if (!fluid.isEmpty() && !(lavaProof && fluid.is(net.minecraft.tags.FluidTags.LAVA))) {
                return false;
            }
        }
        // A plunging graft lets the echo dig out its own floor over a longer drop.
        if (supports(echo, pos) && dropBelow(level, pos) > com.mnemolith.echo.graft.EchoGrafts.maxDrop(echo)) {
            return false;
        }
        return true;
    }

    /** A tunnel cell: tunnel material, safe, the echo has what it takes, and nothing loose rests on it. */
    public static boolean canTunnel(ServerLevel level, EchoEntity echo, BlockPos pos, BlockState state) {
        if (!tunnelMaterial(state) || !safeToBreak(level, echo, pos)) {
            return false;
        }
        BlockPos above = pos.above();
        if (level.isLoaded(above) && level.getBlockState(above).getBlock() instanceof FallingBlock) {
            return false;
        }
        return !needsTool(state) || bestTool(echo.inventory(), state) >= 0;
    }

    /** Whether the echo stands on {@code pos}. */
    public static boolean supports(EchoEntity echo, BlockPos pos) {
        var box = echo.getBoundingBox();
        return box.minY >= pos.getY() + 0.5D && box.minY <= pos.getY() + 1.5D
                && box.maxX > pos.getX() && box.minX < pos.getX() + 1 && box.maxZ > pos.getZ() && box.minZ < pos.getZ() + 1;
    }

    /** How far the echo would fall if {@code pos} were gone (counts empty cells below it). */
    public static int dropBelow(ServerLevel level, BlockPos pos) {
        int drop = 1;
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < com.mnemolith.echo.graft.EchoGrafts.PLUNGE_DROP + 1; i++) {
            cursor.move(Direction.DOWN);
            if (!level.isLoaded(cursor)) {
                return 99;
            }
            BlockState below = level.getBlockState(cursor);
            if (!below.getFluidState().isEmpty()) {
                return 99;
            }
            if (!below.getCollisionShape(level, cursor).isEmpty()) {
                return drop;
            }
            drop++;
        }
        return drop;
    }

    // ---- chests ----

    public static @Nullable Container container(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || level.getBlockEntity(pos) == null) {
            return null;
        }
        return HopperBlockEntity.getContainerAt(level, pos);
    }

    /** Whether the owner's fake player may open the block: protection mods cancel or deny the right-click event. */
    public static boolean mayOpen(ServerLevel level, EchoEntity echo, BlockPos pos) {
        if (echo.ownerId() == null) {
            return false;
        }
        FakePlayer hand = EchoHands.hand(level, echo.ownerId(), echo.ownerName());
        hand.snapTo(echo.getX(), echo.getY(), echo.getZ(), echo.getYRot(), echo.getXRot());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock event = CommonHooks.onRightClickBlock(hand, InteractionHand.MAIN_HAND, pos, hit);
        return !event.isCanceled() && event.getUseBlock() != TriState.FALSE;
    }

    /** Whether a stack stays with the echo on a drop-off: tools, weapons, armor, food, and anything in {@code keep}. */
    public static boolean keeps(ItemStack stack, Map<Item, Integer> keep) {
        return stack.isDamageableItem() || stack.has(DataComponents.TOOL) || stack.has(DataComponents.FOOD) || stack.has(DataComponents.EQUIPPABLE)
                || keep.containsKey(stack.getItem());
    }

    /**
     * Moves everything except kept stacks from the echo's main slots into the chest. Returns the number of items
     * moved; what does not fit stays with the echo.
     */
    public static int deposit(EchoEntity echo, Container chest, Map<Item, Integer> keep) {
        int moved = 0;
        EchoInventory inventory = echo.inventory();
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || keeps(stack, keep)) {
                continue;
            }
            ItemStack taken = inventory.removeItemNoUpdate(i);
            int before = taken.getCount();
            ItemStack rest = HopperBlockEntity.addItem(null, chest, taken, null);
            moved += before - rest.getCount();
            inventory.setItem(i, rest);
        }
        if (moved > 0) {
            chest.setChanged();
        }
        return moved;
    }

    /**
     * Stage 3 farm drop-off: moves the harvest (food included) into the chest and keeps tools, armor and up to
     * {@code keepSeeds} of each planting item in {@code seeds} for replanting. Returns the number of items moved;
     * what does not fit stays with the echo.
     */
    public static int depositFarm(EchoEntity echo, Container chest, java.util.Set<Item> seeds, int keepSeeds) {
        int moved = 0;
        EchoInventory inventory = echo.inventory();
        Map<Item, Integer> kept = new LinkedHashMap<>();
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.isDamageableItem() || stack.has(DataComponents.TOOL) || stack.has(DataComponents.EQUIPPABLE)) {
                continue;
            }
            int move = stack.getCount();
            if (seeds.contains(stack.getItem())) {
                int already = kept.getOrDefault(stack.getItem(), 0);
                int keep = Math.max(0, Math.min(stack.getCount(), keepSeeds - already));
                kept.put(stack.getItem(), already + keep);
                move = stack.getCount() - keep;
            }
            if (move <= 0) {
                continue;
            }
            ItemStack taken = inventory.removeItem(i, move);
            int before = taken.getCount();
            ItemStack rest = HopperBlockEntity.addItem(null, chest, taken, null);
            moved += before - rest.getCount();
            if (!rest.isEmpty()) {
                // Put back what did not fit (same slot if it is empty now, else anywhere; never dropped).
                ItemStack now = inventory.getItem(i);
                if (now.isEmpty()) {
                    inventory.setItem(i, rest);
                } else {
                    now.grow(rest.getCount());
                }
            }
        }
        if (moved > 0) {
            chest.setChanged();
        }
        return moved;
    }

    /** Takes up to {@code wanted} of each item from the chest into the echo. Returns the number of items taken. */
    public static int take(EchoEntity echo, Container chest, Map<Item, Integer> wanted) {
        int taken = 0;
        EchoInventory inventory = echo.inventory();
        Map<Item, Integer> left = new LinkedHashMap<>(wanted);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Integer want = left.get(stack.getItem());
            if (want == null || want <= 0) {
                continue;
            }
            ItemStack part = chest.removeItem(i, Math.min(want, stack.getCount()));
            int count = part.getCount();
            inventory.insert(part);
            if (!part.isEmpty()) {
                // The echo is full: put the rest back where it came from.
                ItemStack back = chest.getItem(i);
                if (back.isEmpty()) {
                    chest.setItem(i, part);
                } else {
                    back.grow(part.getCount());
                }
                count -= part.getCount();
                left.put(stack.getItem(), want - count);
                taken += count;
                break;
            }
            left.put(stack.getItem(), want - count);
            taken += count;
        }
        if (taken > 0) {
            chest.setChanged();
        }
        return taken;
    }

    public static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int freeMainSlots(EchoInventory inventory) {
        int free = 0;
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            if (inventory.getItem(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }
}
