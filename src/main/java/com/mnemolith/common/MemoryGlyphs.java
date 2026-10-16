package com.mnemolith.common;

import com.mnemolith.content.item.RememberingCirclet;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.world.entity.player.Player;

/**
 * Memory feel, stage 4: the lens and circlet overlays' shared rules (common code, so QA can check them on a dedicated
 * server). Each imprint tag has one glyph colour from the memory palette; the lens pill shows the chunk's loudest tag as
 * a swatch in that colour beside its name; the remembering circlet tints the view edge with the kept colour while worn.
 */
public final class MemoryGlyphs {
    /** Kept colour for the circlet edge tint (the same indigo as the shared kept cue). */
    public static final int CIRCLET_TINT = MemoryPalette.INDIGO;

    private MemoryGlyphs() {}

    public static int colour(ImprintTag tag) {
        return switch (tag) {
            case FIRE, EXPLOSION, LIGHTNING -> MemoryPalette.EMBER;
            case DEATH, BOSS -> MemoryPalette.TELEGRAPH;
            case FALL -> MemoryPalette.BONE;
            case BUILD, TRADE -> MemoryPalette.PIGMENT;
            case SILENCE -> MemoryPalette.INDIGO;
            case PLAYER, PATH -> MemoryPalette.TRAIL;
            case REDSTONE -> 0xC0404A;
            case PORTAL -> MemoryPalette.SHIMMER;
            case SCULK -> MemoryPalette.VERDIGRIS;
            case FORGOTTEN -> MemoryPalette.FADED;
        };
    }

    /** Colour for a {@code ChunkPressure.loudest()} ordinal; -1 or out of range has no glyph (0). */
    public static int colour(int ordinal) {
        if (ordinal < 0 || ordinal >= ImprintTag.values().length) {
            return 0;
        }
        return colour(ImprintTag.values()[ordinal]);
    }

    /** Whether the circlet edge tint applies to {@code player} (wearing a remembering circlet, not a spectator). */
    public static boolean circletTint(Player player) {
        return player != null && !player.isSpectator() && RememberingCirclet.worn(player);
    }
}
