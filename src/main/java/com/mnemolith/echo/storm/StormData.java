package com.mnemolith.echo.storm;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Active recollection storms, for every dimension ({@code data/mnemolith_storms.dat}). A storm whose centre chunk is
 * not loaded waits here until a player comes back or, after {@code stormUnwatchedTicks}, fades (see
 * {@link Storms#tick}). Faded centres stay listed until they settle. At most {@code maxStormsPerDimension} storms per
 * dimension.
 */
public final class StormData extends SavedData {
    public static final Codec<StormData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RecollectionStorm.CODEC.listOf().optionalFieldOf("storms", List.of()).forGetter(data -> data.storms),
            Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(data -> data.nextId),
            Faded.CODEC.listOf().optionalFieldOf("faded", List.of()).forGetter(data -> data.faded))
            .apply(instance, StormData::new));

    /** Where a storm faded unwatched: its area settles the next time the centre chunk loads. */
    public record Faded(ResourceKey<Level> dimension, int chunkX, int chunkZ, long stormId) {
        public static final Codec<Faded> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION).fieldOf("dimension").forGetter(Faded::dimension),
                Codec.INT.fieldOf("chunk_x").forGetter(Faded::chunkX),
                Codec.INT.fieldOf("chunk_z").forGetter(Faded::chunkZ),
                Codec.LONG.optionalFieldOf("storm", 0L).forGetter(Faded::stormId))
                .apply(instance, Faded::new));
    }
    public static final SavedDataType<StormData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "storms"), StormData::new, CODEC);

    private final List<RecollectionStorm> storms = new ArrayList<>();
    private long nextId = 1L;
    private final List<Faded> faded = new ArrayList<>();

    public StormData() {}

    private StormData(List<RecollectionStorm> saved, long nextId, List<Faded> faded) {
        this.storms.addAll(saved);
        this.nextId = Math.max(1L, nextId);
        this.faded.addAll(faded);
    }

    /** Faded storm centres still waiting to settle (oldest first). */
    public List<Faded> faded() {
        return List.copyOf(this.faded);
    }

    public void addFaded(Faded entry) {
        this.faded.add(entry);
        // A world that keeps losing storms far away should not grow this list without end.
        while (this.faded.size() > 64) {
            this.faded.remove(0);
        }
        this.setDirty();
    }

    public boolean removeFaded(Faded entry) {
        boolean removed = this.faded.remove(entry);
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    public static StormData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<RecollectionStorm> storms() {
        return List.copyOf(this.storms);
    }

    public int count(ResourceKey<Level> dimension) {
        int count = 0;
        for (RecollectionStorm storm : this.storms) {
            if (storm.dimension().equals(dimension)) {
                count++;
            }
        }
        return count;
    }

    public long takeId() {
        this.setDirty();
        return this.nextId++;
    }

    public void add(RecollectionStorm storm) {
        this.storms.add(storm);
        this.setDirty();
    }

    public boolean remove(RecollectionStorm storm) {
        boolean removed = this.storms.remove(storm);
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    public @Nullable RecollectionStorm byId(long id) {
        for (RecollectionStorm storm : this.storms) {
            if (storm.id() == id) {
                return storm;
            }
        }
        return null;
    }
}
