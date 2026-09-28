package com.mnemolith.content.composition;

import java.util.List;
import java.util.Optional;

import com.mnemolith.imprint.ImprintTag;

import net.minecraft.resources.Identifier;

/**
 * One drum formula: unordered imprint tags in, residual shard tag out — or an optional item result
 * (tuned lens, selective mute, needle upgrade, seal) instead of a shard.
 */
public record CompositionRecipe(Identifier id, List<ImprintTag> tags, ImprintTag product, Optional<Identifier> resultItem) {
    public CompositionRecipe {
        tags = tags.stream().sorted().toList();
        resultItem = resultItem == null ? Optional.empty() : resultItem;
    }

    public CompositionRecipe(Identifier id, List<ImprintTag> tags, ImprintTag product) {
        this(id, tags, product, Optional.empty());
    }

    public boolean hasItemResult() {
        return this.resultItem.isPresent();
    }

    public String translationKey() {
        return "mnemolith.formula." + id.getPath();
    }
}
