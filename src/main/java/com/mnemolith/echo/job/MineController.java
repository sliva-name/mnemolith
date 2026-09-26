package com.mnemolith.echo.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.entity.echo.EchoEntity;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Mining scan, target pick, and the extra break a misfire may take beside an ore.
 * <p>
 * The scan keeps the {@link JobLimits#CANDIDATE_CAP} targets <em>nearest to the work point</em>, not the first ones it
 * reads. A section is read in storage order (y is the slowest axis), so for a common block such as stone or dirt the
 * first 256 hits are the bottom layer of one section: a flat sheet that can lie several blocks under the echo, or out
 * of reach, while the blocks beside and below it are never picked. When the cap left targets out, the next scan starts
 * once these are used up, so the echo works outward from the work point, down included.
 */
final class MineController {
    private final EchoJob job;
    final Set<Block> targets = new HashSet<>();
    final List<Long> sections = new ArrayList<>();
    /** Squared distance from the work point to the nearest cell of each section in {@link #sections} (same order). */
    private final List<Long> sectionReach = new ArrayList<>();
    int sectionIndex;
    int localIndex;
    boolean scanDone;
    /** The last scan had more targets in the radius than the cap; scan again when the candidates run out. */
    boolean truncated;
    final List<BlockPos> candidates = new ArrayList<>();
    /** The nearest targets found so far in this scan, farthest on top. */
    private final PriorityQueue<Found> nearest = new PriorityQueue<>(Comparator.comparingLong(Found::distance).reversed());
    final LongOpenHashSet refused = new LongOpenHashSet();
    @Nullable BlockPos target;
    @Nullable BlockState targetState;

    private record Found(long pos, long distance) {}

    MineController(EchoJob job) {
        this.job = job;
    }

    void resetSearch() {
        this.candidates.clear();
        this.sections.clear();
        this.sectionReach.clear();
        this.nearest.clear();
        this.truncated = false;
        this.refused.clear();
        this.target = null;
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
        switch (this.job.motion.phase) {
            case START -> {
                this.buildTargets();
                this.beginScan(level, anchor);
                this.job.motion.phase = JobMotion.Phase.SCAN;
            }
            case SCAN -> {
                this.scan(level, CommonConfig.ECHO_SCAN_BUDGET.get());
                if (this.scanDone) {
                    this.job.motion.phase = JobMotion.Phase.SELECT;
                }
            }
            case SELECT -> this.selectMiningTarget(level, echo);
            case PATH -> this.job.motion.tickPath(level, echo);
            case WALK -> this.job.motion.tickWalk(level, echo);
            case DIG -> this.job.motion.tickDig(level, echo);
            case TO_CHEST -> this.job.chests.atChest(level, echo);
            default -> this.job.motion.phase = JobMotion.Phase.SELECT;
        }
    }

    /** The taught block types, plus the other variants of the same ore ({@code c:ores/<name>}), e.g. deepslate iron ore for iron ore. */
    private void buildTargets() {
        this.targets.clear();
        for (EchoLesson.MineTarget mineTarget : this.job.lesson.mining()) {
            Block block = mineTarget.block();
            this.targets.add(block);
            block.builtInRegistryHolder().tags()
                    .filter(tag -> tag.location().getNamespace().equals("c") && tag.location().getPath().startsWith("ores/"))
                    .forEach(tag -> addTag(this.targets, tag));
        }
    }

    private static void addTag(Set<Block> into, TagKey<Block> tag) {
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagOrEmpty(tag).forEach(holder -> into.add(holder.value()));
    }

    boolean isTarget(BlockState state) {
        return this.targets.contains(state.getBlock());
    }

    private void beginScan(ServerLevel level, BlockPos anchor) {
        this.sections.clear();
        this.sectionReach.clear();
        this.candidates.clear();
        this.nearest.clear();
        this.truncated = false;
        this.sectionIndex = 0;
        this.localIndex = 0;
        this.scanDone = false;
        int r = this.job.radius();
        int minY = Math.max(level.getMinY(), anchor.getY() - r);
        int maxY = Math.min(level.getMaxY(), anchor.getY() + r);
        List<long[]> ordered = new ArrayList<>();
        for (int sx = SectionPos.blockToSectionCoord(anchor.getX() - r); sx <= SectionPos.blockToSectionCoord(anchor.getX() + r); sx++) {
            for (int sz = SectionPos.blockToSectionCoord(anchor.getZ() - r); sz <= SectionPos.blockToSectionCoord(anchor.getZ() + r); sz++) {
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    long dx = gap(anchor.getX(), sx);
                    long dy = gap(anchor.getY(), sy);
                    long dz = gap(anchor.getZ(), sz);
                    ordered.add(new long[] {SectionPos.asLong(sx, sy, sz), dx * dx + dy * dy + dz * dz});
                }
            }
        }
        // Nearest section box first, so the scan can stop as soon as no section left could hold a nearer target.
        ordered.sort((a, b) -> Long.compare(a[1], b[1]));
        for (long[] entry : ordered) {
            this.sections.add(entry[0]);
            this.sectionReach.add(entry[1]);
        }
    }

    /** Distance along one axis from {@code coord} to the nearest cell of section {@code section} (0 inside it). */
    private static long gap(int coord, int section) {
        int min = section << 4;
        int max = min + 15;
        return coord < min ? min - coord : coord > max ? coord - max : 0;
    }

    private static long distance(BlockPos anchor, int x, int y, int z) {
        long dx = x - anchor.getX();
        long dy = y - anchor.getY();
        long dz = z - anchor.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Keeps {@code pos} if it is among the {@link JobLimits#CANDIDATE_CAP} nearest found so far. */
    private void offer(long pos, long distance) {
        if (this.nearest.size() < JobLimits.CANDIDATE_CAP) {
            this.nearest.add(new Found(pos, distance));
            return;
        }
        this.truncated = true;
        Found farthest = this.nearest.peek();
        if (farthest != null && distance < farthest.distance()) {
            this.nearest.poll();
            this.nearest.add(new Found(pos, distance));
        }
    }

    private void finishScan() {
        this.scanDone = true;
        this.candidates.clear();
        List<Found> found = new ArrayList<>(this.nearest);
        found.sort(Comparator.comparingLong(Found::distance));
        for (Found entry : found) {
            this.candidates.add(BlockPos.of(entry.pos()));
        }
        this.nearest.clear();
    }

    /** Reads up to {@code budget} positions. Sections whose palette cannot contain a target cost one read. */
    private void scan(ServerLevel level, int budget) {
        BlockPos anchor = this.job.workAnchor;
        if (anchor == null) {
            this.finishScan();
            return;
        }
        int r = this.job.radius();
        int work = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (work < budget && this.sectionIndex < this.sections.size()) {
            if (this.localIndex == 0 && this.nearest.size() >= JobLimits.CANDIDATE_CAP) {
                Found farthest = this.nearest.peek();
                if (farthest != null && this.sectionReach.get(this.sectionIndex) >= farthest.distance()) {
                    // Sections are sorted by their nearest cell: none left can hold a nearer target. The rest waits for the next scan.
                    this.truncated = true;
                    this.sectionIndex = this.sections.size();
                    break;
                }
            }
            long key = this.sections.get(this.sectionIndex);
            int sx = SectionPos.x(key);
            int sy = SectionPos.y(key);
            int sz = SectionPos.z(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
            int sectionIdx = level.getSectionIndexFromSectionY(sy);
            if (chunk == null || sectionIdx < 0 || sectionIdx >= chunk.getSectionsCount()) {
                this.nextSection();
                work++;
                continue;
            }
            LevelChunkSection section = chunk.getSection(sectionIdx);
            if (this.localIndex == 0 && (section.hasOnlyAir() || !section.maybeHas(this::isTarget))) {
                this.nextSection();
                work++;
                continue;
            }
            while (this.localIndex < 4096 && work < budget) {
                int i = this.localIndex++;
                work++;
                int lx = i & 15;
                int lz = (i >> 4) & 15;
                int ly = i >> 8;
                BlockState state = section.getBlockState(lx, ly, lz);
                if (!this.isTarget(state)) {
                    continue;
                }
                cursor.set((sx << 4) + lx, (sy << 4) + ly, (sz << 4) + lz);
                if (Math.abs(cursor.getX() - anchor.getX()) > r || Math.abs(cursor.getY() - anchor.getY()) > r || Math.abs(cursor.getZ() - anchor.getZ()) > r) {
                    continue;
                }
                if (!this.refused.contains(cursor.asLong())) {
                    this.offer(cursor.asLong(), distance(anchor, cursor.getX(), cursor.getY(), cursor.getZ()));
                }
            }
            if (this.localIndex >= 4096) {
                this.nextSection();
            }
        }
        if (this.sectionIndex >= this.sections.size()) {
            this.finishScan();
        }
    }

    private void nextSection() {
        this.sectionIndex++;
        this.localIndex = 0;
    }

    private void selectMiningTarget(ServerLevel level, EchoEntity echo) {
        if (this.job.chests.needsDropOff(echo)) {
            if (this.job.chest == null) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.INVENTORY_FULL));
            } else {
                this.job.chests.goToChest(level, echo);
            }
            return;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        var iterator = this.candidates.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (this.refused.contains(pos.asLong()) || !level.isLoaded(pos) || !this.isTarget(level.getBlockState(pos))) {
                iterator.remove();
                continue;
            }
            double distance = pos.distToCenterSqr(echo.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        if (best != null && this.job.motion.unreachableInRow >= JobLimits.UNREACHABLE_GIVE_UP) {
            // The nearest targets in a row could not be reached; the rest are farther away. Stop instead of searching on.
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.UNREACHABLE));
            return;
        }
        if (best == null) {
            if (!this.scanDone) {
                this.job.motion.phase = JobMotion.Phase.SCAN;
                return;
            }
            BlockPos anchor = this.job.workAnchor;
            if (this.truncated && anchor != null) {
                // The cap left farther targets out: scan again (refused positions stay refused).
                this.beginScan(level, anchor);
                this.job.motion.phase = JobMotion.Phase.SCAN;
                return;
            }
            if (this.job.motion.unreachableInRow > 0) {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.UNREACHABLE));
            } else {
                this.job.halt(echo, JobStatus.of(JobStatus.Kind.NOTHING_LEFT));
            }
            return;
        }
        BlockState state = level.getBlockState(best);
        if (!EchoWork.safeToBreak(level, echo, best)) {
            this.refused.add(best.asLong());
            this.candidates.remove(best);
            return;
        }
        int tool = EchoWork.bestTool(echo.inventory(), state);
        if (tool < 0 && EchoWork.needsTool(state)) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.NO_TOOL, EchoWork.toolKind(state)));
            return;
        }
        this.target = best;
        this.targetState = state;
        this.job.setStatus(new JobStatus(JobStatus.Kind.MINING, JobTexts.key(state.getBlock()), this.job.mined, 0));
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
        }, (pos, s) -> !pos.equals(goalPos) && EchoWork.canTunnel(level, echo, pos, s), CommonConfig.ECHO_TUNNEL_MAX.get(), false);
    }

    /** A miner's misfire: also breaks one natural block next to the target (drops go to the echo as usual). */
    boolean breakExtra(ServerLevel level, EchoEntity echo, BlockPos target) {
        BlockPos feet = echo.blockPosition();
        for (Direction side : Direction.values()) {
            BlockPos pos = target.relative(side);
            if (pos.equals(feet) || pos.equals(feet.above()) || pos.equals(feet.below()) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || this.isTarget(state) || !EchoWork.canTunnel(level, echo, pos, state) || !EchoWork.safeToBreak(level, echo, pos)) {
                continue;
            }
            int tool = EchoWork.bestTool(echo.inventory(), state);
            if (tool < 0 && EchoWork.needsTool(state)) {
                continue;
            }
            return EchoHands.breakForJob(level, echo, pos, tool).outcome() == EchoHands.Outcome.DONE;
        }
        return false;
    }
}
