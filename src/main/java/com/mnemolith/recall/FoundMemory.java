package com.mnemolith.recall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Memory types one player has read for themselves. Empty until a lens read names something.
 * Bits use {@link GestureKind} and {@link AnchorKind} ordinals. Do not reorder those enums.
 */
public final class FoundMemory {
    public static final MapCodec<FoundMemory> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("gestures", 0).forGetter(FoundMemory::gestures),
            Codec.INT.optionalFieldOf("anchors", 0).forGetter(FoundMemory::anchors),
            Codec.BOOL.optionalFieldOf("distorted", false).forGetter(FoundMemory::distorted),
            Codec.BOOL.optionalFieldOf("imprint", false).forGetter(FoundMemory::imprint)
    ).apply(instance, FoundMemory::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoundMemory> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FoundMemory::gestures,
            ByteBufCodecs.VAR_INT, FoundMemory::anchors,
            ByteBufCodecs.BOOL, FoundMemory::distorted,
            ByteBufCodecs.BOOL, FoundMemory::imprint,
            FoundMemory::new);

    private int gestures;
    private int anchors;
    private boolean distorted;
    private boolean imprint;

    public FoundMemory() {}

    public FoundMemory(int gestures, int anchors, boolean distorted, boolean imprint) {
        this.gestures = gestures;
        this.anchors = anchors;
        this.distorted = distorted;
        this.imprint = imprint;
    }

    public int gestures() {
        return this.gestures;
    }

    public int anchors() {
        return this.anchors;
    }

    public boolean distorted() {
        return this.distorted;
    }

    public boolean imprint() {
        return this.imprint;
    }

    public boolean isEmpty() {
        return this.gestures == 0 && this.anchors == 0 && !this.distorted && !this.imprint;
    }

    public int lines() {
        return Integer.bitCount(this.gestures) + Integer.bitCount(this.anchors) + (this.distorted ? 1 : 0) + (this.imprint ? 1 : 0);
    }

    public boolean hasGesture(int ordinal) {
        return ordinal >= 0 && ordinal < GestureKind.values().length && (this.gestures & (1 << ordinal)) != 0;
    }

    public boolean hasAnchor(int ordinal) {
        return ordinal >= 0 && ordinal < AnchorKind.values().length && (this.anchors & (1 << ordinal)) != 0;
    }

    public boolean noteGesture(int ordinal) {
        if (ordinal < 0 || ordinal >= GestureKind.values().length) {
            return false;
        }
        int bit = 1 << ordinal;
        if ((this.gestures & bit) != 0) {
            return false;
        }
        this.gestures |= bit;
        return true;
    }

    public boolean noteAnchor(int ordinal) {
        if (ordinal < 0 || ordinal >= AnchorKind.values().length) {
            return false;
        }
        int bit = 1 << ordinal;
        if ((this.anchors & bit) != 0) {
            return false;
        }
        this.anchors |= bit;
        return true;
    }

    public boolean noteDistorted() {
        if (this.distorted) {
            return false;
        }
        this.distorted = true;
        return true;
    }

    public boolean noteImprint() {
        if (this.imprint) {
            return false;
        }
        this.imprint = true;
        return true;
    }
}
