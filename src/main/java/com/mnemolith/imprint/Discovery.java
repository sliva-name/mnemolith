package com.mnemolith.imprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Tags and formulas one player has learned. Stored on the player, not on the chunk.
 * Bits use {@link ImprintTag} and composition-formula ordinals. Do not reorder those enums.
 */
public final class Discovery {
    public static final int FORMULA_COUNT = 4;

    public static final MapCodec<Discovery> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("tags", 0).forGetter(Discovery::tags),
            Codec.INT.optionalFieldOf("formulas", 0).forGetter(Discovery::formulas),
            Codec.BOOL.optionalFieldOf("mute_noted", false).forGetter(Discovery::muteNoted),
            Codec.BOOL.optionalFieldOf("observatory_noted", false).forGetter(Discovery::observatoryNoted)
    ).apply(instance, Discovery::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Discovery> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Discovery::tags,
            ByteBufCodecs.VAR_INT, Discovery::formulas,
            ByteBufCodecs.BOOL, Discovery::muteNoted,
            ByteBufCodecs.BOOL, Discovery::observatoryNoted,
            Discovery::new);

    private int tags;
    private int formulas;
    private boolean muteNoted;
    private boolean observatoryNoted;

    public Discovery() {}

    public Discovery(int tags, int formulas, boolean muteNoted, boolean observatoryNoted) {
        this.tags = tags;
        this.formulas = formulas;
        this.muteNoted = muteNoted;
        this.observatoryNoted = observatoryNoted;
    }

    public int tags() {
        return this.tags;
    }

    public int formulas() {
        return this.formulas;
    }

    public boolean muteNoted() {
        return this.muteNoted;
    }

    public boolean observatoryNoted() {
        return this.observatoryNoted;
    }

    public boolean isEmpty() {
        return this.tags == 0 && this.formulas == 0 && !this.muteNoted && !this.observatoryNoted;
    }

    public boolean hasTag(ImprintTag tag) {
        return (this.tags & (1 << tag.ordinal())) != 0;
    }

    public boolean hasFormula(int ordinal) {
        return ordinal >= 0 && ordinal < FORMULA_COUNT && (this.formulas & (1 << ordinal)) != 0;
    }

    public boolean noteTag(ImprintTag tag) {
        int bit = 1 << tag.ordinal();
        if ((this.tags & bit) != 0) {
            return false;
        }
        this.tags |= bit;
        return true;
    }

    public boolean noteFormula(int ordinal) {
        if (ordinal < 0 || ordinal >= FORMULA_COUNT) {
            return false;
        }
        int bit = 1 << ordinal;
        if ((this.formulas & bit) != 0) {
            return false;
        }
        this.formulas |= bit;
        return true;
    }

    public boolean noteMute() {
        if (this.muteNoted) {
            return false;
        }
        this.muteNoted = true;
        return true;
    }

    public boolean noteObservatory() {
        if (this.observatoryNoted) {
            return false;
        }
        this.observatoryNoted = true;
        return true;
    }

    public int knownFormulas() {
        return Integer.bitCount(this.formulas & ((1 << FORMULA_COUNT) - 1));
    }

    public int unreadFormulas() {
        return FORMULA_COUNT - this.knownFormulas();
    }
}
