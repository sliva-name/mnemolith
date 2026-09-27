package com.mnemolith.worldgen.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.PleadingChair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * Picks one indoor floor cell in a village house piece and stands a chair there.
 * About one house in eight. Farms, pens, stables, and meeting points are not passed in.
 */
public final class ChairHouses {
    public static final int CHANCE = 8;

    private ChairHouses() {}

    /** Stable across the two chunk placements of one house piece. */
    public static boolean selected(BlockPos pieceOrigin) {
        return RandomSource.create(Mth.getSeed(pieceOrigin)).nextInt(CHANCE) == 0;
    }

    /**
     * Air cell just above an interior floor, or null when the piece has no closed room.
     * {@code random} only chooses which of the valid cells.
     */
    public static BlockPos chooseSpot(List<StructureTemplate.StructureBlockInfo> blocks, RandomSource random) {
        List<BlockPos> spots = interiorFloors(blocks);
        if (spots.isEmpty()) {
            return null;
        }
        return spots.get(random.nextInt(spots.size()));
    }

    public static float yawTowardCenter(List<StructureTemplate.StructureBlockInfo> blocks, BlockPos spot) {
        List<BlockPos> spots = interiorFloors(blocks);
        if (spots.isEmpty()) {
            return 0.0F;
        }
        double cx = 0.0D;
        double cz = 0.0D;
        for (BlockPos pos : spots) {
            cx += pos.getX() + 0.5D;
            cz += pos.getZ() + 0.5D;
        }
        cx /= spots.size();
        cz /= spots.size();
        double dx = cx - (spot.getX() + 0.5D);
        double dz = cz - (spot.getZ() + 0.5D);
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }

    public static void spawn(ServerLevelAccessor level, BlockPos spot, float yaw) {
        if (level instanceof net.minecraft.world.level.Level world) {
            AABB box = new AABB(spot);
            if (!world.getEntitiesOfClass(PleadingChair.class, box, entity -> entity.isAlive()).isEmpty()) {
                return;
            }
        }
        PleadingChair chair = ModEntities.PLEADING_CHAIR.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (chair == null) {
            return;
        }
        chair.snapTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, yaw, 0.0F);
        chair.yRotO = yaw;
        if (!level.addFreshEntity(chair)) {
            return;
        }
        BlockPos at = chair.blockPosition();
        Mnemolith.LOGGER.debug("Mnemolith pleading chair at {},{},{}", at.getX(), at.getY(), at.getZ());
    }

    static List<BlockPos> interiorFloors(List<StructureTemplate.StructureBlockInfo> blocks) {
        Map<BlockPos, BlockState> at = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            BlockPos pos = info.pos();
            at.put(pos, info.state());
            minX = Math.min(minX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        List<BlockPos> spots = new ArrayList<>();
        if (at.isEmpty()) {
            return spots;
        }
        for (Map.Entry<BlockPos, BlockState> entry : at.entrySet()) {
            BlockPos floor = entry.getKey();
            if (!isFloor(entry.getValue())) {
                continue;
            }
            BlockPos stand = floor.above();
            if (!open(at, stand) || !open(at, stand.above()) || !hasCeiling(at, stand) || besideDoor(at, stand)) {
                continue;
            }
            if (stand.getX() <= minX || stand.getX() >= maxX || stand.getZ() <= minZ || stand.getZ() >= maxZ) {
                continue;
            }
            spots.add(stand);
        }
        return spots;
    }

    private static boolean isFloor(BlockState state) {
        if (state.isAir() || state.getBlock() instanceof FarmlandBlock || state.is(Blocks.DIRT_PATH)) {
            return false;
        }
        return state.blocksMotion() && state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static boolean open(Map<BlockPos, BlockState> at, BlockPos pos) {
        BlockState state = at.get(pos);
        return state != null && (state.isAir() || state.getBlock() instanceof CarpetBlock);
    }

    private static boolean hasCeiling(Map<BlockPos, BlockState> at, BlockPos stand) {
        for (int dy = 2; dy <= 4; dy++) {
            BlockState above = at.get(stand.above(dy));
            if (above != null && above.blocksMotion()) {
                return true;
            }
        }
        return false;
    }

    private static boolean besideDoor(Map<BlockPos, BlockState> at, BlockPos stand) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState neighbor = at.get(stand.relative(direction));
            if (neighbor != null && neighbor.getBlock() instanceof DoorBlock) {
                return true;
            }
        }
        return false;
    }
}
