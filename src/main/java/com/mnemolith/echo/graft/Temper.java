package com.mnemolith.echo.graft;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mnemolith.imprint.ImprintTag;

/**
 * What a grafted memory makes of an echo. One temper per graftable tag. Path, build, redstone and player slips only
 * remember who was there (an echo already is that), so they do not take as a graft.
 */
public enum Temper {
    /** Silence: its own and nearby echoes' work leaves no imprints; mobs and memory mobs do not notice it; slower. */
    HUSHED(ImprintTag.SILENCE, 12, 0xB8C6DC, null),
    /** Death: a decoy that pulls hostile mobs and stands its ground. */
    GRAVE(ImprintTag.DEATH, 24, 0xB6A2E8, null),
    /** Fire: fireproof, may mine beside lava, smelts the ore it (and echoes near it) mine. Work writes fire. */
    KINDLED(ImprintTag.FIRE, 32, 0xFF9A5C, ImprintTag.FIRE),
    /** Fall: takes long drops and digs out its own floor. */
    PLUNGING(ImprintTag.FALL, 24, 0x7FE0CF, null),
    /** Explosion: digs fast, work writes explosions, bursts when its body dies. */
    VOLATILE(ImprintTag.EXPLOSION, 48, 0xFF5E4E, ImprintTag.EXPLOSION);

    private final ImprintTag tag;
    private final int baseCharge;
    private final int rgb;
    private final @Nullable ImprintTag residue;

    Temper(ImprintTag tag, int baseCharge, int rgb, @Nullable ImprintTag residue) {
        this.tag = tag;
        this.baseCharge = baseCharge;
        this.rgb = rgb;
        this.residue = residue;
    }

    public ImprintTag tag() {
        return this.tag;
    }

    /** Charges one slip gives before {@code echoGraftChargeScale}. */
    public int baseCharge() {
        return this.baseCharge;
    }

    /** Body tint and particle color (RGB). */
    public int rgb() {
        return this.rgb;
    }

    /** Tag the echo's work imprints carry instead of build; null keeps build. */
    public @Nullable ImprintTag residue() {
        return this.residue;
    }

    public String key() {
        return "mnemolith.temper." + this.name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable Temper of(ImprintTag tag) {
        for (Temper temper : values()) {
            if (temper.tag == tag) {
                return temper;
            }
        }
        return null;
    }

    /** 0 is "no temper"; 1..5 are the tempers (used in the synced entity field). */
    public static @Nullable Temper byId(int id) {
        Temper[] values = values();
        return id <= 0 || id > values.length ? null : values[id - 1];
    }

    public int id() {
        return this.ordinal() + 1;
    }
}
