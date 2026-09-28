package com.mnemolith.content.composition;

import java.util.List;

import com.mnemolith.imprint.ImprintTag;

import net.minecraft.resources.Identifier;

/** One drum formula: unordered imprint tags in, residual shard tag out. */
public record CompositionRecipe(Identifier id, List<ImprintTag> tags, ImprintTag product) {
    public CompositionRecipe {
        tags = tags.stream().sorted().toList();
    }

    public String translationKey() {
        return "mnemolith.formula." + id.getPath();
    }
}
