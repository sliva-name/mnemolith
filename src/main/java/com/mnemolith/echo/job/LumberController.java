package com.mnemolith.echo.job;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.LumberLesson;
import com.mnemolith.entity.echo.EchoEntity;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Chop taught logs and replant saplings in the work radius. */
final class LumberController {
    private final EchoJob job;
    LumberLesson taught = LumberLesson.NONE;
    int chopped;
    final List<BlockPos> tasks = new ArrayList<>();
    final LongOpenHashSet refused = new LongOpenHashSet();
    int scanIndex;
    int fieldSize;
    @Nullable BlockPos target;
    /** True when the current target is a planting spot (air over plantable ground). */
    boolean planting;

    LumberController(EchoJob job) {
        this.job = job;
    }

    void resetSearch() {
        this.target = null;
        this.tasks.clear();
        this.refused.clear();
        this.planting = false;
    }

    String logKey() {
        return this.taught.logs().isEmpty() ? "" : JobTexts.key(this.taught.logs().get(0));
    }

    private int radius() {
        return Math.max(2, Math.min(this.job.radius(), CommonConfig.ECHO_FARM_MAX_RADIUS.get()));
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
                this.tasks.clear();
                this.scanIndex = 0;
                this.fieldSize = 0;
                this.job.motion.phase = JobMotion.Phase.SCAN;
            }
            case SCAN -> {
                if (this.scan(level, anchor, CommonConfig.ECHO_SCAN_BUDGET.get())) {
                    this.job.motion.phase = JobMotion.Phase.SELECT;
                }
            }
            case SELECT -> this.select(level, echo);
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

    private boolean scan(ServerLevel level, BlockPos anchor, int budget) {
        int r = this.radius();
        int side = r * 2 + 1;
        int height = 15;
        int total = side * side * height;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int work = 0;
        while (this.scanIndex < total && work < budget) {
            int i = this.scanIndex++;
            work++;
            int dx = i % side - r;
            int dz = (i / side) % side - r;
            int dy = i / (side * side) - 1;
            cursor.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
            if (!level.isLoaded(cursor)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (LumberLesson.isLog(state) && this.taught.knowsLog(state.getBlock())) {
                this.fieldSize++;
                this.remember(cursor.immutable(), true);
            } else if (state.isAir() && LumberLesson.canPlantOn(level.getBlockState(cursor.below()))) {
                this.fieldSize++;
                this.remember(cursor.immutable(), false);
            }
        }
        return this.scanIndex >= total;
    }

    private void remember(BlockPos pos, boolean chop) {
        if (this.tasks.size() < JobLimits.CANDIDATE_CAP) {
            this.tasks.add(pos);
            return;
        }
        if (!chop) {
            return;
        }
        // Prefer chops over plant spots when the list is full.
        for (int i = 0; i < this.tasks.size(); i++) {
            // Keep chops; replace a plant candidate (we don't track which — re-validate later).
        }
    }

    private boolean valid(ServerLevel level, EchoEntity echo, BlockPos pos) {
        if (!level.isLoaded(pos) || this.refused.contains(pos.asLong())) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (LumberLesson.isLog(state) && this.taught.knowsLog(state.getBlock())) {
            return true;
        }
        if (state.isAir() && LumberLesson.canPlantOn(level.getBlockState(pos.below()))) {
            return this.saplingToPlant(echo) != null;
        }
        return false;
    }

    private @Nullable Block saplingToPlant(EchoEntity echo) {
        for (Block log : this.taught.logs()) {
            Item item = this.taught.saplingItemFor(log);
            if (item != Items.AIR && echo.inventory().find(item) >= 0) {
                Block sapling = this.taught.saplingFor(log);
                if (sapling != null) {
                    return sapling;
                }
            }
        }
        for (Block sapling : this.taught.saplings()) {
            if (echo.inventory().find(sapling.asItem()) >= 0) {
                return sapling;
            }
        }
        return null;
    }

    private void select(ServerLevel level, EchoEntity echo) {
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
        boolean bestPlant = false;
        var iterator = this.tasks.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!this.valid(level, echo, pos)) {
                iterator.remove();
                continue;
            }
            boolean plant = level.getBlockState(pos).isAir();
            double distance = pos.distToCenterSqr(echo.position());
            // Prefer chops over planting when distances are close.
            if (plant && best != null && !bestPlant && distance + 4.0D > bestDistance) {
                continue;
            }
            if (distance < bestDistance || (!plant && bestPlant)) {
                bestDistance = distance;
                best = pos;
                bestPlant = plant;
            }
        }
        if (best == null) {
            if (this.fieldSize == 0) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.NO_FIELD));
                return;
            }
            this.job.release(echo);
            this.job.setStatus(new JobStatus(JobStatus.Kind.LUMBER_WAIT, this.logKey(), this.chopped, 0));
            this.job.motion.phase = JobMotion.Phase.WAIT;
            this.job.motion.waitTicks = 0;
            this.refused.clear();
            return;
        }
        this.target = best;
        this.planting = bestPlant;
        this.job.setStatus(new JobStatus(JobStatus.Kind.LUMBER, this.logKey(), this.chopped, 0));
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

    void act(ServerLevel level, EchoEntity echo) {
        BlockPos pos = this.target;
        boolean plant = this.planting;
        this.target = null;
        this.planting = false;
        this.job.motion.phase = JobMotion.Phase.SELECT;
        if (pos == null) {
            return;
        }
        echo.lookAt(Vec3.atCenterOf(pos));
        BlockState state = level.getBlockState(pos);
        if (!plant && LumberLesson.isLog(state) && this.taught.knowsLog(state.getBlock())) {
            if (!EchoWork.safeToBreak(level, echo, pos)) {
                this.refused.add(pos.asLong());
                return;
            }
            int tool = EchoWork.bestTool(echo.inventory(), state);
            if (tool < 0 && EchoWork.needsTool(state)) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.NO_TOOL, EchoWork.toolKind(state)));
                return;
            }
            EchoHands.JobBreak result = EchoHands.breakForJob(level, echo, pos, tool);
            if (result.outcome() != EchoHands.Outcome.DONE) {
                this.refused.add(pos.asLong());
                return;
            }
            this.chopped++;
            this.job.motion.unreachableInRow = 0;
            this.job.strain.onWorkAction(level, echo, pos);
            this.job.setStatus(new JobStatus(JobStatus.Kind.LUMBER, JobTexts.key(state.getBlock()), this.chopped, 0));
            this.job.motion.placeCooldown = JobLimits.PLACE_INTERVAL;
            // Try to replant on the ground under the chopped log when it is now air over plantable soil.
            BlockPos ground = pos;
            while (ground.getY() > level.getMinY() && level.getBlockState(ground).isAir()) {
                ground = ground.below();
            }
            BlockPos plantAt = ground.above();
            if (level.getBlockState(plantAt).isAir() && LumberLesson.canPlantOn(level.getBlockState(ground))) {
                this.plant(level, echo, plantAt, state.getBlock());
            }
            return;
        }
        if (plant && state.isAir() && LumberLesson.canPlantOn(level.getBlockState(pos.below()))) {
            if (!this.plant(level, echo, pos, null)) {
                this.refused.add(pos.asLong());
            }
            this.job.motion.placeCooldown = JobLimits.PLACE_INTERVAL;
        }
    }

    private boolean plant(ServerLevel level, EchoEntity echo, BlockPos pos, @Nullable Block fromLog) {
        Block sapling = fromLog != null ? this.taught.saplingFor(fromLog) : this.saplingToPlant(echo);
        if (sapling == null) {
            sapling = this.saplingToPlant(echo);
        }
        if (sapling == null || sapling == Blocks.AIR) {
            return false;
        }
        if (echo.inventory().find(sapling.asItem()) < 0) {
            return false;
        }
        boolean planted = EchoHands.placeForJob(level, echo, pos, sapling.defaultBlockState()) == EchoHands.Outcome.DONE;
        if (planted) {
            this.job.motion.unreachableInRow = 0;
            this.job.strain.onWorkAction(level, echo, pos);
        }
        return planted;
    }
}
