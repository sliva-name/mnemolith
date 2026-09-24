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
            Codec.INT.fieldOf("instability").forGetter(ChunkMemory::instability)
    ).apply(instance, ChunkMemory::fromCodec));

    private final List<Imprint> imprints = new ArrayList<>();
    private final List<BlockPos> muteStones = new ArrayList<>();
    private boolean fractured;
    private boolean archival;
    private long lastWriteGameTime;
    private int cachedPressure;
    private int instability;

    public ChunkMemory() {}

    private static ChunkMemory fromCodec(List<Imprint> imprints, List<BlockPos> muteStones, boolean fractured, boolean archival, long lastWriteGameTime, int cachedPressure, int instability) {
        ChunkMemory memory = new ChunkMemory();
        memory.imprints.addAll(imprints);
        memory.muteStones.addAll(muteStones);
        memory.fractured = fractured;
        memory.archival = archival;
        memory.lastWriteGameTime = lastWriteGameTime;
        memory.cachedPressure = cachedPressure;
        memory.instability = instability;
        return memory;
    }

    public boolean isEmpty() {
        return this.imprints.isEmpty()
                && this.muteStones.isEmpty()
                && !this.fractured
                && !this.archival
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
    }

    public boolean acceptsThrottledWrite(long gameTime, int debounceTicks) {
        return gameTime - this.lastWriteGameTime >= debounceTicks;
    }

    public void markWritten(long gameTime) {
        this.lastWriteGameTime = gameTime;
    }

    public void addImprint(Imprint imprint, int cap) {
        this.imprints.add(imprint);
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
        if (this.muteStones.size() >= ImprintConstants.ABSOLUTE_LIST_CAP) {
            return false;
        }
        if (this.muteStones.contains(pos)) {
            return false;
        }
        this.muteStones.add(pos.immutable());
        return true;
    }

    public boolean removeMuteStone(BlockPos pos) {
        return this.muteStones.remove(pos);
    }

    public List<ImprintTag> tags() {
        return this.imprints.stream().map(Imprint::tag).sorted(Comparator.comparing(Enum::ordinal)).toList();
    }
}
