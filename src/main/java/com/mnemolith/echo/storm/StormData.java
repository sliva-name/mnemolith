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
 * not loaded waits here, frozen, until a player comes back. At most {@code maxStormsPerDimension} per dimension.
 */
public final class StormData extends SavedData {
    public static final Codec<StormData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RecollectionStorm.CODEC.listOf().optionalFieldOf("storms", List.of()).forGetter(data -> data.storms),
            Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(data -> data.nextId))
            .apply(instance, StormData::new));
    public static final SavedDataType<StormData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "storms"), StormData::new, CODEC);

    private final List<RecollectionStorm> storms = new ArrayList<>();
    private long nextId = 1L;

    public StormData() {}

    private StormData(List<RecollectionStorm> saved, long nextId) {
        this.storms.addAll(saved);
        this.nextId = Math.max(1L, nextId);
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
