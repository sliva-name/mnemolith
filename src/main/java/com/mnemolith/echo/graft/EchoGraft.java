package com.mnemolith.echo.graft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.mnemolith.data.ImprintCast;

/**
 * A memory grafted into an echo: the imprint that was on the slip, and the charges left. Saved on the echo
 * ({@code echo_graft}) and on a possessed body; both fields are new and optional, so older saves simply have none.
 */
public record EchoGraft(ImprintCast cast, int charge) {
    public static final Codec<EchoGraft> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ImprintCast.CODEC.fieldOf("cast").forGetter(EchoGraft::cast),
            Codec.INT.fieldOf("charge").forGetter(EchoGraft::charge))
            .apply(instance, EchoGraft::new));

    /** Never null for a stored graft: only graftable tags are ever stored. Falls back to hushed for a hand-edited save. */
    public Temper temper() {
        Temper temper = Temper.of(this.cast.tag());
        return temper == null ? Temper.HUSHED : temper;
    }

    public EchoGraft withCharge(int value) {
        return new EchoGraft(this.cast, value);
    }
}
