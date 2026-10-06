package com.mnemolith.recall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The life events each player left in this world ({@link LifeMoment}), and when each last saw a scene.
 * Saved with the server. Bounded per player and per kind; the first moment of a kind is never trimmed.
 */
public final class LifeMoments extends SavedData {
    private static final Codec<Map<UUID, List<LifeMoment>>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, LifeMoment.CODEC.listOf());
    private static final Codec<Map<UUID, Long>> SCENE_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG);

    public static final Codec<LifeMoments> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MAP_CODEC.optionalFieldOf("owners", Map.of()).forGetter(saved -> saved.owners),
            SCENE_CODEC.optionalFieldOf("last_scene", Map.of()).forGetter(saved -> saved.lastScene))
            .apply(instance, LifeMoments::new));

    public static final SavedDataType<LifeMoments> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "life_moments"), LifeMoments::new, CODEC);

    /** What {@link #add} did. */
    public enum Added { NEW, MERGED }

    private final Map<UUID, List<LifeMoment>> owners = new HashMap<>();
    private final Map<UUID, Long> lastScene = new HashMap<>();

    public LifeMoments() {}

    private LifeMoments(Map<UUID, List<LifeMoment>> saved, Map<UUID, Long> scenes) {
        saved.forEach((owner, moments) -> this.owners.put(owner, new ArrayList<>(moments)));
        this.lastScene.putAll(scenes);
    }

    public static LifeMoments get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** A copy of one player's moments, oldest first. */
    public List<LifeMoment> of(UUID owner) {
        return List.copyOf(this.owners.getOrDefault(owner, List.of()));
    }

    public int size(UUID owner) {
        return this.owners.getOrDefault(owner, List.of()).size();
    }

    public int count(UUID owner, LifeMomentKind kind) {
        int count = 0;
        for (LifeMoment moment : this.owners.getOrDefault(owner, List.of())) {
            if (moment.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    /**
     * Adds {@code moment}, or folds it into the moment of the same kind already at that place.
     * Then trims: the kind to its own cap, the player to {@code cap}; oldest non-first moments go first.
     */
    public Added add(UUID owner, LifeMoment moment, int cap) {
        List<LifeMoment> list = this.owners.computeIfAbsent(owner, id -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            LifeMoment known = list.get(i);
            if (known.sameSpot(moment)) {
                list.set(i, known.repeated(moment));
                this.setDirty();
                return Added.MERGED;
            }
        }
        boolean firstOfKind = true;
        for (LifeMoment known : list) {
            if (known.kind() == moment.kind()) {
                firstOfKind = false;
                break;
            }
        }
        list.add(firstOfKind ? moment.markFirst() : moment);
        this.trimKind(list, moment.kind());
        this.trimAll(list, cap);
        this.setDirty();
        return Added.NEW;
    }

    /**
     * Replaces the kept moment that is {@code old} (same kind, dimension, place and first time; the seen time and counts
     * may have moved on since it was read) with {@code updated}.
     */
    public void replace(UUID owner, LifeMoment old, LifeMoment updated) {
        List<LifeMoment> list = this.owners.get(owner);
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            LifeMoment kept = list.get(i);
            if (kept.kind() == old.kind() && kept.dimension().equals(old.dimension()) && kept.pos().equals(old.pos()) && kept.gameTime() == old.gameTime()) {
                list.set(i, updated);
                this.setDirty();
                return;
            }
        }
    }

    /** Marks every moment of {@code owner} in {@code dimension} within {@code radius} of {@code pos} as seen at {@code now}. */
    public void markNear(UUID owner, String dimension, BlockPos pos, int radius, long now) {
        List<LifeMoment> list = this.owners.get(owner);
        if (list == null) {
            return;
        }
        double max = (double) radius * radius;
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            LifeMoment moment = list.get(i);
            if (moment.dimension().equals(dimension) && moment.pos().distSqr(pos) <= max && moment.lastNear() != now) {
                list.set(i, moment.withNear(now));
                changed = true;
            }
        }
        if (changed) {
            this.setDirty();
        }
    }

    public long lastScene(UUID owner) {
        return this.lastScene.getOrDefault(owner, Long.MIN_VALUE / 2);
    }

    public void markScene(UUID owner, long now) {
        this.lastScene.put(owner, now);
        this.setDirty();
    }

    public void forget(UUID owner) {
        boolean changed = this.owners.remove(owner) != null;
        changed |= this.lastScene.remove(owner) != null;
        if (changed) {
            this.setDirty();
        }
    }

    private void trimKind(List<LifeMoment> list, LifeMomentKind kind) {
        while (this.count(list, kind) > kind.cap()) {
            int drop = this.oldest(list, kind);
            if (drop < 0) {
                return;
            }
            list.remove(drop);
        }
    }

    private void trimAll(List<LifeMoment> list, int cap) {
        int limit = Math.max(1, cap);
        while (list.size() > limit) {
            int drop = this.oldest(list, null);
            if (drop < 0) {
                return;
            }
            list.remove(drop);
        }
    }

    private int count(List<LifeMoment> list, LifeMomentKind kind) {
        int count = 0;
        for (LifeMoment moment : list) {
            if (moment.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    /** Oldest non-first index of {@code kind} (any kind when null), or -1. */
    private int oldest(List<LifeMoment> list, LifeMomentKind kind) {
        int best = -1;
        long time = Long.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            LifeMoment moment = list.get(i);
            if (moment.first() || (kind != null && moment.kind() != kind)) {
                continue;
            }
            if (moment.gameTime() < time) {
                time = moment.gameTime();
                best = i;
            }
        }
        return best;
    }
}
