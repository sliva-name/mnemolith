package com.mnemolith.content.composition;

import java.util.List;

import com.mnemolith.imprint.ImprintTag;

public enum CompositionFormula {
    UNRECORDED(List.of(ImprintTag.DEATH, ImprintTag.SILENCE)),
    FIRE_TRAIL(List.of(ImprintTag.FIRE, ImprintTag.BUILD)),
    LANDING_BURST(List.of(ImprintTag.FALL, ImprintTag.PLAYER));

    private final List<ImprintTag> tags;

    CompositionFormula(List<ImprintTag> tags) {
        this.tags = tags.stream().sorted().toList();
    }

    public List<ImprintTag> tags() {
        return this.tags;
    }

    public String translationKey() {
        return "mnemolith.formula." + this.name().toLowerCase();
    }
}
