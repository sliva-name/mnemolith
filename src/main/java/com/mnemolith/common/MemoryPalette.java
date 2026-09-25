package com.mnemolith.common;

/**
 * Shared pigments. GUI colors add an opaque alpha at the call site.
 * Effect and particle colors use the same RGB with no alpha byte.
 */
public final class MemoryPalette {
    /** Bone. Fire-trail potion, extract and snatch motes, light glyphs. */
    public static final int BONE = 0xE6DCC8;
    /** Panel fill without alpha. */
    public static final int INK = 0x1C244A;
    /** Drop shadow under bone glyphs. */
    public static final int SHADOW = 0x070B18;
    /** Light verdigris glyph on the dark panel. */
    public static final int VERDIGRIS = 0x8ED9C8;
    /** Light ember glyph for a failed compose. */
    public static final int FAIL = 0xFFB089;
    /** Chip behind an unread formula. */
    public static final int CHIP = 0x101628;
    /** Near-black ink on the bone field-guide page. */
    public static final int GUIDE_INK = 0x1A1520;
    /** Light drop shadow under field-guide ink. */
    public static final int GUIDE_SHADOW = 0xF5F0E6;
    /** Inactive page dot on the bone page. */
    public static final int GUIDE_DOT = 0x6B6258;
    /** Verdigris pigment. Unrecorded potion, success mote, lens bar. */
    public static final int PIGMENT = 0x3E8E7E;
    /** Indigo. Landing-burst potion and mute haze. */
    public static final int INDIGO = 0x3D4A8A;
    /** Ember mote for a failed compose and a pressure warning. */
    public static final int EMBER = 0xE07A4A;
    /** Imprint shimmer mote. */
    public static final int SHIMMER = 0x6E7CC4;
    /** Echo-strider trail mote. */
    public static final int TRAIL = 0x7ED0C2;
    /** Replicant telegraph mote. */
    public static final int TELEGRAPH = 0xC45A6A;

    private MemoryPalette() {}

    /** Full opacity in front of an RGB pigment. */
    public static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }
}
