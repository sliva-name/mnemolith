package com.mnemolith.echo.storm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * One recollection storm: where it is (a 3x3-chunk area around {@link #center()}), which phase it is in, how far along,
 * and the storm-born residues it is carrying. Mutable; {@link StormData} saves it. Rules live in {@link Storms}.
 */
public final class RecollectionStorm {
    public enum Phase implements StringRepresentable {
        GATHERING("gathering"),
        RAGING("raging");

        public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);
        private final String name;

        Phase(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final Codec<RecollectionStorm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(RecollectionStorm::id),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(RecollectionStorm::dimension),
            Codec.INT.fieldOf("chunk_x").forGetter(storm -> storm.center.x()),
            Codec.INT.fieldOf("chunk_z").forGetter(storm -> storm.center.z()),
            Phase.CODEC.fieldOf("phase").forGetter(RecollectionStorm::phase),
            Codec.INT.fieldOf("ticks").forGetter(RecollectionStorm::ticks),
            Codec.INT.fieldOf("waves_left").forGetter(RecollectionStorm::wavesLeft),
            UUIDUtil.CODEC.listOf().optionalFieldOf("residues", List.of()).forGetter(RecollectionStorm::residues),
            Codec.STRING.optionalFieldOf("cause", "natural").forGetter(RecollectionStorm::cause))
            .apply(instance, RecollectionStorm::new));

    private final long id;
    private final ResourceKey<Level> dimension;
    private final ChunkPos center;
    private Phase phase;
    private int ticks;
    private int wavesLeft;
    private final List<UUID> residues = new ArrayList<>();
    private final String cause;

    public RecollectionStorm(long id, ResourceKey<Level> dimension, ChunkPos center, String cause) {
        this(id, dimension, center.x(), center.z(), Phase.GATHERING, 0, Storms.WAVES, List.of(), cause);
    }

    private RecollectionStorm(long id, ResourceKey<Level> dimension, int chunkX, int chunkZ, Phase phase, int ticks, int wavesLeft, List<UUID> residues,
            String cause) {
        this.id = id;
        this.dimension = dimension;
        this.center = new ChunkPos(chunkX, chunkZ);
        this.phase = phase;
        this.ticks = ticks;
        this.wavesLeft = wavesLeft;
        this.residues.addAll(residues);
        this.cause = cause;
    }

    public long id() {
        return this.id;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public ChunkPos center() {
        return this.center;
    }

    /** Middle of the centre chunk at {@code y}. */
    public BlockPos middle(int y) {
        return new BlockPos(this.center.getMiddleBlockX(), y, this.center.getMiddleBlockZ());
    }

    public Phase phase() {
        return this.phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
        this.ticks = 0;
    }

    public int ticks() {
        return this.ticks;
    }

    public void tick() {
        this.ticks++;
    }

    public int wavesLeft() {
        return this.wavesLeft;
    }

    public void spendWaves(int waves) {
        this.wavesLeft = Math.max(0, this.wavesLeft - waves);
    }

    /** Live list of the storm-born residues' ids (pruned by {@link Storms} each wave). */
    public List<UUID> residues() {
        return this.residues;
    }

    public String cause() {
        return this.cause;
    }

    /** True when {@code chunk} lies in this storm's 3x3 area. */
    public boolean covers(ResourceKey<Level> level, ChunkPos chunk) {
        return this.dimension.equals(level) && Math.abs(chunk.x() - this.center.x()) <= Storms.AREA && Math.abs(chunk.z() - this.center.z()) <= Storms.AREA;
    }
}
