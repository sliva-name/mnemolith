package com.mnemolith.imprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * Mutable per-chunk memory. The attachment serializer writes a copy; callers mutate this object
 * and then {@link net.minecraft.world.level.chunk.LevelChunk#markUnsaved()}.
 */
public final class ChunkMemory {
    public static final MapCodec<ChunkMemory> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.list(Imprint.CODEC, 0, ImprintConstants.ABSOLUTE_LIST_CAP).fieldOf("imprints").forGetter(ChunkMemory::imprintsCopy),
            Codec.list(BlockPos.CODEC, 0, ImprintConstants.ABSOLUTE_LIST_CAP).fieldOf("mute_stones").forGetter(ChunkMemory::muteStonesCopy),
            Codec.BOOL.fieldOf("fractured").forGetter(ChunkMemory::fractured),
            Codec.BOOL.fieldOf("archival").forGetter(ChunkMemory::archival),
            Codec.LONG.fieldOf("last_write").forGetter(ChunkMemory::lastWriteGameTime),
            Codec.INT.fieldOf("pressure").forGetter(ChunkMemory::cachedPressure),
            Codec.INT.fieldOf("instability").forGetter(ChunkMemory::instability),
            Codec.list(BlockPos.CODEC, 0, ImprintConstants.ABSOLUTE_LIST_CAP).optionalFieldOf("resonators", List.of()).forGetter(ChunkMemory::resonatorsCopy),
            Codec.list(BlockPos.CODEC, 0, ImprintConstants.ABSOLUTE_LIST_CAP).optionalFieldOf("strata", List.of()).forGetter(ChunkMemory::strataCopy),
            Codec.BOOL.optionalFieldOf("observatory", false).forGetter(ChunkMemory::observatory)
    ).apply(instance, ChunkMemory::fromCodec));

    private final List<Imprint> imprints = new ArrayList<>();
    private final List<BlockPos> muteStones = new ArrayList<>();
    private final List<BlockPos> resonators = new ArrayList<>();
    private final List<BlockPos> strata = new ArrayList<>();
    private boolean fractured;
    private boolean archival;
    private boolean observatory;
    private long lastWriteGameTime;
    private int cachedPressure;
    private int instability;
    private long lastCoolGameTime;
    /** Set when instability or a build, redstone, or path imprint can still cool. Not saved. */
    private boolean coolDirty;

    public ChunkMemory() {}

    private static ChunkMemory fromCodec(List<Imprint> imprints, List<BlockPos> muteStones, boolean fractured, boolean archival, long lastWriteGameTime, int cachedPressure, int instability, List<BlockPos> resonators, List<BlockPos> strata, boolean observatory) {
        ChunkMemory memory = new ChunkMemory();
        memory.imprints.addAll(imprints);
        memory.muteStones.addAll(muteStones);
        memory.fractured = fractured;
        memory.archival = archival;
        memory.lastWriteGameTime = lastWriteGameTime;
        memory.cachedPressure = cachedPressure;
        memory.instability = instability;
        memory.resonators.addAll(resonators);
        memory.strata.addAll(strata);
        memory.observatory = observatory;
        memory.refreshCooling();
        return memory;
    }

    public boolean isEmpty() {
        return this.imprints.isEmpty()
                && this.muteStones.isEmpty()
                && this.resonators.isEmpty()
                && this.strata.isEmpty()
                && !this.fractured
                && !this.archival
                && !this.observatory
                && this.instability == 0;
    }

    public List<Imprint> imprintsCopy() {
        return List.copyOf(this.imprints);
    }

    public List<BlockPos> muteStonesCopy() {
        return List.copyOf(this.muteStones);
    }

    public int imprintCount() {
        return this.imprints.size();
    }

    public Imprint imprintAt(int index) {
        return this.imprints.get(index);
    }

    public boolean hasMuteStone() {
        return !this.muteStones.isEmpty();
    }

    public boolean fractured() {
        return this.fractured;
    }

    public void setFractured(boolean fractured) {
        this.fractured = fractured;
    }

    public boolean archival() {
        return this.archival;
    }

    public void setArchival(boolean archival) {
        this.archival = archival;
    }

    public long lastWriteGameTime() {
        return this.lastWriteGameTime;
    }

    public int cachedPressure() {
        return this.cachedPressure;
    }

    public void setCachedPressure(int cachedPressure) {
        this.cachedPressure = cachedPressure;
    }

    public int instability() {
        return this.instability;
    }

    public void addInstability(int amount, int cap) {
        if (amount <= 0) {
            return;
        }
        this.instability = Math.min(cap, this.instability + amount);
        this.coolDirty = true;
    }

    public boolean wantsCooling() {
        return this.coolDirty;
    }

    /** Keeps the cool flag only while instability or a quiet imprint remains. */
    public void refreshCooling() {
        if (this.instability > 0) {
            this.coolDirty = true;
            return;
        }
        for (int index = 0; index < this.imprints.size(); index++) {
            if (isQuiet(this.imprints.get(index).tag())) {
                this.coolDirty = true;
                return;
            }
        }
        this.coolDirty = false;
    }

    public int coolInstability(int amount) {
        if (amount <= 0 || this.instability <= 0) {
            return 0;
        }
        int removed = Math.min(amount, this.instability);
        this.instability -= removed;
        return removed;
    }

    /**
     * Drops the oldest build, redstone, or path imprint that has aged past {@code fadeTicks}.
     * Deaths, explosions, falls, fire, silence, and player imprints stay until extracted.
     */
    public boolean fadeQuiet(long now, int fadeTicks) {
        if (fadeTicks <= 0 || this.imprints.isEmpty()) {
            return false;
        }
        int oldest = -1;
        long oldestTime = Long.MAX_VALUE;
        for (int index = 0; index < this.imprints.size(); index++) {
            Imprint imprint = this.imprints.get(index);
            if (!isQuiet(imprint.tag()) || now - imprint.writtenAt() < fadeTicks) {
                continue;
            }
            if (imprint.writtenAt() < oldestTime) {
                oldestTime = imprint.writtenAt();
                oldest = index;
            }
        }
        if (oldest < 0) {
            return false;
        }
        this.imprints.remove(oldest);
        return true;
    }

    /** One cool pulse per chunk per game tick, even if several players stand in it. Not saved. */
    public boolean markCoolPulse(long gameTime) {
        if (this.lastCoolGameTime == gameTime) {
            return false;
        }
        this.lastCoolGameTime = gameTime;
        return true;
    }

    private static boolean isQuiet(ImprintTag tag) {
        return tag == ImprintTag.BUILD || tag == ImprintTag.REDSTONE || tag == ImprintTag.PATH;
    }

    public boolean acceptsThrottledWrite(long gameTime, int debounceTicks) {
        return gameTime - this.lastWriteGameTime >= debounceTicks;
    }

    public void markWritten(long gameTime) {
        this.lastWriteGameTime = gameTime;
    }

    public void addImprint(Imprint imprint, int cap) {
        this.imprints.add(imprint);
        if (isQuiet(imprint.tag())) {
            this.coolDirty = true;
        }
        int limit = Math.max(1, Math.min(cap, ImprintConstants.ABSOLUTE_LIST_CAP));
        while (this.imprints.size() > limit) {
            int lowest = 0;
            for (int i = 1; i < this.imprints.size(); i++) {
                if (lowerPriority(this.imprints.get(i), this.imprints.get(lowest))) {
                    lowest = i;
                }
            }
            this.imprints.remove(lowest);
        }
    }

    private static boolean lowerPriority(Imprint candidate, Imprint current) {
        if (candidate.intensity() != current.intensity()) {
            return candidate.intensity() < current.intensity();
        }
        return candidate.writtenAt() < current.writtenAt();
    }

    public Optional<Imprint> removeHighest() {
        if (this.imprints.isEmpty()) {
            return Optional.empty();
        }
        int best = 0;
        for (int i = 1; i < this.imprints.size(); i++) {
            Imprint candidate = this.imprints.get(i);
            Imprint current = this.imprints.get(best);
            if (candidate.intensity() > current.intensity()
                    || (candidate.intensity() == current.intensity() && candidate.writtenAt() > current.writtenAt())) {
                best = i;
            }
        }
        return Optional.of(this.imprints.remove(best));
    }

    public boolean addMuteStone(BlockPos pos) {
        return addMark(this.muteStones, pos);
    }

    public int muteStoneCount() {
        return this.muteStones.size();
    }

    public int resonatorCount() {
        return this.resonators.size();
    }

    /** Mark lists stop at {@link ImprintConstants#ABSOLUTE_LIST_CAP}; a full list may leave real blocks untracked. */
    public static boolean markListFull(int size) {
        return size >= ImprintConstants.ABSOLUTE_LIST_CAP;
    }

    public boolean removeMuteStone(BlockPos pos) {
        return this.muteStones.remove(pos);
    }

    public List<BlockPos> resonatorsCopy() {
        return List.copyOf(this.resonators);
    }

    public boolean hasResonator() {
        return !this.resonators.isEmpty();
    }

    public boolean addResonator(BlockPos pos) {
        return addMark(this.resonators, pos);
    }

    public boolean removeResonator(BlockPos pos) {
        return this.resonators.remove(pos);
    }

    public List<BlockPos> strataCopy() {
        return List.copyOf(this.strata);
    }

    public int strataCount() {
        return this.strata.size();
    }

    public boolean noteStratum(BlockPos pos) {
        return addMark(this.strata, pos);
    }

    private static boolean addMark(List<BlockPos> marks, BlockPos pos) {
        if (markListFull(marks.size()) || marks.contains(pos)) {
            return false;
        }
        marks.add(pos.immutable());
        return true;
    }

    public boolean forgetStratum(BlockPos pos) {
        return this.strata.remove(pos);
    }

    public boolean observatory() {
        return this.observatory;
    }

    public void setObservatory(boolean observatory) {
        this.observatory = observatory;
    }

    public List<ImprintTag> tags() {
        return this.imprints.stream().map(Imprint::tag).sorted(Comparator.comparing(Enum::ordinal)).toList();
    }
}
