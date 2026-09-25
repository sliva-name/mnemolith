package com.mnemolith.echo.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.FarmLesson;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Harvest mature taught crops, replant, and remember the field scan. */
final class FarmController {
    private final EchoJob job;
    FarmLesson taught = FarmLesson.NONE;
    int harvested;
    final List<BlockPos> farmTasks = new ArrayList<>();
    final LongOpenHashSet farmRefused = new LongOpenHashSet();
    int farmScanIndex;
    int fieldSize;
    @Nullable BlockPos farmTarget;

    FarmController(EchoJob job) {
        this.job = job;
    }

    void resetSearch() {
        this.farmTarget = null;
        this.farmTasks.clear();
        this.farmRefused.clear();
    }

    String cropKey() {
        return this.taught.crops().isEmpty() ? "" : JobTexts.key(this.taught.crops().get(0));
    }

    private int farmRadius() {
        return Math.max(2, Math.min(this.job.radius(), CommonConfig.ECHO_FARM_MAX_RADIUS.get()));
    }

    Set<Item> seedItems() {
        Set<Item> seeds = new HashSet<>();
        for (Block crop : this.taught.crops()) {
            seeds.add(FarmLesson.seedFor(crop));
        }
        return seeds;
    }

    void tick(ServerLevel level, EchoEntity echo) {
        BlockPos anchor = this.job.workAnchor;
        if (anchor == null) {
            anchor = echo.blockPosition();
            this.job.workAnchor = anchor;
        }
        if (!level.isLoaded(anchor)) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.UNLOADED));
            return;
        }
        if (this.job.motion.placeCooldown > 0) {
            this.job.motion.placeCooldown--;
            return;
        }
        switch (this.job.motion.phase) {
            case START -> {
                this.farmTasks.clear();
                this.farmScanIndex = 0;
                this.fieldSize = 0;
                this.job.motion.phase = JobMotion.Phase.SCAN;
            }
            case SCAN -> {
                if (this.scanFarm(level, anchor, CommonConfig.ECHO_SCAN_BUDGET.get())) {
                    this.job.motion.phase = JobMotion.Phase.SELECT;
                }
            }
            case SELECT -> this.selectFarmTask(level, echo);
            case PATH -> this.job.motion.tickPath(level, echo);
            case WALK -> this.job.motion.tickWalk(level, echo);
            case TO_CHEST -> this.job.chests.atChest(level, echo);
            case WAIT -> {
                if (++this.job.motion.waitTicks % CommonConfig.ECHO_FARM_POLL_TICKS.get() == 0) {
                    this.job.motion.phase = JobMotion.Phase.START;
                }
            }
            default -> this.job.motion.phase = JobMotion.Phase.SELECT;
        }
    }

    /** Reads up to {@code budget} positions of the field box. True once the whole box was read. */
    private boolean scanFarm(ServerLevel level, BlockPos anchor, int budget) {
        int r = this.farmRadius();
        int side = r * 2 + 1;
        int height = 7;
        int total = side * side * height;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int work = 0;
        while (this.farmScanIndex < total && work < budget) {
            int i = this.farmScanIndex++;
            work++;
            int dx = i % side - r;
            int dz = (i / side) % side - r;
            int dy = i / (side * side) - 3;
            cursor.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
            if (!level.isLoaded(cursor)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof CropBlock) {
                this.fieldSize++;
                if (this.taught.knows(state.getBlock()) && FarmLesson.isMature(state) && this.farmTasks.size() < JobLimits.CANDIDATE_CAP) {
                    this.farmTasks.add(cursor.immutable());
                }
            } else if (state.is(Blocks.FARMLAND)) {
                this.fieldSize++;
                if (level.getBlockState(cursor.above()).isAir() && this.farmTasks.size() < JobLimits.CANDIDATE_CAP) {
                    this.farmTasks.add(cursor.above().immutable());
                }
            }
        }
        return this.farmScanIndex >= total;
    }

    /** A harvest (mature taught crop) or a planting spot (air over farmland, and the echo carries a taught seed). */
    private boolean farmTaskValid(ServerLevel level, EchoEntity echo, BlockPos pos) {
        if (!level.isLoaded(pos) || this.farmRefused.contains(pos.asLong())) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (FarmLesson.isMature(state)) {
            return this.taught.knows(state.getBlock());
        }
        return state.isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND) && this.seedToPlant(echo, null) != null;
    }

    /** The crop to plant: {@code preferred} when the echo carries its seed, else the first taught crop it has seeds for. */
    private @Nullable Block seedToPlant(EchoEntity echo, @Nullable Block preferred) {
        if (preferred != null && this.taught.knows(preferred) && echo.inventory().find(FarmLesson.seedFor(preferred)) >= 0) {
            return preferred;
        }
        for (Block crop : this.taught.crops()) {
            if (echo.inventory().find(FarmLesson.seedFor(crop)) >= 0) {
                return crop;
            }
        }
        return null;
    }

    /** Harvest and extra planting items above what it keeps for replanting. */
    int produce(EchoEntity echo) {
        Set<Item> seeds = this.seedItems();
        Map<Item, Integer> seedCounts = new LinkedHashMap<>();
        int produce = 0;
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            ItemStack stack = echo.inventory().getItem(i);
            if (stack.isEmpty() || stack.isDamageableItem() || stack.has(DataComponents.TOOL) || stack.has(DataComponents.EQUIPPABLE)) {
                continue;
            }
            if (seeds.contains(stack.getItem())) {
                seedCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            } else {
                produce += stack.getCount();
            }
        }
        for (int count : seedCounts.values()) {
            produce += Math.max(0, count - 64);
        }
        return produce;
    }

    private void selectFarmTask(ServerLevel level, EchoEntity echo) {
        if (this.job.chests.needsDropOff(echo)) {
            if (this.job.chest == null) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.INVENTORY_FULL));
            } else {
                this.job.chests.goToChest(level, echo);
            }
            return;
        }
        if (this.job.motion.unreachableInRow >= JobLimits.UNREACHABLE_GIVE_UP) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.UNREACHABLE));
            return;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        var iterator = this.farmTasks.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!this.farmTaskValid(level, echo, pos)) {
                iterator.remove();
                continue;
            }
            double distance = pos.distToCenterSqr(echo.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        if (best == null) {
            if (this.fieldSize == 0) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.NO_FIELD));
                return;
            }
            if (this.job.chest != null && this.produce(echo) > 0) {
                this.job.chests.goToChest(level, echo);
                return;
            }
            this.job.release(echo);
            this.job.setStatus(new JobStatus(JobStatus.Kind.FARM_WAIT, this.cropKey(), this.harvested, 0));
            this.job.motion.phase = JobMotion.Phase.WAIT;
            this.job.motion.waitTicks = 0;
            this.farmRefused.clear();
            return;
        }
        this.farmTarget = best;
        BlockState state = level.getBlockState(best);
        String crop = state.getBlock() instanceof CropBlock ? JobTexts.key(state.getBlock()) : this.cropKey();
        this.job.setStatus(new JobStatus(JobStatus.Kind.FARMING, crop, this.harvested, 0));
        BlockPos goalPos = best;
        this.job.motion.startPath(level, echo, new EchoNav.Goal() {
            @Override
            public boolean reached(BlockPos feet) {
                return JobReach.canWorkOn(level, feet, goalPos);
            }

            @Override
            public double estimate(BlockPos feet) {
                return Math.max(0.0D, Math.sqrt(feet.distSqr(goalPos)) - 3.0D);
            }
        }, null, 0, false);
    }

    /** At the spot: harvest a mature crop and replant it from the echo's own seeds, or plant empty farmland. */
    void act(ServerLevel level, EchoEntity echo) {
        BlockPos pos = this.farmTarget;
        this.farmTarget = null;
        this.job.motion.phase = JobMotion.Phase.SELECT;
        if (pos == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        echo.lookAt(Vec3.atCenterOf(pos));
        if (FarmLesson.isMature(state) && this.taught.knows(state.getBlock())) {
            if (this.job.strain.misfire(echo)) {
                echo.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.job.strain.misfireNotice(echo, "skip", pos);
                this.job.motion.placeCooldown = 20;
                return;
            }
            if (!EchoWork.safeToBreak(level, echo, pos)) {
                this.farmRefused.add(pos.asLong());
                return;
            }
            Block crop = state.getBlock();
            EchoHands.JobBreak result = EchoHands.breakForJob(level, echo, pos, -1);
            if (result.outcome() != EchoHands.Outcome.DONE) {
                this.farmRefused.add(pos.asLong());
                return;
            }
            this.harvested++;
            this.job.motion.unreachableInRow = 0;
            this.job.strain.onWorkAction(level, echo, pos);
            this.plant(level, echo, pos, crop);
            this.job.setStatus(new JobStatus(JobStatus.Kind.FARMING, JobTexts.key(crop), this.harvested, 0));
            this.job.motion.placeCooldown = JobLimits.PLACE_INTERVAL;
            this.job.mimic.afterBreak();
            return;
        }
        if (state.isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
            if (!this.plant(level, echo, pos, null)) {
                this.farmRefused.add(pos.asLong());
            }
            this.job.motion.placeCooldown = JobLimits.PLACE_INTERVAL;
        }
    }

    /** Plants a taught crop at {@code pos} from the echo's own seeds. In overload it may pick another taught seed. */
    private boolean plant(ServerLevel level, EchoEntity echo, BlockPos pos, @Nullable Block preferred) {
        Block crop = this.seedToPlant(echo, preferred);
        if (crop == null) {
            return false;
        }
        if (this.taught.crops().size() > 1 && this.job.strain.misfire(echo) && this.job.strain.misfires++ % 2 == 1) {
            for (Block other : this.taught.crops()) {
                if (other != crop && echo.inventory().find(FarmLesson.seedFor(other)) >= 0) {
                    crop = other;
                    this.job.strain.misfireNotice(echo, "seed", pos);
                    break;
                }
            }
        }
        boolean planted = EchoHands.placeForJob(level, echo, pos, crop.defaultBlockState()) == EchoHands.Outcome.DONE;
        if (planted) {
            this.job.motion.unreachableInRow = 0;
            this.job.strain.onWorkAction(level, echo, pos);
        }
        return planted;
    }
}
