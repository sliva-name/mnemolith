package com.mnemolith.echo.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.EchoHands;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.echo.EchoInventory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The job of one echo: what it was taught, where it works, and a small state machine that runs one step per tick.
 * Only the settings and progress are saved; searches and paths are rebuilt after a load, so a job resumes after a
 * relog or a restart.
 * <p>
 * Cost per tick is bounded: the target scan reads at most {@code echoScanBudget} positions (sections whose palette
 * cannot hold a target are skipped whole), a path search expands at most {@code echoPathBudget} nodes, and a waiting
 * builder polls its materials every 40 ticks.
 */
public final class EchoJob {
    public enum Mode implements StringRepresentable {
        IDLE,
        REPLAY,
        MINE,
        BUILD;

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Mode byId(int id) {
            Mode[] values = values();
            return id >= 0 && id < values.length ? values[id] : IDLE;
        }
    }

    /** The saved part of a job. */
    public record Saved(Mode mode, EchoLesson lesson, Optional<BlockPos> workAnchor, int radius, Optional<BlockPos> chest, Optional<BlockPos> buildAnchor,
            Rotation rotation, int mined, JobStatus status, Stage3 stage3) {
        public static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Mode.CODEC.optionalFieldOf("mode", Mode.IDLE).forGetter(Saved::mode),
                EchoLesson.CODEC.optionalFieldOf("lesson", EchoLesson.NONE).forGetter(Saved::lesson),
                BlockPos.CODEC.optionalFieldOf("work_anchor").forGetter(Saved::workAnchor),
                Codec.INT.optionalFieldOf("radius", 16).forGetter(Saved::radius),
                BlockPos.CODEC.optionalFieldOf("chest").forGetter(Saved::chest),
                BlockPos.CODEC.optionalFieldOf("build_anchor").forGetter(Saved::buildAnchor),
                Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(Saved::rotation),
                Codec.INT.optionalFieldOf("mined", 0).forGetter(Saved::mined),
                JobStatus.CODEC.optionalFieldOf("status", JobStatus.IDLE).forGetter(Saved::status),
                Stage3.CODEC.optionalFieldOf("stage3", Stage3.EMPTY).forGetter(Saved::stage3))
                .apply(instance, Saved::new));
    }

    /** A block placed by a misfire, to be taken back and replaced with the right one. */
    public record Misfire(BlockPos pos, BlockState state) {
        public static final Codec<Misfire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Misfire::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Misfire::state))
                .apply(instance, Misfire::new));
    }

    /** Stage 3 job state, saved in one optional field so older saves load unchanged. */
    public record Stage3(List<Misfire> misfired, int workActions) {
        public static final Stage3 EMPTY = new Stage3(List.of(), 0);
        public static final Codec<Stage3> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Misfire.CODEC.listOf().optionalFieldOf("misfired", List.of()).forGetter(Stage3::misfired),
                Codec.INT.optionalFieldOf("work_actions", 0).forGetter(Stage3::workActions))
                .apply(instance, Stage3::new));
    }

    private enum Phase {
        START,
        SCAN,
        SELECT,
        PATH,
        WALK,
        DIG,
        TO_CHEST,
        WAIT
    }

    private enum DigFor {
        TUNNEL,
        TARGET,
        CLEAR,
        /** Stage 3: take back a misfired block, then place the right one. */
        FIX
    }

    private static final int CANDIDATE_CAP = 256;
    private static final int UNREACHABLE_GIVE_UP = 12;
    private static final int WAIT_POLL = 40;
    /** Ticks between two placed blocks: the same pace as a player's right-click delay. */
    private static final int PLACE_INTERVAL = 4;
    private static final int STUCK_TICKS = 50;
    private static final double CHEST_REACH = 4.0D;

    // ---- saved ----
    private Mode mode = Mode.IDLE;
    private EchoLesson lesson = EchoLesson.NONE;
    private @Nullable BlockPos workAnchor;
    private int radius = -1;
    private @Nullable BlockPos chest;
    private @Nullable BlockPos buildAnchor;
    private Rotation rotation = Rotation.NONE;
    private int mined;
    private JobStatus status = JobStatus.IDLE;

    // ---- transient ----
    private Phase phase = Phase.START;
    private int waitTicks;
    private int placeCooldown;
    // mining scan
    private final Set<Block> targets = new HashSet<>();
    private final List<Long> sections = new ArrayList<>();
    private int sectionIndex;
    private int localIndex;
    private boolean scanDone;
    private final List<BlockPos> candidates = new ArrayList<>();
    private final LongOpenHashSet refused = new LongOpenHashSet();
    private int unreachableInRow;
    private @Nullable BlockPos target;
    private @Nullable BlockState targetState;
    // building
    private List<EchoLesson.Entry> plan = List.of();
    private final LongOpenHashSet blocked = new LongOpenHashSet();
    private final LongOpenHashSet skipped = new LongOpenHashSet();
    private final Map<Long, Integer> placeFailures = new LinkedHashMap<>();
    private int builtCount;
    private EchoLesson.@Nullable Entry buildTarget;
    private boolean fetching;
    // path
    private EchoNav.@Nullable Search search;
    private List<EchoNav.Step> path = List.of();
    private int pathIndex;
    private int replans;
    private double bestDistance;
    private int stuckTicks;
    private boolean pathToChest;
    // digging
    private @Nullable BlockPos digPos;
    private DigFor digFor = DigFor.TARGET;
    private int digSlot = -1;
    private int digTicks;
    private int digTotal;
    private boolean dirty = true;
    // ---- stage 3: interruptions (transient) ----
    private final EchoMover mover = new EchoMover();
    /** Running from, or hiding after, an attack. The job keeps its mode and resumes when it is calm again. */
    private boolean alarmed;
    private int calmTicks;
    private @Nullable Vec3 threat;
    /** A short line shown instead of the status (a theft, a mimic, a misfire); the job keeps running under it. */
    private @Nullable JobStatus notice;
    private int noticeTicks;
    // moment replicant mimic
    private int mimicTicks;
    private int mimicLeft;
    private int mimicCount;
    private int mimicPlaced;
    private java.util.@Nullable UUID mimicBy;
    private @Nullable BlockPos undoPos;
    private @Nullable BlockState undoState;
    private int undoDelay;
    private int stumbleTicks;
    // ---- stage 3: pressure from work ----
    private com.mnemolith.pressure.PressureBand strain = com.mnemolith.pressure.PressureBand.CALM;
    private long workChunk = Long.MIN_VALUE;
    private int workActions;
    private int misfires;
    /** Misfired blocks (saved): world position to the wrong state the echo put there. */
    private final Map<Long, BlockState> misfired = new LinkedHashMap<>();
    /** QA: overrides {@code echoMisfireChance} when set. */
    public static @Nullable Double qaMisfireChance;

    public EchoJob() {}

    // ---- settings and state ----

    public Mode mode() {
        return this.mode;
    }

    public EchoLesson lesson() {
        return this.lesson;
    }

    public JobStatus status() {
        return this.status;
    }

    public int radius() {
        return this.radius < 0 ? CommonConfig.ECHO_MINE_RADIUS.get() : this.radius;
    }

    public @Nullable BlockPos chest() {
        return this.chest;
    }

    public @Nullable BlockPos buildAnchor() {
        return this.buildAnchor;
    }

    public Rotation rotation() {
        return this.rotation;
    }

    public @Nullable BlockPos workAnchor() {
        return this.workAnchor;
    }

    public int mined() {
        return this.mined;
    }

    public int built() {
        return this.builtCount;
    }

    public int planSize() {
        return this.plan.size();
    }

    /** True once since the last call when anything the client shows (mode, status, radius, chest, anchor, lesson) changed. */
    public boolean consumeDirty() {
        boolean was = this.dirty;
        this.dirty = false;
        return was;
    }

    public void setLesson(EchoLesson lesson) {
        this.lesson = lesson;
        this.buildAnchor = null;
        this.misfired.clear();
        this.dirty = true;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(2, Math.min(CommonConfig.ECHO_MINE_MAX_RADIUS.get(), radius));
        if (this.mode == Mode.MINE) {
            this.restartPhase();
        }
        this.dirty = true;
    }

    public void setChest(@Nullable BlockPos chest) {
        this.chest = chest == null ? null : chest.immutable();
        this.dirty = true;
    }

    public void setBlueprintAnchor(BlockPos anchor, Rotation rotation) {
        this.misfired.clear();
        this.buildAnchor = anchor.immutable();
        this.rotation = rotation;
        this.dirty = true;
    }

    public void clearBlueprintAnchor() {
        this.misfired.clear();
        this.buildAnchor = null;
        this.dirty = true;
    }

    public void setStatus(JobStatus status) {
        if (!status.equals(this.status)) {
            this.status = status;
            this.dirty = true;
        }
    }

    /** What the label shows: a live notice over the status, when there is one. */
    public JobStatus shownStatus() {
        return this.notice != null && this.noticeTicks > 0 ? this.notice : this.status;
    }

    /** Shows {@code line} over the status for {@code ticks} ticks. */
    public void notice(JobStatus line, int ticks) {
        this.notice = line;
        this.noticeTicks = Math.max(1, ticks);
        this.dirty = true;
    }

    /** A job that works in the world: mobs may hunt it, the replicant may mimic it, the work writes imprints. */
    public boolean isWorking() {
        return this.mode == Mode.MINE || this.mode == Mode.BUILD;
    }

    /** Memory band of the chunk the echo works in (updated once a second while it works). */
    public com.mnemolith.pressure.PressureBand strain() {
        return this.strain;
    }

    public int misfiredCount() {
        return this.misfired.size();
    }

    public boolean alarmed() {
        return this.alarmed;
    }

    public boolean mimicked() {
        return this.mimicTicks > 0;
    }

    public int mimicUndone() {
        return this.mimicCount;
    }

    /** Clears every stage 3 interruption (attack alarm, mimic, notices) when the job changes or stops. */
    private void clearInterruptions(EchoEntity echo) {
        if (this.alarmed || this.mover.active()) {
            if (echo.level() instanceof ServerLevel level) {
                this.mover.stop(level, echo);
            }
        }
        this.alarmed = false;
        this.threat = null;
        this.calmTicks = 0;
        this.mimicTicks = 0;
        this.mimicLeft = 0;
        this.mimicBy = null;
        this.undoPos = null;
        this.undoState = null;
        this.stumbleTicks = 0;
        if (this.notice != null) {
            this.notice = null;
            this.noticeTicks = 0;
            this.dirty = true;
        }
    }

    /** Replay of the recording started (stage 1 behaviour); the job waits until it ends. */
    public void beginReplay() {
        this.alarmed = false;
        this.mimicTicks = 0;
        this.undoPos = null;
        this.notice = null;
        this.mode = Mode.REPLAY;
        this.setStatus(JobStatus.REPLAY);
        this.restartPhase();
        this.dirty = true;
    }

    public void stop(EchoEntity echo) {
        boolean wasBuilding = this.mode == Mode.BUILD;
        this.clearInterruptions(echo);
        this.mode = Mode.IDLE;
        this.setStatus(JobStatus.IDLE);
        this.release(echo);
        this.dirty = true;
        if (wasBuilding) {
            echo.onBuildFinished();
        }
    }

    /** Starts mining around the echo. Returns false (with a stop status) when there is nothing to mine with. */
    public boolean startMining(EchoEntity echo) {
        this.clearInterruptions(echo);
        this.release(echo);
        if (!this.lesson.teachesMining()) {
            this.mode = Mode.IDLE;
            this.setStatus(JobStatus.of(JobStatus.Kind.NO_LESSON));
            return false;
        }
        this.mode = Mode.MINE;
        this.workAnchor = echo.blockPosition();
        this.mined = 0;
        this.restartPhase();
        this.setStatus(new JobStatus(JobStatus.Kind.MINING, key(this.lesson.mining().get(0).block()), 0, 0));
        this.dirty = true;
        return true;
    }

    public boolean startBuilding(EchoEntity echo) {
        this.clearInterruptions(echo);
        this.release(echo);
        if (!this.lesson.teachesBuilding()) {
            this.mode = Mode.IDLE;
            this.setStatus(JobStatus.of(JobStatus.Kind.NO_LESSON));
            return false;
        }
        if (this.buildAnchor == null) {
            this.mode = Mode.IDLE;
            this.setStatus(JobStatus.of(JobStatus.Kind.NO_BLUEPRINT));
            return false;
        }
        this.mode = Mode.BUILD;
        this.restartPhase();
        this.setStatus(JobStatus.of(JobStatus.Kind.BUILDING, 0, this.lesson.blueprint().map(EchoLesson.Blueprint::size).orElse(0)));
        this.dirty = true;
        // The owner sees the pink ghost of what is still missing for as long as the build runs.
        echo.sendGhostToOwner();
        return true;
    }

    /** World positions and states the build places (rotated, bottom-up). Empty when no blueprint is placed. */
    public List<EchoLesson.Entry> plan() {
        if (this.buildAnchor == null || this.lesson.blueprint().isEmpty()) {
            return List.of();
        }
        return this.lesson.blueprint().get().placed(this.buildAnchor, this.rotation);
    }

    private void restartPhase() {
        this.phase = Phase.START;
        this.search = null;
        this.path = List.of();
        this.pathIndex = 0;
        this.candidates.clear();
        this.sections.clear();
        this.refused.clear();
        this.blocked.clear();
        this.skipped.clear();
        this.placeFailures.clear();
        this.target = null;
        this.buildTarget = null;
        this.digPos = null;
        this.unreachableInRow = 0;
        this.replans = 0;
        this.waitTicks = 0;
    }

    /** Stops moving and clears a crack overlay. */
    public void release(EchoEntity echo) {
        echo.setMoveTarget(null);
        if (this.digPos != null && echo.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(echo.getId(), this.digPos, -1);
        }
        this.digPos = null;
        this.search = null;
        this.path = List.of();
    }

    private void halt(EchoEntity echo, JobStatus why) {
        boolean wasBuilding = this.mode == Mode.BUILD;
        this.clearInterruptions(echo);
        this.release(echo);
        this.mode = Mode.IDLE;
        if (wasBuilding) {
            echo.onBuildFinished();
        }
        this.setStatus(why);
        this.dirty = true;
        Mnemolith.LOGGER.info("Mnemolith echo job stopped owner={} status={} detail={} a={} b={}", echo.ownerName(), why.kind().getSerializedName(), why.detail(), why.a(), why.b());
    }

    // ---- save ----

    public Saved save() {
        return new Saved(this.mode, this.lesson, Optional.ofNullable(this.workAnchor), this.radius(), Optional.ofNullable(this.chest), Optional.ofNullable(this.buildAnchor),
                this.rotation, this.mined, this.status, this.saveStage3());
    }

    private Stage3 saveStage3() {
        List<Misfire> list = new ArrayList<>();
        for (Map.Entry<Long, BlockState> entry : this.misfired.entrySet()) {
            list.add(new Misfire(BlockPos.of(entry.getKey()), entry.getValue()));
        }
        return new Stage3(list, this.workActions);
    }

    public void load(Saved saved) {
        this.mode = saved.mode() == Mode.REPLAY ? Mode.IDLE : saved.mode();
        this.lesson = saved.lesson();
        this.workAnchor = saved.workAnchor().orElse(null);
        this.radius = saved.radius();
        this.chest = saved.chest().orElse(null);
        this.buildAnchor = saved.buildAnchor().orElse(null);
        this.rotation = saved.rotation();
        this.mined = saved.mined();
        this.status = saved.status();
        this.misfired.clear();
        for (Misfire misfire : saved.stage3().misfired()) {
            this.misfired.put(misfire.pos().asLong(), misfire.state());
        }
        this.workActions = saved.stage3().workActions();
        this.restartPhase();
        this.dirty = true;
    }

    // ---- tick ----

    public void tick(ServerLevel level, EchoEntity echo) {
        if (this.noticeTicks > 0 && --this.noticeTicks == 0) {
            this.notice = null;
            this.dirty = true;
        }
        if (this.mimicTicks > 0) {
            this.tickMimic(level, echo);
        }
        if (echo.tickCount % 20 == 3) {
            this.refreshStrain(level, echo);
            if (!this.isWorking()) {
                return;
            }
        }
        if (this.alarmed) {
            this.tickAlarm(level, echo);
            return;
        }
        if (this.stumbleTicks > 0) {
            this.stumbleTicks--;
            echo.setMoveTarget(null);
            return;
        }
        switch (this.mode) {
            case MINE -> this.tickMining(level, echo);
            case BUILD -> this.tickBuilding(level, echo);
            case REPLAY -> {
                if (!echo.isReplaying()) {
                    this.mode = Mode.IDLE;
                    this.setStatus(JobStatus.IDLE);
                }
            }
            default -> {
            }
        }
    }

    // ================= attacks (stage 3) =================

    /**
     * A hostile mob hurt the echo. A working echo drops what it was doing, runs {@code echoFleeDistance} blocks away
     * from the attacker and waits; it never hits back. The job keeps its mode, so it resumes after a calm spell.
     */
    public void onAttacked(ServerLevel level, EchoEntity echo, net.minecraft.world.entity.LivingEntity attacker) {
        if (!this.isWorking()) {
            return;
        }
        this.calmTicks = 0;
        this.threat = attacker.position();
        if (!this.alarmed) {
            this.alarmed = true;
            this.release(echo);
            this.stumbleTicks = 0;
            this.setStatus(JobStatus.of(JobStatus.Kind.ATTACKED));
            Mnemolith.LOGGER.info("Mnemolith echo attacked owner={} by={} at {}", echo.ownerName(),
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()), echo.blockPosition().toShortString());
            this.flee(level, echo);
        } else if (!this.mover.active()) {
            this.flee(level, echo);
        }
    }

    private void flee(ServerLevel level, EchoEntity echo) {
        Vec3 from = this.threat == null ? echo.position() : this.threat;
        BlockPos origin = BlockPos.containing(from);
        int distance = CommonConfig.ECHO_FLEE_DISTANCE.get();
        this.mover.start(level, echo, new EchoNav.Goal() {
            @Override
            public boolean reached(BlockPos feet) {
                return feet.distSqr(origin) >= (double) distance * distance;
            }

            @Override
            public double estimate(BlockPos feet) {
                return Math.max(0.0D, distance - Math.sqrt(feet.distSqr(origin)));
            }
        });
    }

    private void tickAlarm(ServerLevel level, EchoEntity echo) {
        this.calmTicks++;
        EchoMover.Result result = this.mover.tick(level, echo);
        if (result == EchoMover.Result.RUNNING) {
            return;
        }
        if (this.calmTicks % 10 != 0) {
            return;
        }
        net.minecraft.world.entity.Mob hunter = hunter(level, echo);
        if (hunter != null) {
            // Still hunted: keep out of its way, and do not count this as calm.
            this.threat = hunter.position();
            if (hunter.distanceToSqr(echo) < 36.0D) {
                this.flee(level, echo);
            }
            this.calmTicks = Math.min(this.calmTicks, CommonConfig.ECHO_FLEE_SAFE_TICKS.get() / 2);
            return;
        }
        if (this.calmTicks >= CommonConfig.ECHO_FLEE_SAFE_TICKS.get()) {
            this.resume(echo);
        }
    }

    /** The nearest mob within 12 blocks that has this echo as its target. */
    public static net.minecraft.world.entity.@Nullable Mob hunter(ServerLevel level, EchoEntity echo) {
        net.minecraft.world.entity.Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, echo.getBoundingBox().inflate(12.0D),
                mob -> mob.isAlive() && mob.getTarget() == echo)) {
            double distance = mob.distanceToSqr(echo);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        return best;
    }

    private void resume(EchoEntity echo) {
        this.alarmed = false;
        this.threat = null;
        this.calmTicks = 0;
        echo.setMoveTarget(null);
        this.restartPhase();
        this.setStatus(this.workingStatus());
        Mnemolith.LOGGER.info("Mnemolith echo resumed owner={} mode={} at {}", echo.ownerName(), this.mode.getSerializedName(), echo.blockPosition().toShortString());
    }

    /** The status a working job shows when it (re)starts its loop. */
    private JobStatus workingStatus() {
        return switch (this.mode) {
            case MINE -> new JobStatus(JobStatus.Kind.MINING, this.lesson.mining().isEmpty() ? "" : key(this.lesson.mining().get(0).block()), this.mined, 0);
            case BUILD -> JobStatus.of(JobStatus.Kind.BUILDING, this.builtCount, this.lesson.blueprint().map(EchoLesson.Blueprint::size).orElse(0));
            default -> JobStatus.IDLE;
        };
    }

    // ================= moment replicant mimic (stage 3) =================

    /**
     * A moment replicant copies this job for {@code ticks}. While it lasts, every other block the builder places is
     * pulled back by the replicant (the block item goes back into the echo, so the builder simply places it again),
     * and a miner stumbles for a moment after a block. At most {@code undoMax} such tricks per mimic.
     */
    public void beginMimic(java.util.UUID replicant, int ticks, int undoMax) {
        if (!this.isWorking() || ticks <= 0) {
            return;
        }
        this.mimicBy = replicant;
        this.mimicTicks = ticks;
        this.mimicLeft = undoMax;
        this.mimicCount = 0;
        this.mimicPlaced = 0;
        this.notice(JobStatus.of(JobStatus.Kind.MIMIC, 0, 0), ticks);
    }

    private void tickMimic(ServerLevel level, EchoEntity echo) {
        this.mimicTicks--;
        net.minecraft.world.entity.Entity by = this.mimicBy == null ? null : level.getEntity(this.mimicBy);
        if (by == null || !by.isAlive() || !this.isWorking()
                || (by instanceof com.mnemolith.entity.mob.MomentReplicant replicant && !replicant.isMimicking(echo))) {
            this.endMimic();
            return;
        }
        BlockPos pos = this.undoPos;
        BlockState state = this.undoState;
        if (pos != null && state != null && --this.undoDelay <= 0) {
            this.undoPos = null;
            this.undoState = null;
            if (EchoHands.takeBack(level, echo, pos, state)) {
                this.mimicLeft--;
                this.mimicCount++;
                if (by instanceof net.minecraft.world.entity.LivingEntity living) {
                    living.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                level.sendParticles(com.mnemolith.particle.ModParticles.REPLICANT_TELEGRAPH.get(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 10, 0.3D, 0.3D, 0.3D, 0.01D);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.6F, 0.6F);
                this.notice(JobStatus.of(JobStatus.Kind.MIMIC, this.mimicCount, 0), Math.max(40, this.mimicTicks));
                Mnemolith.LOGGER.info("Mnemolith replicant undid echo block owner={} at {} undone={}", echo.ownerName(), pos.toShortString(), this.mimicCount);
            }
        }
        if (this.mimicTicks <= 0) {
            this.endMimic();
        }
    }

    private void endMimic() {
        this.mimicTicks = 0;
        this.mimicBy = null;
        this.undoPos = null;
        this.undoState = null;
        if (this.notice != null && this.notice.kind() == JobStatus.Kind.MIMIC) {
            this.noticeTicks = Math.min(this.noticeTicks, 20);
        }
    }

    /** Called after a block the job placed; a mimicking replicant may pull it back a moment later. */
    private void mimicAfterPlace(BlockPos pos, BlockState state) {
        if (this.mimicTicks <= 0 || this.mimicLeft <= 0 || this.undoPos != null) {
            return;
        }
        if (this.mimicPlaced++ % 2 == 0) {
            this.undoPos = pos.immutable();
            this.undoState = state;
            this.undoDelay = 10;
        }
    }

    /** Called after a block the job broke or harvested; a mimicking replicant makes the echo stumble. */
    private void mimicAfterBreak() {
        if (this.mimicTicks <= 0 || this.mimicLeft <= 0) {
            return;
        }
        this.mimicLeft--;
        this.mimicCount++;
        this.stumbleTicks = 30;
        this.notice(JobStatus.of(JobStatus.Kind.MIMIC, this.mimicCount, 0), Math.max(40, this.mimicTicks));
    }

    // ================= pressure from work (stage 3) =================

    /** Reads the band of the echo's chunk; a working echo stops in a fracture. */
    public void refreshStrain(ServerLevel level, EchoEntity echo) {
        com.mnemolith.pressure.PressureBand band = com.mnemolith.pressure.PressureBand.CALM;
        if (this.isWorking()) {
            com.mnemolith.imprint.ChunkMemory memory = com.mnemolith.world.LoadedChunkMemory.existing(level.getChunkAt(echo.blockPosition()));
            band = memory == null ? band : com.mnemolith.pressure.MemoryPressure.band(memory.cachedPressure());
        }
        if (band != this.strain) {
            this.strain = band;
            this.dirty = true;
        }
        if (band == com.mnemolith.pressure.PressureBand.FRACTURE && this.isWorking() && CommonConfig.ECHO_FRACTURE_STOPS.get()) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.FRACTURED));
        }
    }

    /**
     * One finished piece of work (a mined, placed or harvested block). Every {@code echoWorkImprintEvery} pieces in the
     * same chunk the work leaves a build imprint by the owner there, plus {@code echoWorkInstability}; a muted chunk
     * refuses the write, and then no instability is added either.
     */
    private void onWorkAction(ServerLevel level, EchoEntity echo, BlockPos pos) {
        int every = CommonConfig.ECHO_WORK_IMPRINT_EVERY.get();
        if (every <= 0) {
            return;
        }
        long chunk = net.minecraft.world.level.ChunkPos.pack(pos);
        if (chunk != this.workChunk) {
            this.workChunk = chunk;
            this.workActions = 0;
        }
        if (++this.workActions < every) {
            return;
        }
        this.workActions = 0;
        boolean written = com.mnemolith.imprint.ImprintWriter.write(level, pos, List.of(com.mnemolith.imprint.ImprintTag.BUILD), echo.ownerId(), false);
        int instability = CommonConfig.ECHO_WORK_INSTABILITY.get();
        if (written && instability > 0) {
            com.mnemolith.imprint.ImprintWriter.spike(level, pos, instability);
        }
        Mnemolith.LOGGER.info("Mnemolith echo work imprint owner={} at {} written={}", echo.ownerName(), pos.toShortString(), written);
    }

    /** True when this action misfires: only in an overloaded chunk, with {@code echoMisfireChance}. */
    private boolean misfire(EchoEntity echo) {
        if (this.strain != com.mnemolith.pressure.PressureBand.OVERLOADED) {
            return false;
        }
        double chance = qaMisfireChance != null ? qaMisfireChance : CommonConfig.ECHO_MISFIRE_CHANCE.get();
        return chance > 0.0D && echo.getRandom().nextDouble() < chance;
    }

    private void misfireNotice(EchoEntity echo, String kind, BlockPos pos) {
        this.notice(JobStatus.of(JobStatus.Kind.MISFIRE, kind), 60);
        Mnemolith.LOGGER.info("Mnemolith echo misfire owner={} kind={} at {}", echo.ownerName(), kind, pos.toShortString());
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
                this.misfired.put(pos.asLong(), level.getBlockState(pos));
                return true;
            }
            return false;
        }
        return false;
    }

    private static boolean simpleBlock(BlockState state) {
        return !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                && !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.CHEST_TYPE);
    }

    /** A miner's misfire: also breaks one natural block next to the target (drops go to the echo as usual). */
    private boolean breakExtra(ServerLevel level, EchoEntity echo, BlockPos target) {
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

    // ================= mining =================

    private void tickMining(ServerLevel level, EchoEntity echo) {
        BlockPos anchor = this.workAnchor;
        if (anchor == null) {
            anchor = echo.blockPosition();
            this.workAnchor = anchor;
        }
        if (!level.isLoaded(anchor)) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.UNLOADED));
            return;
        }
        switch (this.phase) {
            case START -> {
                this.buildTargets();
                this.beginScan(level, anchor);
                this.phase = Phase.SCAN;
            }
            case SCAN -> {
                this.scan(level, CommonConfig.ECHO_SCAN_BUDGET.get());
                if (this.scanDone || this.candidates.size() >= CANDIDATE_CAP) {
                    this.phase = Phase.SELECT;
                }
            }
            case SELECT -> this.selectMiningTarget(level, echo);
            case PATH -> this.tickPath(level, echo);
            case WALK -> this.tickWalk(level, echo);
            case DIG -> this.tickDig(level, echo);
            case TO_CHEST -> this.atChest(level, echo);
            default -> this.phase = Phase.SELECT;
        }
    }

    /** The taught block types, plus the other variants of the same ore ({@code c:ores/<name>}), e.g. deepslate iron ore for iron ore. */
    private void buildTargets() {
        this.targets.clear();
        for (EchoLesson.MineTarget mineTarget : this.lesson.mining()) {
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

    private boolean isTarget(BlockState state) {
        return this.targets.contains(state.getBlock());
    }

    private void beginScan(ServerLevel level, BlockPos anchor) {
        this.sections.clear();
        this.candidates.clear();
        this.sectionIndex = 0;
        this.localIndex = 0;
        this.scanDone = false;
        int r = this.radius();
        int minY = Math.max(level.getMinY(), anchor.getY() - r);
        int maxY = Math.min(level.getMaxY(), anchor.getY() + r);
        List<long[]> ordered = new ArrayList<>();
        for (int sx = SectionPos.blockToSectionCoord(anchor.getX() - r); sx <= SectionPos.blockToSectionCoord(anchor.getX() + r); sx++) {
            for (int sz = SectionPos.blockToSectionCoord(anchor.getZ() - r); sz <= SectionPos.blockToSectionCoord(anchor.getZ() + r); sz++) {
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    double cx = (sx << 4) + 8 - anchor.getX();
                    double cy = (sy << 4) + 8 - anchor.getY();
                    double cz = (sz << 4) + 8 - anchor.getZ();
                    ordered.add(new long[] {SectionPos.asLong(sx, sy, sz), (long) (cx * cx + cy * cy + cz * cz)});
                }
            }
        }
        ordered.sort((a, b) -> Long.compare(a[1], b[1]));
        for (long[] entry : ordered) {
            this.sections.add(entry[0]);
        }
    }

    /** Reads up to {@code budget} positions. Sections whose palette cannot contain a target cost one read. */
    private void scan(ServerLevel level, int budget) {
        BlockPos anchor = this.workAnchor;
        if (anchor == null) {
            this.scanDone = true;
            return;
        }
        int r = this.radius();
        int work = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (work < budget && this.sectionIndex < this.sections.size() && this.candidates.size() < CANDIDATE_CAP) {
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
                    this.candidates.add(cursor.immutable());
                    if (this.candidates.size() >= CANDIDATE_CAP) {
                        break;
                    }
                }
            }
            if (this.localIndex >= 4096) {
                this.nextSection();
            }
        }
        if (this.sectionIndex >= this.sections.size()) {
            this.scanDone = true;
        }
    }

    private void nextSection() {
        this.sectionIndex++;
        this.localIndex = 0;
    }

    private void selectMiningTarget(ServerLevel level, EchoEntity echo) {
        if (this.needsDropOff(echo)) {
            if (this.chest == null) {
                this.halt(echo, JobStatus.of(JobStatus.Kind.INVENTORY_FULL));
            } else {
                this.goToChest(level, echo);
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
        if (best != null && this.unreachableInRow >= UNREACHABLE_GIVE_UP) {
            // The nearest targets in a row could not be reached; the rest are farther away. Stop instead of searching on.
            this.halt(echo, JobStatus.of(JobStatus.Kind.UNREACHABLE));
            return;
        }
        if (best == null) {
            if (!this.scanDone) {
                this.phase = Phase.SCAN;
                return;
            }
            if (this.unreachableInRow > 0) {
                this.halt(echo, JobStatus.of(JobStatus.Kind.UNREACHABLE));
            } else {
                this.halt(echo, JobStatus.of(JobStatus.Kind.NOTHING_LEFT));
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
            this.halt(echo, JobStatus.of(JobStatus.Kind.NO_TOOL, EchoWork.toolKind(state)));
            return;
        }
        this.target = best;
        this.targetState = state;
        this.setStatus(new JobStatus(JobStatus.Kind.MINING, key(state.getBlock()), this.mined, 0));
        BlockPos goalPos = best;
        this.startPath(level, echo, new EchoNav.Goal() {
            @Override
            public boolean reached(BlockPos feet) {
                return canWorkOn(level, feet, goalPos);
            }

            @Override
            public double estimate(BlockPos feet) {
                return Math.max(0.0D, Math.sqrt(feet.distSqr(goalPos)) - 3.0D);
            }
        }, (pos, s) -> !pos.equals(goalPos) && EchoWork.canTunnel(level, echo, pos, s), CommonConfig.ECHO_TUNNEL_MAX.get(), false);
    }

    /** Standing at {@code feet}, the echo can break or place at {@code pos}: in reach, not inside it, and next to it or in sight of it. */
    static boolean canWorkOn(ServerLevel level, BlockPos feet, BlockPos pos) {
        if (feet.equals(pos) || feet.above().equals(pos)) {
            return false;
        }
        Vec3 eye = new Vec3(feet.getX() + 0.5D, feet.getY() + 1.62D, feet.getZ() + 0.5D);
        Vec3 center = Vec3.atCenterOf(pos);
        if (eye.distanceToSqr(center) > EchoWork.REACH * EchoWork.REACH) {
            return false;
        }
        if (adjacent(feet, pos) || adjacent(feet.above(), pos)) {
            return true;
        }
        BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private static boolean adjacent(BlockPos a, BlockPos b) {
        return a.distManhattan(b) == 1;
    }

    private boolean needsDropOff(EchoEntity echo) {
        return EchoWork.freeMainSlots(echo.inventory()) <= CommonConfig.ECHO_DEPOSIT_FREE_SLOTS.get();
    }

    // ================= building =================

    private void tickBuilding(ServerLevel level, EchoEntity echo) {
        BlockPos anchor = this.buildAnchor;
        if (anchor == null) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.NO_BLUEPRINT));
            return;
        }
        if (!level.isLoaded(anchor)) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.UNLOADED));
            return;
        }
        if (this.placeCooldown > 0) {
            this.placeCooldown--;
            return;
        }
        switch (this.phase) {
            case START -> {
                this.plan = this.plan();
                this.phase = Phase.SELECT;
            }
            case SELECT -> this.selectBuildTarget(level, echo);
            case PATH -> this.tickPath(level, echo);
            case WALK -> this.tickWalk(level, echo);
            case DIG -> this.tickDig(level, echo);
            case TO_CHEST -> this.atChest(level, echo);
            case WAIT -> {
                if (++this.waitTicks % WAIT_POLL == 0 && this.materialsAppeared(level, echo)) {
                    this.phase = Phase.SELECT;
                }
            }
            default -> this.phase = Phase.SELECT;
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
            BlockState wrong = this.misfired.get(key);
            if (wrong != null) {
                if (level.getBlockState(pos).getBlock() == wrong.getBlock()) {
                    if (fix == null) {
                        fix = entry;
                    }
                    continue;
                }
                this.misfired.remove(key);
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
            this.halt(echo, JobStatus.of(JobStatus.Kind.UNLOADED));
            return;
        }
        if (done == total) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.DONE, done, total));
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
                    this.halt(echo, JobStatus.of(JobStatus.Kind.BLOCKED, this.blocked.size(), 0));
                } else {
                    this.halt(echo, JobStatus.of(this.unreachableInRow > 0 ? JobStatus.Kind.UNREACHABLE : JobStatus.Kind.NO_SUPPORT));
                }
                return;
            }
            // Something is still needed and the echo carries none of it: the chest, or wait.
            Map<Item, Integer> missing = this.missing(level, echo, remaining);
            Container container = this.chest == null ? null : EchoWork.container(level, this.chest);
            boolean chestHasSome = container != null && remaining.keySet().stream().anyMatch(item -> EchoWork.count(container, item) > 0);
            boolean carriesSome = remaining.keySet().stream().anyMatch(item -> echo.inventory().find(item) >= 0);
            if (chestHasSome && !this.fetching) {
                this.fetching = true;
                this.setStatus(JobStatus.of(JobStatus.Kind.FETCH, done, total));
                this.goToChest(level, echo);
                return;
            }
            this.fetching = false;
            if (carriesSome) {
                // Only skipped (unsupported or unreachable) spots are left for what it carries.
                this.skipped.clear();
                if (++this.unreachableInRow > 3) {
                    this.halt(echo, JobStatus.of(JobStatus.Kind.NO_SUPPORT));
                }
                return;
            }
            this.release(echo);
            this.setStatus(new JobStatus(JobStatus.Kind.WAIT_MISSING, JobStatus.missingDetail(missing.isEmpty() ? remaining : missing), done, total));
            this.phase = Phase.WAIT;
            this.waitTicks = 0;
            return;
        }
        this.fetching = false;
        this.buildTarget = pick;
        this.setStatus(JobStatus.of(JobStatus.Kind.BUILDING, done, total));
        BlockPos goalPos = pick.offset();
        this.digFor = pick == fix ? DigFor.FIX : pick == clear ? DigFor.CLEAR : DigFor.TARGET;
        this.startPath(level, echo, new EchoNav.Goal() {
            @Override
            public boolean reached(BlockPos feet) {
                return canWorkOn(level, feet, goalPos);
            }

            @Override
            public double estimate(BlockPos feet) {
                return Math.max(0.0D, Math.sqrt(feet.distSqr(goalPos)) - 3.0D);
            }
        }, null, 0, false);
    }

    /** What is still needed beyond what the echo and its chest hold. */
    private Map<Item, Integer> missing(ServerLevel level, EchoEntity echo, Map<Item, Integer> remaining) {
        Container container = this.chest == null ? null : EchoWork.container(level, this.chest);
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : remaining.entrySet()) {
            int have = count(echo.inventory(), entry.getKey()) + (container == null ? 0 : EchoWork.count(container, entry.getKey()));
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
        Container container = this.chest == null ? null : EchoWork.container(level, this.chest);
        if (container != null) {
            for (Item item : remaining.keySet()) {
                if (EchoWork.count(container, item) > 0) {
                    return true;
                }
            }
        }
        // Refresh the missing list (someone may have taken or added other blocks).
        Map<Item, Integer> missing = this.missing(level, echo, remaining);
        this.setStatus(new JobStatus(JobStatus.Kind.WAIT_MISSING, JobStatus.missingDetail(missing.isEmpty() ? remaining : missing), this.builtCount, this.plan.size()));
        return false;
    }

    private void placeBuildTarget(ServerLevel level, EchoEntity echo) {
        EchoLesson.Entry entry = this.buildTarget;
        this.buildTarget = null;
        if (entry == null) {
            this.phase = Phase.SELECT;
            return;
        }
        BlockPos pos = entry.offset();
        if (this.misfire(echo)) {
            if (this.misfires++ % 2 == 1 && this.placeWrong(level, echo, entry)) {
                this.misfireNotice(echo, "wrong", pos);
                this.placeCooldown = PLACE_INTERVAL;
            } else {
                echo.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.misfireNotice(echo, "skip", pos);
                this.placeCooldown = 20;
            }
            this.phase = Phase.SELECT;
            return;
        }
        EchoHands.Outcome outcome = EchoHands.placeForJob(level, echo, pos, entry.state());
        switch (outcome) {
            case DONE -> {
                this.unreachableInRow = 0;
                this.skipped.clear();
                this.builtCount++;
                this.setStatus(JobStatus.of(JobStatus.Kind.BUILDING, this.builtCount, this.plan.size()));
                this.placeCooldown = PLACE_INTERVAL;
                this.mimicAfterPlace(pos, entry.state());
                this.onWorkAction(level, echo, pos);
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
        this.phase = Phase.SELECT;
    }

    // ================= chest =================

    private void goToChest(ServerLevel level, EchoEntity echo) {
        BlockPos chestPos = this.chest;
        if (chestPos == null) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.CHEST_UNAVAILABLE));
            return;
        }
        if (!level.isLoaded(chestPos)) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.UNLOADED));
            return;
        }
        if (this.mode == Mode.MINE) {
            this.setStatus(JobStatus.of(JobStatus.Kind.DEPOSIT, this.mined, 0));
        }
        this.startPath(level, echo, new EchoNav.Goal() {
            @Override
            public boolean reached(BlockPos feet) {
                Vec3 eye = new Vec3(feet.getX() + 0.5D, feet.getY() + 1.62D, feet.getZ() + 0.5D);
                return !feet.equals(chestPos) && eye.distanceToSqr(Vec3.atCenterOf(chestPos)) <= CHEST_REACH * CHEST_REACH;
            }

            @Override
            public double estimate(BlockPos feet) {
                return Math.max(0.0D, Math.sqrt(feet.distSqr(chestPos)) - 3.0D);
            }
        }, null, 0, true);
    }

    private void atChest(ServerLevel level, EchoEntity echo) {
        BlockPos chestPos = this.chest;
        Container container = chestPos == null ? null : EchoWork.container(level, chestPos);
        if (chestPos == null || container == null || !EchoWork.mayOpen(level, echo, chestPos)) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.CHEST_UNAVAILABLE));
            return;
        }
        echo.lookAt(Vec3.atCenterOf(chestPos));
        echo.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.playSound(null, chestPos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        if (this.mode == Mode.MINE) {
            int moved = EchoWork.deposit(echo, container, Map.of());
            Mnemolith.LOGGER.info("Mnemolith echo deposit owner={} moved={} chest={}", echo.ownerName(), moved, chestPos.toShortString());
            if (this.needsDropOff(echo)) {
                this.halt(echo, JobStatus.of(JobStatus.Kind.CHEST_FULL));
                return;
            }
            this.setStatus(new JobStatus(JobStatus.Kind.MINING, this.target == null ? key(this.lesson.mining().get(0).block()) : key(level.getBlockState(this.target).getBlock()), this.mined, 0));
        } else if (this.mode == Mode.BUILD) {
            Map<Item, Integer> wanted = new LinkedHashMap<>();
            for (EchoLesson.Entry entry : this.plan) {
                if (level.isLoaded(entry.offset()) && !this.placedCorrectly(level, entry) && !this.blocked.contains(entry.offset().asLong())) {
                    wanted.merge(entry.item(), 1, Integer::sum);
                }
            }
            for (Map.Entry<Item, Integer> entry : wanted.entrySet()) {
                entry.setValue(Math.max(0, entry.getValue() - count(echo.inventory(), entry.getKey())));
            }
            int taken = EchoWork.take(echo, container, wanted);
            Mnemolith.LOGGER.info("Mnemolith echo fetch owner={} taken={} chest={}", echo.ownerName(), taken, chestPos.toShortString());
        }
        level.playSound(null, chestPos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        this.phase = Phase.SELECT;
    }

    // ================= paths =================

    private void startPath(ServerLevel level, EchoEntity echo, EchoNav.Goal goal, EchoNav.@Nullable Digger digger, int maxDug, boolean toChest) {
        BlockPos start = echo.blockPosition();
        if (goal.reached(start) && echo.onGround()) {
            this.path = List.of();
            this.pathIndex = 0;
            this.pathToChest = toChest;
            this.arrive(level, echo);
            return;
        }
        int budget = CommonConfig.ECHO_PATH_BUDGET.get();
        this.search = new EchoNav.Search(level, start, goal, digger, maxDug, budget * 16);
        this.pathToChest = toChest;
        this.phase = Phase.PATH;
    }

    private void tickPath(ServerLevel level, EchoEntity echo) {
        EchoNav.Search current = this.search;
        if (current == null) {
            this.phase = Phase.SELECT;
            return;
        }
        EchoNav.State state = current.step(CommonConfig.ECHO_PATH_BUDGET.get());
        if (state == EchoNav.State.RUNNING) {
            return;
        }
        this.search = null;
        if (state == EchoNav.State.FAILED) {
            Mnemolith.LOGGER.debug("Mnemolith echo path failed owner={} expanded={}", echo.ownerName(), current.expanded());
            this.onUnreachable(level, echo);
            return;
        }
        this.path = current.path();
        this.pathIndex = 0;
        this.stuckTicks = 0;
        this.bestDistance = Double.MAX_VALUE;
        this.phase = Phase.WALK;
    }

    private void onUnreachable(ServerLevel level, EchoEntity echo) {
        echo.setMoveTarget(null);
        if (this.pathToChest) {
            this.fetching = false;
            this.halt(echo, JobStatus.of(JobStatus.Kind.CHEST_UNAVAILABLE));
            return;
        }
        this.unreachableInRow++;
        if (this.mode == Mode.MINE && this.target != null) {
            this.refused.add(this.target.asLong());
            this.target = null;
        } else if (this.mode == Mode.BUILD && this.buildTarget != null) {
            this.skipped.add(this.buildTarget.offset().asLong());
            this.buildTarget = null;
        }
        this.digFor = DigFor.TARGET;
        this.phase = Phase.SELECT;
    }

    private void tickWalk(ServerLevel level, EchoEntity echo) {
        if (this.pathIndex >= this.path.size()) {
            echo.setMoveTarget(null);
            this.arrive(level, echo);
            return;
        }
        EchoNav.Step step = this.path.get(this.pathIndex);
        if (!level.isLoaded(step.feet())) {
            this.onUnreachable(level, echo);
            return;
        }
        // Clear the cells of this move first (tunnel).
        for (BlockPos cell : step.dig()) {
            int kind = EchoNav.cell(level, cell, null);
            if (kind == EchoNav.OPEN) {
                continue;
            }
            BlockState state = level.getBlockState(cell);
            if (!EchoWork.canTunnel(level, echo, cell, state)) {
                this.replan(level, echo);
                return;
            }
            echo.setMoveTarget(null);
            this.beginDig(level, echo, cell, DigFor.TUNNEL);
            return;
        }
        Vec3 goal = Vec3.atBottomCenterOf(step.feet());
        double dx = goal.x - echo.getX();
        double dz = goal.z - echo.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = goal.y - echo.getY();
        if (horizontal < 0.3D && dy > -0.6D && dy < 0.6D) {
            this.pathIndex++;
            this.stuckTicks = 0;
            this.bestDistance = Double.MAX_VALUE;
            if (this.pathIndex >= this.path.size()) {
                echo.setMoveTarget(null);
                this.arrive(level, echo);
            }
            return;
        }
        echo.setMoveTarget(goal);
        double distance = horizontal + Math.abs(dy) * 0.5D;
        if (distance < this.bestDistance - 0.05D) {
            this.bestDistance = distance;
            this.stuckTicks = 0;
        } else if (++this.stuckTicks > STUCK_TICKS) {
            this.replan(level, echo);
        }
    }

    private void replan(ServerLevel level, EchoEntity echo) {
        echo.setMoveTarget(null);
        if (++this.replans > 3) {
            this.replans = 0;
            this.onUnreachable(level, echo);
            return;
        }
        // Search again from where the echo stands, to the same goal.
        this.phase = Phase.SELECT;
        if (this.pathToChest) {
            this.goToChest(level, echo);
        } else if (this.mode == Mode.MINE && this.target != null) {
            this.candidates.add(0, this.target);
            this.target = null;
        } else if (this.mode == Mode.BUILD) {
            this.buildTarget = null;
        }
    }

    private void arrive(ServerLevel level, EchoEntity echo) {
        echo.setMoveTarget(null);
        this.replans = 0;
        if (this.pathToChest) {
            this.pathToChest = false;
            this.phase = Phase.TO_CHEST;
            return;
        }
        if (this.mode == Mode.MINE) {
            BlockPos pos = this.target;
            if (pos == null) {
                this.phase = Phase.SELECT;
                return;
            }
            this.beginDig(level, echo, pos, DigFor.TARGET);
        } else if (this.mode == Mode.BUILD) {
            EchoLesson.Entry entry = this.buildTarget;
            if (entry == null) {
                this.phase = Phase.SELECT;
                return;
            }
            if (this.digFor == DigFor.CLEAR) {
                this.beginDig(level, echo, entry.offset(), DigFor.CLEAR);
                return;
            }
            if (this.digFor == DigFor.FIX) {
                this.digFor = DigFor.TARGET;
                BlockState wrong = this.misfired.remove(entry.offset().asLong());
                if (wrong != null && !EchoHands.takeBack(level, echo, entry.offset(), wrong)) {
                    // Someone protects or changed it: leave it, the builder reports it as blocked.
                    this.blocked.add(entry.offset().asLong());
                    this.buildTarget = null;
                    this.phase = Phase.SELECT;
                    return;
                }
                this.notice(JobStatus.of(JobStatus.Kind.MISFIRE, "fix"), 40);
            }
            this.placeBuildTarget(level, echo);
        }
    }

    // ================= digging =================

    private void beginDig(ServerLevel level, EchoEntity echo, BlockPos pos, DigFor why) {
        BlockState state = level.getBlockState(pos);
        if (!EchoWork.safeToBreak(level, echo, pos)) {
            this.digRefused(level, echo, pos, why);
            return;
        }
        int tool = EchoWork.bestTool(echo.inventory(), state);
        if (tool < 0 && EchoWork.needsTool(state)) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.NO_TOOL, EchoWork.toolKind(state)));
            return;
        }
        this.digPos = pos.immutable();
        this.digFor = why;
        this.digSlot = tool;
        this.digTicks = 0;
        ItemStack stack = tool >= 0 ? echo.inventory().getItem(tool) : ItemStack.EMPTY;
        this.digTotal = EchoWork.breakTicks(level, pos, state, stack);
        this.phase = Phase.DIG;
        echo.lookAt(Vec3.atCenterOf(pos));
    }

    private void tickDig(ServerLevel level, EchoEntity echo) {
        BlockPos pos = this.digPos;
        if (pos == null) {
            this.phase = Phase.SELECT;
            return;
        }
        echo.setMoveTarget(null);
        echo.lookAt(Vec3.atCenterOf(pos));
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !level.isLoaded(pos)) {
            level.destroyBlockProgress(echo.getId(), pos, -1);
            this.digPos = null;
            this.afterDig(level, echo, pos, false);
            return;
        }
        this.digTicks++;
        if (this.digTicks % 5 == 1) {
            echo.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        level.destroyBlockProgress(echo.getId(), pos, Math.min(9, this.digTicks * 10 / Math.max(1, this.digTotal)));
        if (this.digTicks < this.digTotal) {
            return;
        }
        if (this.digFor == DigFor.TARGET && this.misfire(echo)) {
            if (this.misfires++ % 2 == 1 && this.breakExtra(level, echo, pos)) {
                this.misfireNotice(echo, "extra", pos);
            } else {
                // Fumbled: the dig starts over.
                this.digTicks = 0;
                this.misfireNotice(echo, "skip", pos);
                return;
            }
        }
        level.destroyBlockProgress(echo.getId(), pos, -1);
        this.digPos = null;
        if (!EchoWork.safeToBreak(level, echo, pos)) {
            this.digRefused(level, echo, pos, this.digFor);
            return;
        }
        int tool = EchoWork.bestTool(echo.inventory(), state);
        if (tool < 0 && EchoWork.needsTool(state)) {
            this.halt(echo, JobStatus.of(JobStatus.Kind.NO_TOOL, EchoWork.toolKind(state)));
            return;
        }
        EchoHands.JobBreak result = EchoHands.breakForJob(level, echo, pos, tool);
        if (result.outcome() != EchoHands.Outcome.DONE) {
            this.digRefused(level, echo, pos, this.digFor);
            return;
        }
        if (result.toolBroke()) {
            Mnemolith.LOGGER.info("Mnemolith echo tool broke owner={} at {}", echo.ownerName(), pos.toShortString());
        }
        this.afterDig(level, echo, pos, result.toolBroke());
    }

    private void afterDig(ServerLevel level, EchoEntity echo, BlockPos pos, boolean toolBroke) {
        switch (this.digFor) {
            case TUNNEL -> this.phase = Phase.WALK;
            case CLEAR -> {
                this.digFor = DigFor.TARGET;
                this.placeBuildTarget(level, echo);
            }
            case TARGET -> {
                this.mined++;
                this.unreachableInRow = 0;
                this.target = null;
                this.setStatus(new JobStatus(JobStatus.Kind.MINING, this.targetState == null ? "" : key(this.targetState.getBlock()), this.mined, 0));
                this.phase = Phase.SELECT;
                this.mimicAfterBreak();
                this.onWorkAction(level, echo, pos);
            }
        }
        if (toolBroke && this.mode == Mode.MINE) {
            // Keep going only if another fitting tool is left for what it mines.
            BlockState sample = this.targetState;
            if (sample != null && EchoWork.needsTool(sample) && EchoWork.bestTool(echo.inventory(), sample) < 0) {
                this.halt(echo, JobStatus.of(JobStatus.Kind.TOOL_BROKE));
            }
        }
    }

    private void digRefused(ServerLevel level, EchoEntity echo, BlockPos pos, DigFor why) {
        switch (why) {
            case TARGET -> {
                this.refused.add(pos.asLong());
                this.target = null;
                this.phase = Phase.SELECT;
            }
            case TUNNEL -> this.replan(level, echo);
            case CLEAR, FIX -> {
                this.digFor = DigFor.TARGET;
                this.blocked.add(pos.asLong());
                this.buildTarget = null;
                this.phase = Phase.SELECT;
            }
        }
    }

    // ================= helpers =================

    private static int count(EchoInventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < EchoInventory.MAIN; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static String key(Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    /** Debug line for QA logs. */
    public String describe() {
        return "mode=" + this.mode.getSerializedName() + " phase=" + this.phase + " status=" + this.status.kind().getSerializedName()
                + " shown=" + this.shownStatus().kind().getSerializedName() + (this.alarmed ? " alarmed" : "") + " mined=" + this.mined
                + " built=" + this.builtCount + "/" + this.plan.size() + " candidates=" + this.candidates.size() + " refused=" + this.refused.size();
    }

    /** Direction the job looks for a build anchor preview; kept here so client and server share the rule. */
    public static Direction horizontalFacing(float yRot) {
        return Direction.fromYRot(yRot);
    }
}
