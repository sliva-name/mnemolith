package com.mnemolith.recall;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Ring of gestures on one player, plus the game time of their last wow moment.
 * Mutated in place on the server. Not synced.
 */
public final class GestureLog {
    public static final MapCodec<GestureLog> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Gesture.CODEC.listOf().optionalFieldOf("gestures", List.of()).forGetter(GestureLog::gestures),
            Codec.LONG.optionalFieldOf("last_wow", 0L).forGetter(GestureLog::lastWow)
    ).apply(instance, GestureLog::new));

    private final List<Gesture> gestures = new ArrayList<>();
    private long lastWow;

    public GestureLog() {}

    public GestureLog(List<Gesture> gestures, long lastWow) {
        this.gestures.addAll(gestures);
        this.lastWow = lastWow;
    }

    public List<Gesture> gestures() {
        return List.copyOf(this.gestures);
    }

    public int size() {
        return this.gestures.size();
    }

    public boolean isEmpty() {
        return this.gestures.isEmpty() && this.lastWow == 0L;
    }

    public long lastWow() {
        return this.lastWow;
    }

    public void markWow(long gameTime) {
        this.lastWow = gameTime;
    }

    public @Nullable Gesture newest() {
        return this.gestures.isEmpty() ? null : this.gestures.get(this.gestures.size() - 1);
    }

    /** Drops gestures older than {@code maxAge} and keeps at most {@code cap}, oldest first to go. */
    public void add(Gesture gesture, long now, int cap, long maxAge) {
        this.purge(now, maxAge);
        this.gestures.add(gesture);
        this.purge(now, maxAge);
        int limit = Math.max(1, cap);
        while (this.gestures.size() > limit) {
            this.gestures.remove(0);
        }
    }

    public Optional<Gesture> qualifying(long now, long minAge, long maxAge) {
        Gesture best = null;
        for (Gesture gesture : this.gestures) {
            long age = now - gesture.gameTime();
            if (age < minAge || age > maxAge) {
                continue;
            }
            if (best == null || gesture.gameTime() < best.gameTime()) {
                best = gesture;
            }
        }
        return Optional.ofNullable(best);
    }

    private void purge(long now, long maxAge) {
        this.gestures.removeIf(gesture -> now - gesture.gameTime() > maxAge);
    }
}
