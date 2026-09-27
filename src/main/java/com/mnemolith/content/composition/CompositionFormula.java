package com.mnemolith.content.composition;

import java.util.List;

import com.mnemolith.imprint.ImprintTag;

public enum CompositionFormula {
    /** Death and silence, both already graftable, seal into a full volatile graft. */
    UNRECORDED(List.of(ImprintTag.DEATH, ImprintTag.SILENCE), ImprintTag.EXPLOSION),
    /** Build does not graft. It completes the fire slip into a full kindled graft. */
    FIRE_TRAIL(List.of(ImprintTag.FIRE, ImprintTag.BUILD), ImprintTag.FIRE),
    /** The player tag does not graft. It completes the fall slip into a full plunging graft. */
    LANDING_BURST(List.of(ImprintTag.FALL, ImprintTag.PLAYER), ImprintTag.FALL),
    /** The player tag does not graft. It completes the silence slip into a full hushed graft. */
    BAIT(List.of(ImprintTag.SILENCE, ImprintTag.PLAYER), ImprintTag.SILENCE);

    /** Strength 4 is a full graft: two slips of that temper. */
    public static final int SHARD_STRENGTH = 4;

    private final List<ImprintTag> tags;
    private final ImprintTag product;

    CompositionFormula(List<ImprintTag> tags, ImprintTag product) {
        this.tags = tags.stream().sorted().toList();
        this.product = product;
    }

    public List<ImprintTag> tags() {
        return this.tags;
    }

    /** Tag the sealed shard carries. */
    public ImprintTag product() {
        return this.product;
    }

    public String translationKey() {
        return "mnemolith.formula." + this.name().toLowerCase();
    }
}
