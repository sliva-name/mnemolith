package com.mnemolith.echo.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.FarmLesson;
import com.mnemolith.entity.echo.EchoEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The job of one echo: what it was taught, where it works, and a small state machine that runs one step per tick.
 * Only the settings and progress are saved; searches and paths are rebuilt after a load, so a job resumes after a
 * relog or a restart.
 * <p>
 * {@link #tick} only notices, strain, orders, and alarms, then hands the step to {@link MineController},
 * {@link BuildController}, or {@link FarmController}. Those share {@link JobMotion} (path, walk, dig) and
 * {@link JobChest}. The saved codec is unchanged.
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
        BUILD,
        /** Stage 3: harvest mature taught crops, replant, deposit. */
        FARM;

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
    public record Stage3(List<Misfire> misfired, int workActions, FarmLesson farm, int harvested, Order order) {
        public static final Stage3 EMPTY = new Stage3(List.of(), 0, FarmLesson.NONE, 0, Order.NONE);
        public static final Codec<Stage3> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Misfire.CODEC.listOf().optionalFieldOf("misfired", List.of()).forGetter(Stage3::misfired),
                Codec.INT.optionalFieldOf("work_actions", 0).forGetter(Stage3::workActions),
                FarmLesson.CODEC.optionalFieldOf("farm", FarmLesson.NONE).forGetter(Stage3::farm),
                Codec.INT.optionalFieldOf("harvested", 0).forGetter(Stage3::harvested),
                Order.CODEC.optionalFieldOf("order", Order.NONE).forGetter(Stage3::order))
                .apply(instance, Stage3::new));
    }

    /** Stage 3 lens orders (stay, follow me, return to the work point). Ids are the ordinals: only append. */
    public enum Order implements StringRepresentable {
        NONE("none"),
        STAY("stay"),
        FOLLOW("follow"),
        RETURN("return");

        public static final Codec<Order> CODEC = StringRepresentable.fromEnum(Order::values);
        private final String name;

        Order(String name) {
            this.name = name;
        }

        public static Order byId(int id) {
            Order[] values = values();
            return id >= 0 && id < values.length ? values[id] : NONE;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    // ---- saved ----
    Mode mode = Mode.IDLE;
    EchoLesson lesson = EchoLesson.NONE;
    @Nullable BlockPos workAnchor;
    private int radius = -1;
    @Nullable BlockPos chest;
    @Nullable BlockPos buildAnchor;
    private Rotation rotation = Rotation.NONE;
    int mined;
    private JobStatus status = JobStatus.IDLE;

    // ---- collaborators (transient searches; saved progress stays on this job) ----
    final MineController mine = new MineController(this);
    final BuildController build = new BuildController(this);
    final FarmController farm = new FarmController(this);
    final JobMotion motion = new JobMotion(this);
    final JobChest chests = new JobChest(this);
    final JobOrders orders = new JobOrders(this);
    final JobAlarm alarm = new JobAlarm(this);
    final JobMimic mimic = new JobMimic(this);
    final JobStrain strain = new JobStrain(this);
    final EchoMover mover = new EchoMover();

    /** A short line shown instead of the status (a theft, a mimic, a misfire); the job keeps running under it. */
    @Nullable JobStatus notice;
    int noticeTicks;
    boolean dirty = true;
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
        return this.build.builtCount;
    }

    public int planSize() {
        return this.build.plan.size();
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
        this.strain.misfired.clear();
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
        this.strain.misfired.clear();
        this.buildAnchor = anchor.immutable();
        this.rotation = rotation;
        this.dirty = true;
    }

    public void clearBlueprintAnchor() {
        this.strain.misfired.clear();
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
        return this.hasWorkMode() && this.orders.order == Order.NONE;
    }

    /** MINE, BUILD or FARM, also while a lens order pauses it. */
    public boolean hasWorkMode() {
        return this.mode == Mode.MINE || this.mode == Mode.BUILD || this.mode == Mode.FARM;
    }

    public Order order() {
        return this.orders.order;
    }

    public FarmLesson farmLesson() {
        return this.farm.taught;
    }

    /** The farming lesson from a recording (stage 3). A new recording always replaces it, also with none. */
    public void setFarmLesson(FarmLesson farm) {
        this.farm.taught = farm;
        this.dirty = true;
    }

    public int harvested() {
        return this.farm.harvested;
    }

    /** Memory band of the chunk the echo works in (updated once a second while it works). */
    public com.mnemolith.pressure.PressureBand strain() {
        return this.strain.strain;
    }

    /** Misfires so far (skips, wrong blocks, extra breaks, wrong seeds), for QA and the log. */
    public int misfireCount() {
        return this.strain.misfires;
    }

    /** Sets the point a RETURN order walks to (the job start point); used by QA and when an echo is moved by command. */
    public void setWorkAnchor(@Nullable BlockPos anchor) {
        this.workAnchor = anchor == null ? null : anchor.immutable();
        this.dirty = true;
    }

    public int misfiredCount() {
        return this.strain.misfired.size();
    }

    /** Doors and gates this echo opened on its walks (job and lens orders), for QA. */
    public int doorsOpened() {
        return this.motion.walker.doorsOpened() + this.mover.walker().doorsOpened();
    }

    public boolean alarmed() {
        return this.alarm.alarmed;
    }

    public boolean mimicked() {
        return this.mimic.mimicTicks > 0;
    }

    public int mimicUndone() {
        return this.mimic.mimicCount;
    }

    /** Clears every stage 3 interruption (attack alarm, mimic, notices) when the job changes or stops. */
    private void clearInterruptions(EchoEntity echo) {
        if (this.alarm.alarmed || this.mover.active()) {
            if (echo.level() instanceof ServerLevel level) {
                this.mover.stop(level, echo);
            }
        }
        this.alarm.alarmed = false;
        this.alarm.threat = null;
        this.alarm.calmTicks = 0;
        this.mimic.mimicTicks = 0;
        this.mimic.mimicLeft = 0;
        this.mimic.mimicBy = null;
        this.mimic.undoPos = null;
        this.mimic.undoState = null;
        this.mimic.stumbleTicks = 0;
        this.orders.order = Order.NONE;
        this.orders.orderRetry = 0;
        if (this.notice != null) {
            this.notice = null;
            this.noticeTicks = 0;
            this.dirty = true;
        }
    }

    /** Replay of the recording started (stage 1 behaviour); the job waits until it ends. */
    public void beginReplay() {
        this.alarm.alarmed = false;
        this.orders.order = Order.NONE;
        this.mimic.mimicTicks = 0;
        this.mimic.undoPos = null;
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
        this.setStatus(new JobStatus(JobStatus.Kind.MINING, JobTexts.key(this.lesson.mining().get(0).block()), 0, 0));
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

    /** Starts farming around the echo (stage 3). False, with a stop status, when it was not taught farming. */
    public boolean startFarming(EchoEntity echo) {
        this.clearInterruptions(echo);
        this.release(echo);
        if (!this.farm.taught.teaches()) {
            this.mode = Mode.IDLE;
            this.setStatus(JobStatus.of(JobStatus.Kind.NO_LESSON));
            return false;
        }
        this.mode = Mode.FARM;
        this.workAnchor = echo.blockPosition();
        this.farm.harvested = 0;
        this.restartPhase();
        this.setStatus(new JobStatus(JobStatus.Kind.FARMING, this.farm.cropKey(), 0, 0));
        this.dirty = true;
        return true;
    }

    /** World positions and states the build places (rotated, bottom-up). Empty when no blueprint is placed. */
    public List<EchoLesson.Entry> plan() {
        if (this.buildAnchor == null || this.lesson.blueprint().isEmpty()) {
            return List.of();
        }
        return this.lesson.blueprint().get().placed(this.buildAnchor, this.rotation);
    }

    void restartPhase() {
        this.motion.restart();
        this.mine.resetSearch();
        this.build.resetSearch();
        this.farm.resetSearch();
    }

    /** Stops moving and clears a crack overlay. */
    public void release(EchoEntity echo) {
        echo.setMoveTarget(null);
        if (echo.level() instanceof ServerLevel level) {
            this.motion.walker.reset(level, echo);
        }
        if (this.motion.digPos != null && echo.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(echo.getId(), this.motion.digPos, -1);
        }
        this.motion.digPos = null;
        this.motion.clearRoute();
    }

    void halt(EchoEntity echo, JobStatus why) {
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
        for (Map.Entry<Long, BlockState> entry : this.strain.misfired.entrySet()) {
            list.add(new Misfire(BlockPos.of(entry.getKey()), entry.getValue()));
        }
        return new Stage3(list, this.strain.workActions, this.farm.taught, this.farm.harvested, this.orders.order);
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
        this.strain.misfired.clear();
        for (Misfire misfire : saved.stage3().misfired()) {
            this.strain.misfired.put(misfire.pos().asLong(), misfire.state());
        }
        this.strain.workActions = saved.stage3().workActions();
        this.farm.taught = saved.stage3().farm();
        this.farm.harvested = saved.stage3().harvested();
        this.orders.order = saved.stage3().order();
        this.restartPhase();
        this.dirty = true;
    }

    // ---- tick ----

    public void tick(ServerLevel level, EchoEntity echo) {
        if (this.noticeTicks > 0 && --this.noticeTicks == 0) {
            this.notice = null;
            this.dirty = true;
        }
        if (this.mimic.active()) {
            this.mimic.tick(level, echo);
        }
        if (echo.tickCount % 20 == 3) {
            this.strain.refresh(level, echo);
        }
        if (this.orders.active()) {
            this.orders.tick(level, echo);
            return;
        }
        if (this.alarm.alarmed) {
            this.alarm.tick(level, echo);
            return;
        }
        if (this.mimic.consumeStumble()) {
            echo.setMoveTarget(null);
            return;
        }
        switch (this.mode) {
            case MINE -> this.mine.tick(level, echo);
            case BUILD -> this.build.tick(level, echo);
            case FARM -> this.farm.tick(level, echo);
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

    public void onAttacked(ServerLevel level, EchoEntity echo, net.minecraft.world.entity.LivingEntity attacker) {
        this.alarm.onAttacked(level, echo, attacker);
    }

    /** The nearest mob within 12 blocks that has this echo as its target. */
    public static @Nullable Mob hunter(ServerLevel level, EchoEntity echo) {
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, echo.getBoundingBox().inflate(12.0D),
                candidate -> candidate.isAlive() && candidate.getTarget() == echo)) {
            double distance = mob.distanceToSqr(echo);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        return best;
    }

    /** The status a working job shows when it (re)starts its loop. */
    JobStatus workingStatus() {
        return switch (this.mode) {
            case MINE -> new JobStatus(JobStatus.Kind.MINING, this.lesson.mining().isEmpty() ? "" : JobTexts.key(this.lesson.mining().get(0).block()), this.mined, 0);
            case BUILD -> JobStatus.of(JobStatus.Kind.BUILDING, this.build.builtCount, this.lesson.blueprint().map(EchoLesson.Blueprint::size).orElse(0));
            case FARM -> new JobStatus(JobStatus.Kind.FARMING, this.farm.cropKey(), this.farm.harvested, 0);
            default -> JobStatus.IDLE;
        };
    }

    public boolean command(ServerLevel level, EchoEntity echo, Order order) {
        return this.orders.command(level, echo, order);
    }

    /** Where RETURN walks to: the build anchor for a build, else where the job was started. */
    public @Nullable BlockPos workPoint() {
        if (this.mode == Mode.BUILD && this.buildAnchor != null) {
            return this.buildAnchor;
        }
        return this.workAnchor != null ? this.workAnchor : this.buildAnchor;
    }

    public void beginMimic(java.util.UUID replicant, int ticks, int undoMax) {
        this.mimic.begin(replicant, ticks, undoMax);
    }

    /** Reads the band of the echo's chunk; a working echo stops in a fracture. */
    public void refreshStrain(ServerLevel level, EchoEntity echo) {
        this.strain.refresh(level, echo);
    }

    /** Debug line for QA logs. */
    public String describe() {
        return "mode=" + this.mode.getSerializedName() + " phase=" + this.motion.phase + " status=" + this.status.kind().getSerializedName()
                + " shown=" + this.shownStatus().kind().getSerializedName() + (this.alarm.alarmed ? " alarmed" : "") + " mined=" + this.mined
                + " harvested=" + this.farm.harvested + " built=" + this.build.builtCount + "/" + this.build.plan.size() + " candidates=" + this.mine.candidates.size()
                + " refused=" + this.mine.refused.size();
    }

    /** Direction the job looks for a build anchor preview; kept here so client and server share the rule. */
    public static Direction horizontalFacing(float yRot) {
        return Direction.fromYRot(yRot);
    }
}
