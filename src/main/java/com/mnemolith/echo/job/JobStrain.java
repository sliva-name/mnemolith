package com.mnemolith.echo.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/** Pressure the work writes, and the overloaded-chunk misfire roll. */
final class JobStrain {
    private final EchoJob job;
    PressureBand strain = PressureBand.CALM;
    long workChunk = Long.MIN_VALUE;
    int workActions;
    int misfires;
    /** Misfired blocks (saved): world position to the wrong state the echo put there. */
    final Map<Long, BlockState> misfired = new LinkedHashMap<>();

    JobStrain(EchoJob job) {
        this.job = job;
    }

    /** Reads the band of the echo's chunk; a working echo stops in a fracture. */
    void refresh(ServerLevel level, EchoEntity echo) {
        PressureBand band = PressureBand.CALM;
        if (this.job.isWorking()) {
            ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(echo.blockPosition()));
            band = memory == null ? band : MemoryPressure.band(memory.cachedPressure());
        }
        if (band != this.strain) {
            this.strain = band;
            this.job.dirty = true;
        }
        if (band == PressureBand.FRACTURE && this.job.isWorking() && CommonConfig.ECHO_FRACTURE_STOPS.get() && !echo.scarred()) {
            this.job.halt(echo, JobStatus.of(JobStatus.Kind.FRACTURED));
        }
    }

    /**
     * One finished piece of work (a mined, placed or harvested block). Every {@code echoWorkImprintEvery} pieces in the
     * same chunk the work leaves a build imprint by the owner there, plus {@code echoWorkInstability}; a muted chunk
     * refuses the write, and then no instability is added either.
     */
    void onWorkAction(ServerLevel level, EchoEntity echo, BlockPos pos) {
        int every = CommonConfig.ECHO_WORK_IMPRINT_EVERY.get();
        if (every <= 0) {
            return;
        }
        long chunk = ChunkPos.pack(pos);
        if (chunk != this.workChunk) {
            this.workChunk = chunk;
            this.workActions = 0;
        }
        if (++this.workActions < every) {
            return;
        }
        this.workActions = 0;
        // Memory grafts: a hush swallows the imprint (and its instability); kindled and volatile work leaves fire or explosion.
        ImprintTag tag = com.mnemolith.echo.graft.EchoGrafts.workImprint(level, echo, pos);
        if (tag == null) {
            return;
        }
        boolean written = ImprintWriter.write(level, pos, List.of(tag), echo.ownerId(), false);
        int instability = CommonConfig.ECHO_WORK_INSTABILITY.get();
        if (written && instability > 0) {
            ImprintWriter.spike(level, pos, instability);
        }
        Mnemolith.LOGGER.info("Mnemolith echo work imprint owner={} tag={} at {} written={}", echo.ownerName(), tag.getSerializedName(), pos.toShortString(), written);
    }

    /** True when this action misfires: only in an overloaded chunk, with {@code echoMisfireChance}. */
    boolean misfire(EchoEntity echo) {
        if (this.strain != PressureBand.OVERLOADED) {
            return false;
        }
        double chance = EchoJob.qaMisfireChance != null ? EchoJob.qaMisfireChance : CommonConfig.ECHO_MISFIRE_CHANCE.get();
        return chance > 0.0D && echo.getRandom().nextDouble() < chance;
    }

    void misfireNotice(EchoEntity echo, String kind, BlockPos pos) {
        this.job.notice(JobStatus.of(JobStatus.Kind.MISFIRE, kind), 60);
        Mnemolith.LOGGER.info("Mnemolith echo misfire owner={} kind={} at {}", echo.ownerName(), kind, pos.toShortString());
    }
}
