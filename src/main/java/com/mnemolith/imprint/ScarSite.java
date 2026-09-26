package com.mnemolith.imprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a recollection storm left in a chunk when its residues merged into the Scar: which tempers merged (a bit per
 * {@link com.mnemolith.echo.graft.Temper#id()}), and the game time the site last seeded its old residue. Saved on the
 * chunk memory as the optional {@code scar} field, so chunks saved before storms simply have none.
 */
public record ScarSite(int tempers, long seededAt) {
    public static final Codec<ScarSite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("tempers", 0).forGetter(ScarSite::tempers),
            Codec.LONG.optionalFieldOf("seeded_at", 0L).forGetter(ScarSite::seededAt))
            .apply(instance, ScarSite::new));

    public ScarSite withSeededAt(long time) {
        return new ScarSite(this.tempers, time);
    }
}
