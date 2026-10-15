package com.mnemolith.imprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Scar site on a chunk: the tempers that merged there, when it last seeded an old residue, and its mending (how
 * many times it was tended, when last, and whether it is now a memorial). The mending fields are optional so older
 * saves load unchanged.
 */
public record ScarSite(int tempers, long seededAt, int tended, long tendedAt, boolean memorial) {
    public static final Codec<ScarSite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("tempers", 0).forGetter(ScarSite::tempers),
            Codec.LONG.optionalFieldOf("seeded_at", 0L).forGetter(ScarSite::seededAt),
            Codec.INT.optionalFieldOf("tended", 0).forGetter(ScarSite::tended),
            Codec.LONG.optionalFieldOf("tended_at", Long.MIN_VALUE / 2).forGetter(ScarSite::tendedAt),
            Codec.BOOL.optionalFieldOf("memorial", false).forGetter(ScarSite::memorial))
            .apply(instance, ScarSite::new));

    public ScarSite(int tempers, long seededAt) {
        this(tempers, seededAt, 0, Long.MIN_VALUE / 2, false);
    }

    public ScarSite withSeededAt(long time) {
        return new ScarSite(this.tempers, time, this.tended, this.tendedAt, this.memorial);
    }

    /** One more tending at {@code time}; it also resets the seeding day (that day's old residue is given up). */
    public ScarSite tend(long time) {
        return new ScarSite(this.tempers, time, this.tended + 1, time, this.memorial);
    }

    public ScarSite asMemorial() {
        return new ScarSite(this.tempers, this.seededAt, this.tended, this.tendedAt, true);
    }
}
