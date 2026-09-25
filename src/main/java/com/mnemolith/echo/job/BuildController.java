package com.mnemolith.echo.job;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.entity.echo.EchoEntity;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Blueprint walk: pick the next block, place it, and remember what is blocked or missing. */
final class BuildController {
    private final EchoJob job;
    List<EchoLesson.Entry> plan = List.of();
    final LongOpenHashSet blocked = new LongOpenHashSet();
    final LongOpenHashSet skipped = new LongOpenHashSet();
    final Map<Long, Integer> placeFailures = new LinkedHashMap<>();
    int builtCount;
    EchoLesson.@Nullable Entry buildTarget;
    boolean fetching;

    BuildController(EchoJob job) {
        this.job = job;
    }

    void resetSearch() {
        this.blocked.clear();
        this.skipped.clear();
        this.placeFailures.clear();
        this.buildTarget = null;
    }

    void tick(ServerLevel level, EchoEntity echo) {
        BlockPos anchor = this.job.buildAnchor;
        if (anchor == null) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.NO_BLUEPRINT));
            return;
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
                this.plan = this.job.plan();
                this.job.motion.phase = JobMotion.Phase.SELECT;
            }
            case SELECT -> this.selectBuildTarget(level, echo);
            case PATH -> this.job.motion.tickPath(level, echo);
            case WALK -> this.job.motion.tickWalk(level, echo);
            case DIG -> this.job.motion.tickDig(level, echo);
            case TO_CHEST -> this.job.chests.atChest(level, echo);
            case WAIT -> {
                if (++this.job.motion.waitTicks % JobLimits.WAIT_POLL == 0 && this.materialsAppeared(level, echo)) {
                    this.job.motion.phase = JobMotion.Phase.SELECT;
                }
            }
            default -> this.job.motion.phase = JobMotion.Phase.SELECT;
        }
    }

    private boolean placedCorrectly(ServerLevel level, EchoLesson.Entry entry) {
        return level.getBlockState(entry.offset()).getBlock() == entry.state().getBlock();
    }

    private void selectBuildTarget(ServerLevel level, EchoEntity echo) {
        int done = 0;
        EchoLesson.Entry pick = null;
        EchoLesson.Entry clear = null;
        EchoLesson.Entry fix = null;
        Map<Item, Integer> remaining = new LinkedHashMap<>();
        boolean anyUnloaded = false;
        for (EchoLesson.Entry entry : this.plan) {
            BlockPos pos = entry.offset();
            if (!level.isLoaded(pos)) {
                anyUnloaded = true;
                continue;
            }
            long key = pos.asLong();
            BlockState wrong = this.job.strain.misfired.get(key);
            if (wrong != null) {
                if (level.getBlockState(pos).getBlock() == wrong.getBlock()) {
                    if (fix == null) {
                        fix = entry;
                    }
                    continue;
                }
                this.job.strain.misfired.remove(key);
            }
            if (this.placedCorrectly(level, entry)) {
                done++;
                continue;
            }
            if (this.blocked.contains(key)) {
                continue;
            }
            BlockState here = level.getBlockState(pos);
            if (!here.isAir() && !here.canBeReplaced()) {
                if (CommonConfig.ECHO_BUILD_CLEARS_TERRAIN.get() && EchoWork.clearableTerrain(here) && EchoWork.safeToBreak(level, echo, pos)) {
                    if (clear == null && !this.skipped.contains(key)) {
                        clear = entry;
                    }
                } else {
                    this.blocked.add(key);
                }
                continue;
            }
            remaining.merge(entry.item(), 1, Integer::sum);
            if (pick == null && !this.skipped.contains(key) && echo.inventory().find(entry.item()) >= 0) {
                pick = entry;
            }
        }
        this.builtCount = done;
        int total = this.plan.size();
        if (anyUnloaded) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.UNLOADED));
            return;
        }
        if (done == total) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.DONE, done, total));
            return;
        }
        if (clear != null && pick == null) {
            pick = clear;
        }
        if (fix != null) {
            pick = fix;
        }
        if (pick == null) {
            if (remaining.isEmpty()) {
                if (!this.blocked.isEmpty()) {
                    this.job.halt(echo, JobStatus.of(JobStatus.Kind.BLOCKED, this.blocked.size(), 0));
                } else {
                    this.job.halt(echo, JobStatus.of(this.job.motion.unreachableInRow > 0 ? JobStatus.Kind.UNREACHABLE : JobStatus.Kind.NO_SUPPORT));
                }
                return;
            }
            // Something is still needed and the echo carries none of it: the chest, or wait.
            Map<Item, Integer> missing = this.missing(level, echo, remaining);
            Container container = this.job.chest == null ? null : EchoWork.container(level, this.job.chest);
            boolean chestHasSome = container != null && remaining.keySet().stream().anyMatch(item -> EchoWork.count(container, item) > 0);
            boolean carriesSome = remaining.keySet().stream().anyMatch(item -> echo.inventory().find(item) >= 0);
            if (chestHasSome && !this.fetching) {
                this.fetching = true;
                this.job.setStatus(JobStatus.of(JobStatus.Kind.FETCH, done, total));
                this.job.chests.goToChest(level, echo);
                return;
            }
            this.fetching = false;
            if (carriesSome) {
                // Only skipped (unsupported or unreachable) spots are left for what it carries.
                this.skipped.clear();
                if (++this.job.motion.unreachableInRow > 3) {
                    this.job.halt(echo, JobStatus.of(JobStatus.Kind.NO_SUPPORT));
                }
                return;
            }
            this.job.release(echo);
            this.job.setStatus(new JobStatus(JobStatus.Kind.WAIT_MISSING, JobStatus.missingDetail(missing.isEmpty() ? remaining : missing), done, total));
            this.job.motion.phase = JobMotion.Phase.WAIT;
            this.job.motion.waitTicks = 0;
            return;
        }
        this.fetching = false;
        this.buildTarget = pick;
        this.job.setStatus(JobStatus.of(JobStatus.Kind.BUILDING, done, total));
        BlockPos goalPos = pick.offset();
        this.job.motion.digFor = pick == fix ? JobMotion.DigFor.FIX : pick == clear ? JobMotion.DigFor.CLEAR : JobMotion.DigFor.TARGET;
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

    /** What is still needed beyond what the echo and its chest hold. */
    private Map<Item, Integer> missing(ServerLevel level, EchoEntity echo, Map<Item, Integer> remaining) {
        Container container = this.job.chest == null ? null : EchoWork.container(level, this.job.chest);
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : remaining.entrySet()) {
            int have = JobTexts.count(echo.inventory(), entry.getKey()) + (container == null ? 0 : EchoWork.count(container, entry.getKey()));
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - have);
            }
        }
        return missing;
    }

    private boolean materialsAppeared(ServerLevel level, EchoEntity echo) {
        Map<Item, Integer> remaining = new LinkedHashMap<>();
        for (EchoLesson.Entry entry : this.plan) {
            if (level.isLoaded(entry.offset()) && !this.placedCorrectly(level, entry)) {
                remaining.merge(entry.item(), 1, Integer::sum);
            }
        }
        if (remaining.isEmpty()) {
            return true;
        }
        for (Item item : remaining.keySet()) {
            if (echo.inventory().find(item) >= 0) {
                return true;
            }
        }
        Container container = this.job.chest == null ? null : EchoWork.container(level, this.job.chest);
        if (container != null) {
            for (Item item : remaining.keySet()) {
                if (EchoWork.count(container, item) > 0) {
                    return true;
                }
            }
        }
        // Refresh the missing list (someone may have taken or added other blocks).
        Map<Item, Integer> missing = this.missing(level, echo, remaining);
        this.job.setStatus(new JobStatus(JobStatus.Kind.WAIT_MISSING, JobStatus.missingDetail(missing.isEmpty() ? remaining : missing), this.builtCount, this.plan.size()));
        return false;
    }

    void placeBuildTarget(ServerLevel level, EchoEntity echo) {
        EchoLesson.Entry entry = this.buildTarget;
        this.buildTarget = null;
        if (entry == null) {
            this.job.motion.phase = JobMotion.Phase.SELECT;
            return;
        }
        BlockPos pos = entry.offset();
        if (this.job.strain.misfire(echo)) {
            if (this.job.strain.misfires++ % 2 == 1 && this.placeWrong(level, echo, entry)) {
                this.job.strain.misfireNotice(echo, "wrong", pos);
                this.job.motion.placeCooldown = JobLimits.PLACE_INTERVAL;
            } else {
                echo.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.job.strain.misfireNotice(echo, "skip", pos);
                this.job.motion.placeCooldown = 20;
            }
            this.job.motion.phase = JobMotion.Phase.SELECT;
            return;
        }
        EchoHands.Outcome outcome = EchoHands.placeForJob(level, echo, pos, entry.state());
        switch (outcome) {
            case DONE -> {
                this.job.motion.unreachableInRow = 0;
                this.skipped.clear();
                this.builtCount++;
                this.job.setStatus(JobStatus.of(JobStatus.Kind.BUILDING, this.builtCount, this.plan.size()));
                this.job.motion.placeCooldown = JobLimits.PLACE_INTERVAL;
                this.job.mimic.afterPlace(pos, entry.state());
                this.job.strain.onWorkAction(level, echo, pos);
            }
            case SKIPPED_CHANGED -> this.skipped.add(pos.asLong());
            case REFUSED -> {
                int failures = this.placeFailures.merge(pos.asLong(), 1, Integer::sum);
                if (failures >= 2) {
                    this.blocked.add(pos.asLong());
                } else {
                    this.skipped.add(pos.asLong());
                }
            }
            default -> {
            }
        }
        this.job.motion.phase = JobMotion.Phase.SELECT;
    }

    /**
     * Puts another block of the blueprint that the echo carries at {@code entry}'s spot instead of the right one. The
     * spot is remembered and fixed first on the next pick: the wrong block goes back into the echo (exactly one item),
     * then the right one is placed. Only simple blocks, and never next to fluids, so the take-back cannot be refused.
     */
    private boolean placeWrong(ServerLevel level, EchoEntity echo, EchoLesson.Entry entry) {
        BlockPos pos = entry.offset();
        for (Direction side : Direction.values()) {
            if (!level.getFluidState(pos.relative(side)).isEmpty()) {
                return false;
            }
        }
        Set<Item> tried = new HashSet<>();
        tried.add(entry.item());
        for (EchoLesson.Entry other : this.plan) {
            Item item = other.item();
            if (!tried.add(item) || echo.inventory().find(item) < 0) {
                continue;
            }
            BlockState wrong = other.state();
            if (wrong.hasBlockEntity() || !simpleBlock(wrong) || !wrong.canSurvive(level, pos)) {
                continue;
            }
            if (EchoHands.placeForJob(level, echo, pos, wrong) == EchoHands.Outcome.DONE) {
                this.job.strain.misfired.put(pos.asLong(), level.getBlockState(pos));
                return true;
            }
            return false;
        }
        return false;
    }

    private static boolean simpleBlock(BlockState state) {
        return !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !state.hasProperty(BlockStateProperties.BED_PART)
                && !state.hasProperty(BlockStateProperties.CHEST_TYPE);
    }

    /** Items still needed for blocks that are not placed and not blocked. Used when the echo is at its chest. */
    Map<Item, Integer> wantedFromChest(ServerLevel level) {
        Map<Item, Integer> wanted = new LinkedHashMap<>();
        for (EchoLesson.Entry entry : this.plan) {
            if (level.isLoaded(entry.offset()) && !this.placedCorrectly(level, entry) && !this.blocked.contains(entry.offset().asLong())) {
                wanted.merge(entry.item(), 1, Integer::sum);
            }
        }
        return wanted;
    }
}
